# Benchmark Considerations: RESTEasy vs Spring RestClient vs MicroProfile

## What This Measures

All three clients are generated from the same OpenAPI spec using `openapi-generator-maven-plugin 7.19.0`
with the `java` generator — `library=resteasy`, `library=restclient`, and `library=microprofile` respectively.

The single benchmarked endpoint is `POST /groups/{groupId}/artifacts` (`createArtifact`),
which exercises both serialization (request body) and deserialization (response body)
with nested objects (`CreateArtifact` -> `CreateVersion` -> `VersionContent`).

WireMock stubs the HTTP layer to isolate client overhead from network variance.

## Key Findings (Quick Smoke Run)

| Metric | RESTEasy | Spring RestClient | MicroProfile |
|---|---|---|---|
| Avg latency (us/op) | ~130 | ~348 | ~135 |
| Throughput (ops/us) | ~0.008 | ~0.003 | ~0.008 |
| Thread-safety (8 threads, ops/s) | ~20700 | ~9500 | ~24700 |

## Allocation Overhead

The restclient generator produces ~40% more allocation per call (~145 KB vs ~104 KB).
This is driven by Spring's `RestClient` -> `JdkClientHttpRequestFactory` -> `java.net.http.HttpClient`
stack, which allocates intermediate buffers, `HttpRequest` builders, and reactive flow objects
that RESTEasy's more direct JAX-RS client path avoids.

## Thread Safety

Both clients survived `@Threads(8)` with a shared instance without exceptions.
However, the RESTEasy `ApiClient` has known thread-safety concerns:
- Mutable `statusCode` and `responseHeaders` fields written after each call
- `SimpleDateFormat` usage in date parsing (not thread-safe)

These don't cause visible failures in a short benchmark but are a correctness risk
under sustained concurrent load. The Spring RestClient is stateless per-request by design.

## HTTP Protocol Note

Spring's `RestClient` (backed by `java.net.http.HttpClient`) defaults to HTTP/2 with fallback.
When targeting HTTP/1.1-only servers (like WireMock), an explicit `HttpClient.Version.HTTP_1_1`
configuration is required to avoid `EOFException` from failed HTTP/2 negotiation.

## MicroProfile REST Client

The MicroProfile client (`library=microprofile`) generates JAX-RS annotated interfaces
that are backed at runtime by RESTEasy's MicroProfile REST Client implementation
(`org.jboss.resteasy.microprofile:microprofile-rest-client`). This is the same stack
Quarkus uses under the hood — `RestClientBuilder` creates a CDI-style proxy over
RESTEasy's HTTP client.

This adds an additional proxy/reflection layer compared to the direct RESTEasy client,
which may show up as slightly higher latency and allocation in benchmarks.

The `microprofile` template in openapi-generator does not support `useJakartaEe=true`,
so a post-generation `maven-antrun-plugin` step replaces `javax.ws.rs` and
`javax.annotation` imports with their `jakarta.*` equivalents.

Jackson is used for serialization (`serializationLibrary=jackson`) instead of
Quarkus's default JSON-B, to keep serialization consistent across all three clients
and isolate the benchmark to the transport/proxy layer.

## Trade-offs

- **RESTEasy**: faster, lower allocation, but thread-safety concerns in generated `ApiClient`
- **Spring RestClient**: higher overhead, but stateless request path and ecosystem integration
- **MicroProfile**: wraps the same RESTEasy stack with an additional proxy layer; type-safe interface-driven approach; uses Jackson here instead of Quarkus's default JSON-B
