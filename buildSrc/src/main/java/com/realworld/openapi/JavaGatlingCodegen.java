package com.realworld.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.CodegenResponse;
import org.openapitools.codegen.InlineModelResolver;
import org.openapitools.codegen.languages.ScalaGatlingCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaGatlingCodegen extends ScalaGatlingCodegen {
    public JavaGatlingCodegen() {
        super();

        sourceFolder = "src" + File.separator + "gatling" + File.separator + "java";

        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", ".java");
        apiTemplateFiles.put("api-default.mustache", ".java");
        apiTemplateFiles.put("api-interface.mustache", ".java");
        apiTemplateFiles.put("simulation.mustache", "SampleSimulation.java");

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".java");
    }

    @Override
    public String getName() {
        return "java-gatling-custom";
    }

    @Override
    public String getHelp() {
        return "Generates Gatling Java simulations from the Scala Gatling preprocessing pipeline.";
    }

    /**
     * Splits each API tag into three generated types: the public {@code {Api}} interface
     * (produced by {@code api-interface.mustache}, exposes only the non-response-saving request
     * methods), the fully generated {@code Generated{Api}} class (produced by {@code api.mustache},
     * holds the complete generated implementation and implements the interface), and a thin
     * {@code Default{Api}} (produced by {@code api-default.mustache}) that extends it.
     * {@code Default{Api}} is the class simulations/tests should reference; the {@code Generated}
     * prefix on the base class signals it is fully regenerated on every build and should never be
     * hand-edited.
     * <p>
     * {@link #apiFilename} normally derives both the output filename and the {@code classname}
     * from {@link #toApiName}, shared across every template registered for a tag. Overriding it
     * here lets the two templates for the same tag produce differently-prefixed filenames
     * without affecting {@code toApiName}/{@code classname} elsewhere (e.g. model imports).
     */
    @Override
    public String apiFilename(String templateName, String tag) {
        if ("api.mustache".equals(templateName)) {
            return apiFileFolder() + File.separator + "Generated" + toApiFilename(tag) + apiTemplateFiles().get(templateName);
        }
        if ("api-default.mustache".equals(templateName)) {
            return apiFileFolder() + File.separator + "Default" + toApiFilename(tag) + apiTemplateFiles().get(templateName);
        }
        if ("api-interface.mustache".equals(templateName)) {
            return apiFileFolder() + File.separator + toApiFilename(tag) + apiTemplateFiles().get(templateName);
        }
        return super.apiFilename(templateName, tag);
    }

    /**
     * We resolve inline models ourselves (see {@link #preprocessOpenAPI}) so that generic,
     * single-property "envelope" wrapper schemas (e.g. {@code { user: {$ref: ...} }}) get a
     * semantic {@code title} assigned before flattening, instead of the generator's default
     * sequential {@code InlineObjectN} naming. Disabling the built-in resolver here just skips
     * the automatic run in {@code DefaultGenerator}; we still flatten inline models ourselves.
     */
    @Override
    public boolean getUseInlineModelResolver() {
        return false;
    }

    /**
     * Mirrors ScalaGatling preprocessing but skips CSV feeder file creation.
     * <p>
     * Also performs our own inline-model flattening pass (since the generator's automatic one is
     * disabled via {@link #getUseInlineModelResolver()}), after first assigning semantic titles to
     * generic single-property "envelope" wrapper schemas found in request bodies and responses.
     * This produces meaningful model names (e.g. {@code ArticleEnvelope}, {@code TagsEnvelope})
     * instead of {@code InlineObjectN}, purely from the schema's own structure — no dependency on
     * operation IDs, paths, or hardcoded contract-specific names, and without modifying the OpenAPI
     * contract file itself (titles are assigned in-memory to the parsed model only).
     */
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        assignEnvelopeSchemaTitles(openAPI);

        InlineModelResolver inlineModelResolver = new InlineModelResolver();
        inlineModelResolver.setInlineSchemaNameMapping(this.inlineSchemaNameMapping());
        inlineModelResolver.setInlineSchemaOptions(this.inlineSchemaOption());
        org.openapitools.codegen.InlineModelResolverAccess.flatten(inlineModelResolver, openAPI);

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String pathname = pathEntry.getKey();
            PathItem path = pathEntry.getValue();
            if (path.readOperations() == null) {
                continue;
            }
            for (Operation operation : path.readOperations()) {
                if (operation.getExtensions() == null) {
                    operation.setExtensions(new HashMap<>());
                }

                // Kept as the raw OpenAPI path (e.g. "/articles/{slug}/comments"), with the
                // {name} placeholders resolved from actual method parameters — not Gatling EL
                // session lookups — via x-gatling-path-format built in
                // postProcessOperationsWithModels.
                if (!operation.getExtensions().containsKey("x-gatling-path")) {
                    operation.addExtension("x-gatling-path", pathname);
                }

                Set<Parameter> headerParameters = new HashSet<>();
                Set<Parameter> formParameters = new HashSet<>();
                Set<Parameter> queryParameters = new HashSet<>();
                Set<Parameter> pathParameters = new HashSet<>();

                if (operation.getParameters() != null) {
                    for (Parameter parameter : operation.getParameters()) {
                        if ("header".equalsIgnoreCase(parameter.getIn())) {
                            headerParameters.add(parameter);
                        }
                        if ("query".equalsIgnoreCase(parameter.getIn())) {
                            queryParameters.add(parameter);
                        }
                        if ("path".equalsIgnoreCase(parameter.getIn())) {
                            pathParameters.add(parameter);
                        }
                    }
                }

                prepareGatlingExtensions(operation, headerParameters, "header");
                prepareGatlingExtensions(operation, formParameters, "form");
                prepareGatlingExtensions(operation, queryParameters, "query");
                prepareGatlingExtensions(operation, pathParameters, "path");
            }
        }
    }

    // ── generic envelope-schema naming ───────────────────────────────────────

    /**
     * Walks every request body and response schema in the spec (both reusable
     * {@code components/requestBodies}/{@code components/responses} entries and any defined
     * inline on an operation) and assigns a semantic {@link Schema#setTitle(String) title} to
     * anonymous single-property "envelope" wrapper schemas, e.g.:
     * <pre>
     *   required: [article]
     *   type: object
     *   properties:
     *     article:
     *       $ref: '#/components/schemas/Article'
     * </pre>
     * gets titled {@code ArticleEnvelope}. The {@link InlineModelResolver} (run right after this
     * method, in {@link #preprocessOpenAPI}) uses a schema's {@code title} as its generated class
     * name when present, so this gives these wrappers meaningful names instead of the default
     * sequential {@code InlineObjectN} fallback — purely from the wrapped property's own name/
     * type, with no hardcoded mapping and no dependency on operationId or path.
     */
    private void assignEnvelopeSchemaTitles(OpenAPI openAPI) {
        if (openAPI.getComponents() != null) {
            if (openAPI.getComponents().getRequestBodies() != null) {
                for (RequestBody requestBody : openAPI.getComponents().getRequestBodies().values()) {
                    titleEnvelopeContent(requestBody.getContent());
                }
            }
            if (openAPI.getComponents().getResponses() != null) {
                for (ApiResponse response : openAPI.getComponents().getResponses().values()) {
                    titleEnvelopeContent(response.getContent());
                }
            }
        }

        if (openAPI.getPaths() != null) {
            for (PathItem path : openAPI.getPaths().values()) {
                if (path.readOperations() == null) {
                    continue;
                }
                for (Operation operation : path.readOperations()) {
                    RequestBody requestBody = operation.getRequestBody();
                    if (requestBody != null && requestBody.get$ref() == null) {
                        titleEnvelopeContent(requestBody.getContent());
                    }
                    if (operation.getResponses() != null) {
                        for (ApiResponse response : operation.getResponses().values()) {
                            if (response.get$ref() == null) {
                                titleEnvelopeContent(response.getContent());
                            }
                        }
                    }
                }
            }
        }
    }

    private void titleEnvelopeContent(Content content) {
        if (content == null) {
            return;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType != null) {
                titleEnvelopeSchema(mediaType.getSchema());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void titleEnvelopeSchema(Schema schema) {
        if (schema == null || schema.get$ref() != null || schema.getTitle() != null) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }

        Map.Entry<String, Schema> mainEntry = findMainProperty(properties);
        if (mainEntry == null) {
            // Couldn't confidently identify a single "main" property (e.g. several equally
            // complex properties) — leave naming to the default resolver behaviour.
            return;
        }

        String propertyName = mainEntry.getKey();
        Schema<?> propertySchema = mainEntry.getValue();

        boolean isArray = propertySchema instanceof ArraySchema;
        Schema<?> itemSchema = isArray ? ((ArraySchema) propertySchema).getItems() : propertySchema;

        String base;
        if (itemSchema != null && itemSchema.get$ref() != null) {
            base = simpleRefName(itemSchema.get$ref());
            if (isArray) {
                base = pluralize(base);
            }
        } else {
            base = capitalize(propertyName);
        }

        schema.setTitle(base + "Envelope");
    }

    /**
     * Picks the single "main" (non-scalar) property out of a wrapper schema's properties, e.g. for
     * {@code { articles: [...], articlesCount: integer }} this returns the {@code articles}
     * entry. Scalar metadata properties (strings, numbers, booleans) alongside exactly one
     * array/object property are treated as auxiliary (e.g. pagination counts) and ignored. If
     * there's exactly one property total, it is always the main one. If there is more than one
     * non-scalar property, the schema doesn't clearly follow the single-payload "envelope"
     * convention, so {@code null} is returned and naming is left to the default resolver.
     */
    @SuppressWarnings("rawtypes")
    private Map.Entry<String, Schema> findMainProperty(Map<String, Schema> properties) {
        if (properties.size() == 1) {
            return properties.entrySet().iterator().next();
        }

        Map.Entry<String, Schema> mainEntry = null;
        for (Map.Entry<String, Schema> entry : properties.entrySet()) {
            Schema<?> propertySchema = entry.getValue();
            boolean isScalar = propertySchema.get$ref() == null
                && !(propertySchema instanceof ArraySchema)
                && (propertySchema.getProperties() == null || propertySchema.getProperties().isEmpty());
            if (!isScalar) {
                if (mainEntry != null) {
                    return null; // more than one candidate — ambiguous, bail out
                }
                mainEntry = entry;
            }
        }
        return mainEntry;
    }

    private static String simpleRefName(String ref) {
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    private static String pluralize(String s) {
        if (s.endsWith("s")) {
            return s;
        }
        return s + "s";
    }

    private void prepareGatlingExtensions(Operation operation, Set<Parameter> parameters, String parameterType) {
        if (parameters.isEmpty()) {
            return;
        }
        List<Object> vendorList = new ArrayList<>();
        for (Parameter parameter : parameters) {
            Map<String, Object> extensionMap = new HashMap<>();
            extensionMap.put("gatlingParamName", parameter.getName());
            extensionMap.put("gatlingParamValue", "${" + parameter.getName() + "}");
            vendorList.add(extensionMap);
        }
        String normalizedType = parameterType.toLowerCase(Locale.ROOT);
        operation.addExtension("x-gatling-" + normalizedType + "-params", vendorList);
        operation.addExtension("x-gatling-" + normalizedType + "-feeder",
            operation.getOperationId() + parameterType.toUpperCase(Locale.ROOT) + "Feeder");
    }

    /**
     * Override to produce Java-style generics ({@code List<T>}) instead of Scala-style
     * ({@code List[T]}) inherited from {@link ScalaGatlingCodegen}.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public String getTypeDeclaration(Schema p) {
        if (ModelUtils.isArraySchema(p)) {
            Schema<?> items = ((ArraySchema) p).getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(items) + ">";
        }
        if (ModelUtils.isMapSchema(p)) {
            Schema<?> inner = ModelUtils.getAdditionalProperties(p);
            if (inner == null) {
                inner = new StringSchema();
            }
            return getSchemaType(p) + "<String, " + getTypeDeclaration(inner) + ">";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);

        if (processed.getOperations() != null && processed.getOperations().getOperation() != null) {
            for (CodegenOperation operation : processed.getOperations().getOperation()) {
                operation.vendorExtensions.put(
                    "x-gatling-java-http-method",
                    operation.httpMethod.toLowerCase(Locale.ROOT)
                );

                buildGatlingPathFormat(operation);

                // Expected HTTP status code for the operation's success response, taken from the
                // contract itself (first non-default 2xx response), so the generated request
                // check stays in sync with the OpenAPI spec instead of assuming 200 everywhere.
                findExpectedStatusCode(operation)
                    .ifPresent(code -> operation.vendorExtensions.put("x-gatling-expected-status", code));

                operation.vendorExtensions.put("x-operation-id-capitalized", capitalize(operation.operationId));

                if (operation.bodyParam != null && operation.bodyParam.dataType != null) {
                    String methodName = "sample" + capitalize(operation.operationId) + "Body";
                    operation.vendorExtensions.put("x-body-sample-method", methodName);
                    operation.vendorExtensions.put("x-body-sample",
                        buildSampleExpression(operation.bodyParam.dataType, allModels, 0));
                }

                operation.vendorExtensions.put("x-last-response-body-key-constant",
                    screamingSnakeCase(operation.operationId) + "_LAST_RESPONSE_BODY_KEY");
                operation.vendorExtensions.put("x-last-success-response-body-key-constant",
                    screamingSnakeCase(operation.operationId) + "_LAST_SUCCESS_RESPONSE_BODY_KEY");
                if (operation.returnType != null) {
                    // Public constant name for the Gatling session key under which
                    // {operationId}AndParseResponse() stores the parsed response model, so
                    // callers can reference it (e.g. ArticlesApi.CREATE_ARTICLE_RESPONSE_KEY)
                    // instead of hardcoding the session key string.
                    operation.vendorExtensions.put("x-response-key-constant",
                        screamingSnakeCase(operation.operationId) + "_RESPONSE_KEY");
                }

                // Build the method parameter list shared by {op}Request()/{op}()/
                // {op}AndParseResponse(): path params (in URL order), then the body param, then
                // query params. Built once here (instead of ad-hoc comma-joining in the
                // template) so path parameters are never silently dropped from the signature.
                List<String> declParts = new ArrayList<>();
                List<String> callParts = new ArrayList<>();
                if (operation.pathParams != null) {
                    for (CodegenParameter pathParam : operation.pathParams) {
                        declParts.add(pathParam.dataType + " " + pathParam.paramName);
                        callParts.add(pathParam.paramName);
                    }
                }
                if (operation.bodyParam != null) {
                    declParts.add(operation.bodyParam.dataType + " body");
                    callParts.add("body");
                }
                if (operation.queryParams != null) {
                    for (CodegenParameter queryParam : operation.queryParams) {
                        declParts.add(queryParam.dataType + " " + queryParam.paramName);
                        callParts.add(queryParam.paramName);
                    }
                }
                operation.vendorExtensions.put("x-gatling-params-decl", String.join(", ", declParts));
                operation.vendorExtensions.put("x-gatling-params-call", String.join(", ", callParts));

                // Build combined sample-call argument list (path params + body + query params)
                String pathArgs = operation.pathParams == null ? "" :
                    operation.pathParams.stream()
                        .map(this::sampleValueForParam)
                        .collect(Collectors.joining(", "));
                String bodyArg = operation.bodyParam != null
                    ? (String) operation.vendorExtensions.get("x-body-sample-method") + "()"
                    : "";
                String queryArgs = operation.queryParams == null ? "" :
                    operation.queryParams.stream()
                        .map(this::sampleValueForParam)
                        .collect(Collectors.joining(", "));
                operation.vendorExtensions.put("x-all-params-sample",
                    Stream.of(pathArgs, bodyArg, queryArgs)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", ")));
            }
        }

        return processed;
    }

    /**
     * Builds the Java expression used as the argument to the Gatling HTTP method call
     * (e.g. {@code .post(...)}). Path parameters are passed explicitly as method arguments and
     * substituted via {@link String#format}, instead of relying on a Gatling EL session lookup
     * (e.g. {@code ${slug}}) that the caller would otherwise have to populate into the session
     * out-of-band. Assembled as a single ready-to-emit expression (rather than separate
     * format/args vendor extensions) because Mustache section contexts (e.g. iterating
     * {@code pathParams}) shadow the parent operation's {@code vendorExtensions}, making it
     * awkward to reference operation-level values from within a parameter loop in the template.
     */
    private void buildGatlingPathFormat(CodegenOperation operation) {
        String rawPath = String.valueOf(operation.vendorExtensions.getOrDefault("x-gatling-path", operation.path));

        if (operation.pathParams == null || operation.pathParams.isEmpty()) {
            operation.vendorExtensions.put("x-gatling-request-path-expr", "\"" + rawPath + "\"");
            return;
        }

        Map<String, String> javaNameByBaseName = operation.pathParams.stream()
            .collect(Collectors.toMap(p -> p.baseName, p -> p.paramName, (a, b) -> a));

        Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(rawPath);
        StringBuilder format = new StringBuilder();
        List<String> orderedArgs = new ArrayList<>();
        int last = 0;
        while (matcher.find()) {
            format.append(rawPath, last, matcher.start()).append("%s");
            String placeholder = matcher.group(1);
            orderedArgs.add(javaNameByBaseName.getOrDefault(placeholder, placeholder));
            last = matcher.end();
        }
        format.append(rawPath.substring(last));

        operation.vendorExtensions.put("x-gatling-request-path-expr",
            "String.format(\"" + format + "\", " + String.join(", ", orderedArgs) + ")");
    }

    /**
     * Finds the expected success HTTP status code declared for an operation in the OpenAPI
     * contract itself (a non-default {@code 2xx} response), so generated status checks stay in
     * sync with the contract instead of hardcoding an assumed code like {@code 200}. Falls back
     * to any non-default response with a numeric code if no {@code 2xx} entry is present, and to
     * empty (no check emitted) if the contract defines no explicit responses at all.
     */
    private Optional<String> findExpectedStatusCode(CodegenOperation operation) {
        if (operation.responses == null) {
            return Optional.empty();
        }
        Optional<String> twoXx = operation.responses.stream()
            .filter(r -> !r.isDefault && r.is2xx && r.code != null)
            .map(r -> r.code)
            .findFirst();
        if (twoXx.isPresent()) {
            return twoXx;
        }
        return operation.responses.stream()
            .filter(r -> !r.isDefault && r.code != null)
            .map(r -> r.code)
            .findFirst();
    }

    // ── sample-value generation ──────────────────────────────────────────────

    /**
     * Recursively builds a Java constructor-chain expression that creates a model
     * populated with representative fake values.
     * <p>
     * {@code depth} limits recursion to avoid infinite loops on circular references:
     * nested models beyond depth 1 are left as empty {@code new Type()} instances.
     */
    private String buildSampleExpression(String dataType, List<ModelMap> allModels, int depth) {
        CodegenModel model = findModel(dataType, allModels);
        if (model == null || model.vars == null || model.vars.isEmpty()) {
            return "new " + dataType + "()";
        }
        String indent = "            " + "    ".repeat(depth);
        StringBuilder sb = new StringBuilder("new ").append(dataType).append("()");
        for (CodegenProperty prop : model.vars) {
            sb.append("\n").append(indent)
              .append(".").append(prop.setter).append("(")
              .append(sampleValueFor(prop, allModels, depth))
              .append(")");
        }
        return sb.toString();
    }

    private String sampleValueFor(CodegenProperty prop, List<ModelMap> allModels, int depth) {
        if (prop.isBoolean)  return "true";
        if (prop.isLong)     return "1L";
        if (prop.isFloat)    return "1.0f";
        if (prop.isDouble || prop.isNumber) return "1.0";
        if (prop.isInteger)  return "1";
        if (prop.isString)   return "\"sample_" + prop.name + "\"";
        if (prop.isDateTime) return "java.time.OffsetDateTime.now()";
        if (prop.isDate)     return "java.time.LocalDate.now()";
        if (prop.isArray)    return "new java.util.ArrayList<>()";
        if (prop.isMap)      return "new java.util.HashMap<>()";
        if (prop.isModel)    return depth < 1
                                    ? buildSampleExpression(prop.dataType, allModels, depth + 1)
                                    : "new " + prop.dataType + "()";
        return "null";
    }

    private String sampleValueForParam(CodegenParameter param) {
        if (param.isBoolean)  return "true";
        if (param.isLong)     return "1L";
        if (param.isFloat)    return "1.0f";
        if (param.isDouble || param.isNumber) return "1.0";
        if (param.isInteger)  return "1";
        if (param.isString)   return "\"sample_" + param.paramName + "\"";
        if (param.isDateTime) return "java.time.OffsetDateTime.now()";
        if (param.isDate)     return "java.time.LocalDate.now()";
        if (param.isArray)    return "new java.util.ArrayList<>()";
        if (param.isMap)      return "new java.util.HashMap<>()";
        return "null";
    }

    private CodegenModel findModel(String dataType, List<ModelMap> allModels) {
        for (ModelMap modelMap : allModels) {
            CodegenModel m = modelMap.getModel();
            if (dataType.equals(m.classname)) return m;
        }
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Converts a camelCase identifier (e.g. {@code createArticle}) into SCREAMING_SNAKE_CASE
     * (e.g. {@code CREATE_ARTICLE}), suitable for use as a Java constant name.
     */
    private static String screamingSnakeCase(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
