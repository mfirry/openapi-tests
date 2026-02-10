# springdoc-openapi: Round-Trip Spec Extraction

## Approach

Unlike the other modules (`java`, `quarkus`, `spring`) which only generate code from a spec,
this module performs a **round-trip**: it generates Spring server controllers from the spec,
boots the application, and uses springdoc-openapi to re-extract the OpenAPI spec at runtime.

The lifecycle during `mvn verify`:

1. `generate-sources` — openapi-generator produces Spring controllers + models
2. `compile` — compiles everything
3. `pre-integration-test` — spring-boot-maven-plugin starts the app
4. `integration-test` — springdoc-openapi-maven-plugin fetches `/v3/api-docs` → `target/openapi-extracted.json`
5. `post-integration-test` — app stops

## Comparison with `spring` module

Both use `openapi-generator-maven-plugin` with `generatorName=spring`, identical config options,
and the same spec. The key difference: this module adds the springdoc runtime extraction pipeline
and provides a stub `ApiUtil.java` (since `generateSupportingFiles=false` omits it).

The `spring` module only generates code; this module validates that the generated server code
correctly re-exposes all API endpoints through springdoc's introspection.

## Observations

- The extracted spec is OpenAPI 3.1.0 (upgraded from the input 3.0.2 spec by springdoc).
- 43 paths and 53 schemas are extracted, matching the input spec's endpoints.
- `generateSupportingFiles=false` requires a manual `ApiUtil` stub since generated default
  methods reference it for example responses. The `interfaceOnly=true` option does not
  eliminate this dependency in the current generator version (7.19.0).
- Jackson version must be managed by Spring Boot's BOM rather than pinned explicitly,
  to avoid `jackson-annotations` / `jackson-databind` version mismatches.
- The springdoc-openapi-maven-plugin (v1.5) requires `spring.application.admin.enabled=true`
  for the spring-boot-maven-plugin's JMX-based start/stop to work.
