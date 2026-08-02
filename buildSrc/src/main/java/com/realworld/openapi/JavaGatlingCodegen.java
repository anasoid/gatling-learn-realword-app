package com.realworld.openapi;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.languages.ScalaGatlingCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaGatlingCodegen extends ScalaGatlingCodegen {
    public JavaGatlingCodegen() {
        super();

        sourceFolder = "src" + File.separator + "gatling" + File.separator + "java";

        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", ".java");
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

                if (operation.bodyParam != null && operation.bodyParam.dataType != null) {
                    String methodName = "sample" + capitalize(operation.operationId) + "Body";
                    operation.vendorExtensions.put("x-body-sample-method", methodName);
                    operation.vendorExtensions.put("x-body-sample",
                        buildSampleExpression(operation.bodyParam.dataType, allModels, 0));
                }

                // Build combined sample-call argument list (body + query params)
                String bodyArg = operation.bodyParam != null
                    ? (String) operation.vendorExtensions.get("x-body-sample-method") + "()"
                    : "";
                String queryArgs = operation.queryParams == null ? "" :
                    operation.queryParams.stream()
                        .map(this::sampleValueForParam)
                        .collect(Collectors.joining(", "));
                operation.vendorExtensions.put("x-all-params-sample",
                    Stream.of(bodyArg, queryArgs)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining(", ")));
            }
        }

        return processed;
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
}
