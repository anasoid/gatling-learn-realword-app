package com.realworld.openapi;

import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.languages.ScalaGatlingCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class JavaGatlingCodegen extends ScalaGatlingCodegen {
    public JavaGatlingCodegen() {
        super();

        sourceFolder = "src" + File.separator + "gatling" + File.separator + "java";

        apiTemplateFiles.clear();
        apiTemplateFiles.put("api.mustache", "Simulation.java");

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

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap processed = super.postProcessOperationsWithModels(objs, allModels);

        if (processed.getOperations() != null && processed.getOperations().getOperation() != null) {
            for (CodegenOperation operation : processed.getOperations().getOperation()) {
                operation.vendorExtensions.put(
                    "x-gatling-java-http-method",
                    operation.httpMethod.toLowerCase(Locale.ROOT)
                );
            }
        }

        return processed;
    }
}
