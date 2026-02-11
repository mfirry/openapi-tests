# Maven Plugin Comparison: OpenAPI Generator vs Kiota

## Overview

Both plugins generate Java HTTP clients from the same `openapi.yaml` spec (single `GET /users` endpoint).

- **OpenAPI Generator** (`org.openapitools:openapi-generator-maven-plugin:7.19.0`) with `library=native`
- **Kiota** (`io.kiota:kiota-maven-plugin:0.0.32`) with `kiota-http-jdk` + `kiota-serialization-jackson`

Both target Java 21, use JDK's built-in `java.net.http.HttpClient`, and Jackson for JSON.

## Code Generation

| Metric | OpenAPI Generator | Kiota |
|---|---|---|
| Generated files | 13 | 2 |
| Generated lines | 1722 | 140 |
| Build tool integration | Plugin runs in-process | Plugin downloads Kiota CLI binary, shells out |
| Additional build plugin needed | No | Yes (`build-helper-maven-plugin` to add source dir) |
| Generation speed | ~1s | ~5s (includes CLI binary download on first run) |

OpenAPI Generator produces a full client SDK: `ApiClient`, `ApiException`, `ApiResponse`, `Configuration`,
`JSON`, `Pair`, `ServerConfiguration`, `ServerVariable`, `DefaultApi`, `AbstractOpenApiSchema`, plus
RFC3339 date-handling utilities. For a one-endpoint spec, this is heavy scaffolding.

Kiota generates only what is needed: a `SampleApiClient` entry point and a `UsersRequestBuilder`.
The framework abstractions (`RequestAdapter`, `RequestInformation`, etc.) live in the runtime
libraries, not in generated code.

## API Style

**OpenAPI Generator** produces traditional method-based APIs:

```java
var apiClient = new ApiClient();
var api = new DefaultApi(apiClient);
List<String> users = api.usersGet();
```

**Kiota** produces a fluent request-builder API mirroring the URL structure:

```java
var client = new SampleApiClient(new JDKRequestAdapter());
List<String> users = client.users().get();
```

The Kiota style scales better for large APIs with deep path hierarchies (`client.groups("id").artifacts().get()`),
while OpenAPI Generator flattens everything into a single API class per tag.

## Dependencies

| | OpenAPI Generator (native) | Kiota (jdk + jackson) |
|---|---|---|
| Direct deps | 8 | 7 |
| Transitive deps | 12 (total) | 15 (total) |
| Jackson | 2.18.2 (4 jars) | 2.20.1 (3 jars, via kiota-serialization-jackson) |
| HTTP library | JDK HttpClient (no extra jar) | JDK HttpClient (no extra jar) |
| Notable extras | Apache HttpMime, jsr305, jackson-databind-nullable | microsoft-kiota-abstractions, OpenTelemetry API, std-uritemplate |

OpenAPI Generator pulls in Apache HttpComponents (for multipart support) even when using the `native`
library. Kiota pulls in OpenTelemetry API (for built-in tracing support) and std-uritemplate
(for RFC 6570 URL templates) transitively through `microsoft-kiota-abstractions`.

## Plugin Mechanics

**OpenAPI Generator** runs entirely as a Maven plugin in-process. The code generator is a Java
library bundled in the plugin jar. No external tooling is needed.

**Kiota** is a thin Maven wrapper around the Kiota CLI (a .NET AOT-compiled native binary).
The plugin downloads the platform-specific binary to `target/kiota/` on first run, then shells
out to it. This means:
- First build is slower (binary download)
- Builds are reproducible (pinned `kiotaVersion`)
- The CLI binary is ~80 MB on disk
- Offline builds require `useSystemKiota=true` with a pre-installed binary

## Configuration Surface

OpenAPI Generator has a much larger configuration surface: 50+ `configOptions` for the `java`
generator alone, 17 library variants (native, restclient, okhttp-gson, resteasy, etc.),
multiple serialization libraries, and extensive template customization via Mustache overrides.

Kiota's configuration is minimal: input spec, namespace, client class name, and
serializer/deserializer factory lists. Customization is done at the runtime library level
(swap HTTP or serialization implementations) rather than at generation time.

## Ecosystem and Maturity

| | OpenAPI Generator | Kiota |
|---|---|---|
| First release | 2018 (fork of Swagger Codegen) | 2022 (Microsoft) |
| Java plugin version | 7.19.0 | 0.0.32 |
| GitHub stars | ~23k | ~200 (kiota-java-extra) |
| Languages supported | 50+ generators | Java, C#, Go, Python, TypeScript, etc. |
| Backing | Community (OpenAPITools org) | Microsoft + community (kiota-java-extra) |

OpenAPI Generator is the established standard with a massive community. Kiota is newer, still
pre-1.0 for the Java extras, but backed by Microsoft and designed from the ground up for
the modern OpenAPI ecosystem (including OpenAPI 3.1 and API manifests).

## Trade-offs

- **OpenAPI Generator**: battle-tested, maximum flexibility via library/config options, generates
  self-contained clients with all utilities baked in. Drawback: verbose generated code, large
  config surface, harder to keep generated code in sync with spec changes.
- **Kiota**: minimal generated code, clean fluent API, built-in OpenTelemetry support,
  runtime-based architecture makes upgrades easier (update dependency, not regenerate).
  Drawback: pre-1.0, smaller community, external CLI binary dependency, requires more
  runtime library configuration (serializers/deserializers).
