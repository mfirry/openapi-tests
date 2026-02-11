package eu.firry.benchmarks;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import eu.firry.benchmarks.resteasy.ApiClient;
import eu.firry.benchmarks.resteasy.ApiException;
import eu.firry.benchmarks.resteasy.api.ArtifactsApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.openjdk.jmh.annotations.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Threads(8)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ThreadSafetyBenchmark {

    private WireMockServer wireMock;
    private ArtifactsApi resteasyApi;
    private eu.firry.benchmarks.restclient.api.ArtifactsApi restclientApi;
    private eu.firry.benchmarks.microprofile.api.ArtifactsApi microprofileApi;

    private eu.firry.benchmarks.resteasy.model.CreateArtifact resteasyRequest;
    private eu.firry.benchmarks.restclient.model.CreateArtifact restclientRequest;
    private eu.firry.benchmarks.microprofile.model.CreateArtifact microprofileRequest;

    private static final String CREATE_ARTIFACT_RESPONSE = """
            {
              "artifact": {
                "groupId": "my-group",
                "artifactId": "my-artifact",
                "name": "My Artifact",
                "description": "A test artifact",
                "artifactType": "AVRO",
                "owner": "user1",
                "createdOn": "2024-01-01T00:00:00Z",
                "modifiedBy": "user1",
                "modifiedOn": "2024-01-01T00:00:00Z",
                "labels": {}
              },
              "version": {
                "version": "1.0.0",
                "globalId": 1,
                "contentId": 1,
                "groupId": "my-group",
                "artifactId": "my-artifact",
                "artifactType": "AVRO",
                "owner": "user1",
                "createdOn": "2024-01-01T00:00:00Z",
                "name": "Version 1",
                "description": "First version",
                "labels": {}
              }
            }
            """;

    @Setup(Level.Trial)
    public void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(post(urlPathMatching("/groups/.*/artifacts"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(CREATE_ARTIFACT_RESPONSE)));

        String baseUrl = "http://localhost:" + wireMock.port();

        var resteasyClient = new ApiClient();
        resteasyClient.setBasePath(baseUrl);
        resteasyApi = new ArtifactsApi(resteasyClient);

        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        var springRestClient = eu.firry.benchmarks.restclient.ApiClient.buildRestClientBuilder()
                .requestFactory(requestFactory)
                .build();
        var restclientClient = new eu.firry.benchmarks.restclient.ApiClient(springRestClient);
        restclientClient.setBasePath(baseUrl);
        restclientApi = new eu.firry.benchmarks.restclient.api.ArtifactsApi(restclientClient);

        var resteasyContent = new eu.firry.benchmarks.resteasy.model.VersionContent();
        resteasyContent.setContent("{\"type\":\"record\",\"name\":\"TestType\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}");
        resteasyContent.setContentType("application/json");

        var resteasyVersion = new eu.firry.benchmarks.resteasy.model.CreateVersion();
        resteasyVersion.setVersion("1.0.0");
        resteasyVersion.setContent(resteasyContent);

        resteasyRequest = new eu.firry.benchmarks.resteasy.model.CreateArtifact();
        resteasyRequest.setArtifactId("bench-artifact");
        resteasyRequest.setArtifactType("AVRO");
        resteasyRequest.setFirstVersion(resteasyVersion);

        var restclientContent = new eu.firry.benchmarks.restclient.model.VersionContent();
        restclientContent.setContent("{\"type\":\"record\",\"name\":\"TestType\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}");
        restclientContent.setContentType("application/json");

        var restclientVersion = new eu.firry.benchmarks.restclient.model.CreateVersion();
        restclientVersion.setVersion("1.0.0");
        restclientVersion.setContent(restclientContent);

        restclientRequest = new eu.firry.benchmarks.restclient.model.CreateArtifact();
        restclientRequest.setArtifactId("bench-artifact");
        restclientRequest.setArtifactType("AVRO");
        restclientRequest.setFirstVersion(restclientVersion);

        microprofileApi = RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl))
                .register(new ContextResolver<ObjectMapper>() {
                    @Override
                    public ObjectMapper getContext(Class<?> type) {
                        var mapper = new ObjectMapper();
                        mapper.registerModule(new JavaTimeModule());
                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                        return mapper;
                    }
                })
                .build(eu.firry.benchmarks.microprofile.api.ArtifactsApi.class);

        var microprofileContent = new eu.firry.benchmarks.microprofile.model.VersionContent();
        microprofileContent.setContent("{\"type\":\"record\",\"name\":\"TestType\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}");
        microprofileContent.setContentType("application/json");

        var microprofileVersion = new eu.firry.benchmarks.microprofile.model.CreateVersion();
        microprofileVersion.setVersion("1.0.0");
        microprofileVersion.setContent(microprofileContent);

        microprofileRequest = new eu.firry.benchmarks.microprofile.model.CreateArtifact();
        microprofileRequest.setArtifactId("bench-artifact");
        microprofileRequest.setArtifactType("AVRO");
        microprofileRequest.setFirstVersion(microprofileVersion);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        wireMock.stop();
    }

    @Benchmark
    public Object resteasy_sharedClient() throws ApiException {
        return resteasyApi.createArtifact("default", resteasyRequest, null, null, null);
    }

    @Benchmark
    public Object restclient_sharedClient() {
        return restclientApi.createArtifact("default", restclientRequest, null, null, null);
    }

    @Benchmark
    public Object microprofile_sharedClient() throws eu.firry.benchmarks.microprofile.api.ApiException {
        return microprofileApi.createArtifact("default", microprofileRequest, null, null, null);
    }
}
