package org.openapitools.codegen;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * {@link InlineModelResolver#flatten(OpenAPI)} is package-private, so this tiny shim (living in
 * the same package on purpose) exposes it to our custom generator. We need to invoke it
 * ourselves — instead of relying on the generator's automatic run — so that semantic titles can
 * be assigned to anonymous "envelope" wrapper schemas beforehand (see
 * {@code JavaGatlingCodegen#preprocessOpenAPI}), giving them meaningful generated class names
 * instead of the default sequential {@code InlineObjectN} fallback.
 */
public final class InlineModelResolverAccess {
    private InlineModelResolverAccess() {
    }

    public static void flatten(InlineModelResolver resolver, OpenAPI openAPI) {
        resolver.flatten(openAPI);
    }
}
