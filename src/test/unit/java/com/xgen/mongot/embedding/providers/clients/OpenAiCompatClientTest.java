package com.xgen.mongot.embedding.providers.clients;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.xgen.mongot.config.util.DeploymentEnvironment;
import com.xgen.mongot.embedding.EmbeddingRequestContext;
import com.xgen.mongot.embedding.VectorOrError;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderNonTransientException;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderTransientException;
import com.xgen.mongot.embedding.providers.configs.EmbeddingModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.EmbeddingProvider;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.ServiceTier;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class OpenAiCompatClientTest {

  private static final EmbeddingServiceConfig.ErrorHandlingConfig RETRY_CONFIG =
      new EmbeddingServiceConfig.ErrorHandlingConfig(3, 10L, 10L, 0.1);

  private static EmbeddingRequestContext floatContext() {
    return new EmbeddingRequestContext(
        "testdb", "testIndex", "testCollection", 3, VectorAutoEmbedQuantization.FLOAT);
  }

  private static EmbeddingModelConfig openAiModel(Optional<String> apiKey) {
    return openAiModel(apiKey, Optional.empty());
  }

  private static EmbeddingModelConfig openAiModel(
      Optional<String> apiKey, Optional<String> authHeaderName) {
    return openAiModel(
        apiKey,
        authHeaderName,
        new EmbeddingServiceConfig.OpenAiModelConfig(
            Optional.empty(), Optional.of(96), Optional.of(120_000), Optional.empty()));
  }

  private static EmbeddingModelConfig openAiModel(
      Optional<String> apiKey,
      Optional<String> authHeaderName,
      EmbeddingServiceConfig.OpenAiModelConfig modelConfig) {
    EmbeddingServiceConfig.OpenAiEmbeddingCredentials creds =
        new EmbeddingServiceConfig.OpenAiEmbeddingCredentials(apiKey, authHeaderName);
    EmbeddingServiceConfig.EmbeddingConfig config =
        new EmbeddingServiceConfig.EmbeddingConfig(
            Optional.empty(),
            modelConfig,
            RETRY_CONFIG,
            creds,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            Optional.of("http://localhost:11434/v1/embeddings"),
            false,
            Optional.empty());
    return EmbeddingModelConfig.create("bge-m3", EmbeddingProvider.OPENAI_COMPATIBLE, config);
  }

  private static OpenAiCompatClient newClient(EmbeddingModelConfig model) {
    return newClient(model, ServiceTier.QUERY);
  }

  private static OpenAiCompatClient newClient(EmbeddingModelConfig model, ServiceTier tier) {
    EmbeddingClientFactory factory =
        new EmbeddingClientFactory(new SimpleMeterRegistry(), DeploymentEnvironment.COMMUNITY);
    EmbeddingModelConfig.ConsolidatedWorkloadParams params =
        switch (tier) {
          case QUERY -> model.query();
          case CHANGE_STREAM -> model.changeStream();
          case COLLECTION_SCAN -> model.collectionScan();
        };
    ClientInterface client = factory.createEmbeddingClient(model, tier, params);
    assertTrue(
        "Expected OPENAI_COMPATIBLE provider to build an OpenAiCompatClient",
        client instanceof OpenAiCompatClient);
    return (OpenAiCompatClient) client;
  }

  private static String base64Floats(float... values) {
    ByteBuffer buffer =
        ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : values) {
      buffer.putFloat(value);
    }
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  private static HttpClient mockHttpClient(int statusCode, String body) throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    doReturn(statusCode).when(mockResponse).statusCode();
    doReturn(body).when(mockResponse).body();
    doReturn(mockResponse)
        .when(mockClient)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    doReturn(true).when(mockClient).awaitTermination(any(Duration.class));
    return mockClient;
  }

  @Test
  public void embed_decodesBase64FloatEmbeddings() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    String body =
        String.format(
            "{\"object\":\"list\",\"data\":[{\"embedding\":\"%s\",\"index\":0}],"
                + "\"usage\":{\"total_tokens\":1}}",
            base64Floats(1f, 2f, 3f));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, body));

    List<VectorOrError> results = client.embed(List.of("hello"), floatContext());

    assertEquals(1, results.size());
    assertTrue(results.get(0).vector.isPresent());
    assertEquals(3, results.get(0).vector.get().numDimensions());
  }

  @Test
  public void embed_emptyInputsReturnEmptyInputError() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, "{\"data\":[]}"));

    List<VectorOrError> results = client.embed(List.of("", ""), floatContext());

    assertEquals(2, results.size());
    assertEquals(VectorOrError.EMPTY_INPUT_ERROR, results.get(0));
    assertEquals(VectorOrError.EMPTY_INPUT_ERROR, results.get(1));
  }

  @Test
  public void embed_invalidRequestFailsFastAsPerInputError() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(
        client, mockHttpClient(400, "{\"error\":{\"message\":\"bad model\"}}"));

    List<VectorOrError> results = client.embed(List.of("hello"), floatContext());

    assertEquals(1, results.size());
    assertTrue(results.get(0).errorMessage.isPresent());
    assertTrue(results.get(0).vector.isEmpty());
  }

  @Test
  public void embed_nonFloatQuantizationFailsFast() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, "{\"data\":[]}"));

    EmbeddingRequestContext scalarContext =
        new EmbeddingRequestContext(
            "testdb", "testIndex", "testCollection", 3, VectorAutoEmbedQuantization.SCALAR);

    org.junit.Assert.assertThrows(
        EmbeddingProviderNonTransientException.class,
        () -> client.embed(List.of("hello"), scalarContext));
  }

  @Test
  public void embed_attachesBearerHeaderOnlyWhenApiKeyPresent() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));

    // With API key -> Authorization header present.
    OpenAiCompatClient withKey = newClient(openAiModel(Optional.of("secret-key")));
    HttpClient withKeyHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(withKey, withKeyHttp);
    withKey.embed(List.of("hello"), floatContext());
    HttpRequest withKeyRequest = captureRequest(withKeyHttp);
    assertTrue(withKeyRequest.headers().firstValue("Authorization").isPresent());
    assertEquals(
        "Bearer secret-key", withKeyRequest.headers().firstValue("Authorization").get());

    // Without API key -> no Authorization header (keyless local engine).
    OpenAiCompatClient withoutKey = newClient(openAiModel(Optional.empty()));
    HttpClient withoutKeyHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(withoutKey, withoutKeyHttp);
    withoutKey.embed(List.of("hello"), floatContext());
    HttpRequest withoutKeyRequest = captureRequest(withoutKeyHttp);
    assertFalse(withoutKeyRequest.headers().firstValue("Authorization").isPresent());
  }

  @Test
  public void embed_usesAzureApiKeyHeaderWhenConfigured() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));

    // authHeaderName=api-key (Azure OpenAI) -> raw key in api-key header, no Bearer, no
    // Authorization header.
    OpenAiCompatClient client =
        newClient(openAiModel(Optional.of("secret-key"), Optional.of("api-key")));
    HttpClient http = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(client, http);
    client.embed(List.of("hello"), floatContext());
    HttpRequest request = captureRequest(http);
    assertEquals("secret-key", request.headers().firstValue("api-key").orElse(null));
    assertFalse(request.headers().firstValue("Authorization").isPresent());
  }

  @Test
  public void embed_forwardsDimensionsOnlyWhenOptedIn() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));

    // forwardDimensions=true -> `dimensions` in request body, and the value is the index's
    // resolved numDimensions from the context (512), NOT the catalog default (1536). one client
    // serves many indexes, so the per-request dimension must win.
    EmbeddingServiceConfig.OpenAiModelConfig withForward =
        new EmbeddingServiceConfig.OpenAiModelConfig(
            Optional.of(1536), Optional.of(96), Optional.of(120_000), Optional.empty(),
            Optional.of(true));
    OpenAiCompatClient forwardClient =
        newClient(openAiModel(Optional.empty(), Optional.empty(), withForward));
    HttpClient forwardHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(forwardClient, forwardHttp);
    EmbeddingRequestContext forwardContext =
        new EmbeddingRequestContext(
            "testdb", "testIndex", "testCollection", 512, VectorAutoEmbedQuantization.FLOAT);
    forwardClient.embed(List.of("hello"), forwardContext);
    String forwardBody = requestBody(captureRequest(forwardHttp));
    assertTrue(
        "Expected the context dimension forwarded when opted in: " + forwardBody,
        forwardBody.contains("\"dimensions\"") && forwardBody.contains("512"));
    assertFalse(
        "Catalog default must not be forwarded; the per-request dimension wins: " + forwardBody,
        forwardBody.contains("1536"));

    // default (no forwardDimensions): outputDimensions is set but NOT forwarded
    // (local engines reject the dimensions field).
    EmbeddingServiceConfig.OpenAiModelConfig noForward =
        new EmbeddingServiceConfig.OpenAiModelConfig(
            Optional.of(768), Optional.of(96), Optional.of(120_000), Optional.empty());
    OpenAiCompatClient defaultClient =
        newClient(openAiModel(Optional.empty(), Optional.empty(), noForward));
    HttpClient defaultHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(defaultClient, defaultHttp);
    defaultClient.embed(List.of("hello"), floatContext());
    String defaultBody = requestBody(captureRequest(defaultHttp));
    assertFalse(
        "Expected no dimensions field by default: " + defaultBody,
        defaultBody.contains("\"dimensions\""));
  }

  @Test
  public void embed_prependsTierAwarePrefix() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));
    EmbeddingServiceConfig.OpenAiModelConfig withPrefixes =
        new EmbeddingServiceConfig.OpenAiModelConfig(
            Optional.empty(),
            Optional.of(96),
            Optional.of(120_000),
            Optional.empty(),
            Optional.empty(),
            Optional.of("search_query: "),
            Optional.of("search_document: "));
    EmbeddingModelConfig model = openAiModel(Optional.empty(), Optional.empty(), withPrefixes);

    // QUERY tier -> queryPrefix.
    OpenAiCompatClient queryClient = newClient(model, ServiceTier.QUERY);
    HttpClient queryHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(queryClient, queryHttp);
    queryClient.embed(List.of("hello"), floatContext());
    assertTrue(requestBody(captureRequest(queryHttp)).contains("search_query: hello"));

    // Indexing tier (collection scan) -> documentPrefix.
    OpenAiCompatClient scanClient = newClient(model, ServiceTier.COLLECTION_SCAN);
    HttpClient scanHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(scanClient, scanHttp);
    scanClient.embed(List.of("hello"), floatContext());
    assertTrue(requestBody(captureRequest(scanHttp)).contains("search_document: hello"));

    // No prefixes configured -> input sent verbatim.
    OpenAiCompatClient plainClient = newClient(openAiModel(Optional.empty()), ServiceTier.QUERY);
    HttpClient plainHttp = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(plainClient, plainHttp);
    plainClient.embed(List.of("hello"), floatContext());
    assertFalse(requestBody(captureRequest(plainHttp)).contains("search_query"));
  }

  @Test
  public void embed_rateLimited429_throwsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(429, "{\"error\":\"rate limit\"}"));

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));
  }

  @Test
  public void embed_authFailure401or403_throwsNonTransient() throws Exception {
    // A bad/missing API key must fail fast (non-transient), not be retried as a transient error.
    for (int statusCode : new int[] {401, 403}) {
      OpenAiCompatClient client = newClient(openAiModel(Optional.of("bad-key")));
      OpenAiCompatClient.injectHttpClient(
          client, mockHttpClient(statusCode, "{\"error\":\"unauthorized\"}"));

      org.junit.Assert.assertThrows(
          "HTTP " + statusCode + " should be non-transient",
          EmbeddingProviderNonTransientException.class,
          () -> client.embed(List.of("hello"), floatContext()));
    }
  }

  @Test
  public void embed_timeout408_throwsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(408, "{\"error\":\"timeout\"}"));

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));
  }

  @Test
  public void embed_serverError500_throwsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(500, "internal error"));

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));
  }

  @Test
  public void embed_fewerVectorsThanInputs_throwsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    // Two non-empty inputs but the engine returns a single embedding.
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, body));

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello", "world"), floatContext()));
  }

  @Test
  public void embed_mixedEmptyAndNonEmptyInputs_backfillsEmptyInputErrors() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    // One embedding for the single non-empty input; the empty input gets a back-filled error.
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, body));

    List<VectorOrError> results = client.embed(List.of("", "hello"), floatContext());

    assertEquals(2, results.size());
    assertEquals(VectorOrError.EMPTY_INPUT_ERROR, results.get(0));
    assertTrue(results.get(1).vector.isPresent());
  }

  @Test
  public void embed_malformedResponseBody_throwsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    // Valid JSON but missing the required `data` field -> parse error -> transient (retryable).
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, "{\"object\":\"list\"}"));

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));
  }

  @Test
  public void embed_interruptedDuringSend_throwsTransientAndRestoresInterruptFlag()
      throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    doThrow(new InterruptedException("interrupted"))
        .when(http)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    OpenAiCompatClient.injectHttpClient(client, http);

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));

    assertTrue("Interrupt flag must be restored", Thread.interrupted());
  }

  @Test
  public void embed_connectTimeout_renewsHttpClientAndThrowsTransient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    doThrow(new HttpConnectTimeoutException("connect timed out"))
        .when(http)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    OpenAiCompatClient.injectHttpClient(client, http);

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));

    assertTrue(
        "Expected a fresh HttpClient after a connect timeout", httpClientField(client) != http);
  }

  @Test
  public void embed_connectionLayerIoException_renewsHttpClient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    doThrow(new IOException("connection reset by peer"))
        .when(http)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    OpenAiCompatClient.injectHttpClient(client, http);

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));

    assertTrue(
        "Expected a fresh HttpClient after a connection-layer failure",
        httpClientField(client) != http);
  }

  @Test
  public void embed_sslFailureCause_renewsHttpClient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    doThrow(new IOException(new SSLException("handshake failed")))
        .when(http)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    OpenAiCompatClient.injectHttpClient(client, http);

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));

    assertTrue(
        "Expected a fresh HttpClient after a TLS failure", httpClientField(client) != http);
  }

  @Test
  public void embed_nonConnectionIoException_keepsHttpClient() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    doThrow(new IOException("truncated body"))
        .when(http)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    OpenAiCompatClient.injectHttpClient(client, http);

    org.junit.Assert.assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("hello"), floatContext()));

    assertTrue(
        "A non-connection IOException must not renew the HttpClient",
        httpClientField(client) == http);
  }

  @Test
  public void renewHttpClientIfStale_replacesClientAfterRefreshInterval() throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient http = mockHttpClient(200, "{}");
    OpenAiCompatClient.injectHttpClient(client, http);

    // Not stale yet -> kept.
    client.renewHttpClientIfStaleForTesting();
    assertTrue("Client must be kept before the refresh interval", httpClientField(client) == http);

    // Age the client past the refresh interval -> replaced.
    setHttpClientCreatedEpochMs(
        client, System.currentTimeMillis() - Duration.ofMinutes(11).toMillis());
    client.renewHttpClientIfStaleForTesting();
    assertTrue(
        "Client must be replaced after the refresh interval", httpClientField(client) != http);
  }

  @Test
  public void renewHttpClientAfterConnectionFailure_skipsWhenClientAlreadyReplaced()
      throws Exception {
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()));
    HttpClient current = mockHttpClient(200, "{}");
    OpenAiCompatClient.injectHttpClient(client, current);

    // The failing client was already replaced (different reference) -> no duplicate renewal.
    HttpClient staleCulprit = mockHttpClient(200, "{}");
    client.renewHttpClientAfterConnectionFailureForTesting(
        new IOException("connection reset"), staleCulprit);

    assertTrue(
        "Renewal must be skipped when the culprit client was already replaced",
        httpClientField(client) == current);
  }

  @Test
  public void updateConfig_appliesNewCredentialsPrefixAndDimensions() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));
    // Start keyless with no prefix and no dimensions forwarding.
    OpenAiCompatClient client = newClient(openAiModel(Optional.empty()), ServiceTier.QUERY);

    EmbeddingServiceConfig.OpenAiModelConfig newModelConfig =
        new EmbeddingServiceConfig.OpenAiModelConfig(
            Optional.of(768),
            Optional.of(96),
            Optional.of(120_000),
            Optional.empty(),
            Optional.of(true),
            Optional.of("search_query: "),
            Optional.of("search_document: "));
    EmbeddingModelConfig updated =
        openAiModel(Optional.of("new-key"), Optional.of("api-key"), newModelConfig);
    client.updateConfig(updated.query());

    HttpClient http = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(client, http);
    client.embed(List.of("hello"), floatContext());

    HttpRequest request = captureRequest(http);
    assertEquals("new-key", request.headers().firstValue("api-key").orElse(null));
    String sentBody = requestBody(request);
    assertTrue(sentBody.contains("search_query: hello"));
    assertTrue(sentBody.contains("\"dimensions\""));
  }

  @Test
  public void updateConfig_nonOpenAiWorkloadParams_fallsBackToDefaults() throws Exception {
    String body =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64Floats(1f, 2f, 3f));
    OpenAiCompatClient client =
        newClient(openAiModel(Optional.of("secret-key"), Optional.of("api-key")));

    // A non-OpenAI (Voyage) config exercises the defensive fallbacks: no key, default
    // Authorization header, no prefix, and the default endpoint.
    EmbeddingServiceConfig.VoyageEmbeddingCredentials voyageCreds =
        new EmbeddingServiceConfig.VoyageEmbeddingCredentials(
            "token", "2024-10-15T22:32:20.925Z");
    EmbeddingServiceConfig.EmbeddingConfig voyageConfig =
        new EmbeddingServiceConfig.EmbeddingConfig(
            Optional.empty(),
            new EmbeddingServiceConfig.VoyageModelConfig(
                Optional.of(1024),
                Optional.of(EmbeddingServiceConfig.TruncationOption.NONE),
                Optional.of(100),
                Optional.of(120_000)),
            RETRY_CONFIG,
            voyageCreds,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            Optional.empty(),
            false,
            Optional.empty());
    EmbeddingModelConfig voyageModel =
        EmbeddingModelConfig.create(
            "voyage-4", EmbeddingServiceConfig.EmbeddingProvider.VOYAGE, voyageConfig);
    client.updateConfig(voyageModel.query());

    HttpClient http = mockHttpClient(200, body);
    OpenAiCompatClient.injectHttpClient(client, http);
    client.embed(List.of("hello"), floatContext());

    HttpRequest request = captureRequest(http);
    assertEquals(OpenAiCompatClient.DEFAULT_ENDPOINT, request.uri().toString());
    assertFalse(request.headers().firstValue("api-key").isPresent());
    assertFalse(request.headers().firstValue("Authorization").isPresent());
  }

  @Test
  public void embed_invalidAuthHeader_redactsApiKeyInError() throws Exception {
    // An invalid header name makes HttpRequest.Builder throw IllegalArgumentException; the client
    // must wrap it as transient and redact the API key from the message.
    OpenAiCompatClient client =
        newClient(openAiModel(Optional.of("secret-key"), Optional.of("bad\nheader")));
    OpenAiCompatClient.injectHttpClient(client, mockHttpClient(200, "{}"));

    EmbeddingProviderTransientException e =
        org.junit.Assert.assertThrows(
            EmbeddingProviderTransientException.class,
            () -> client.embed(List.of("hello"), floatContext()));

    String message = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : "");
    assertFalse("API key must be redacted: " + message, message.contains("secret-key"));
  }

  private static HttpClient httpClientField(OpenAiCompatClient client) throws Exception {
    Field field = OpenAiCompatClient.class.getDeclaredField("httpClient");
    field.setAccessible(true);
    return (HttpClient) field.get(client);
  }

  private static void setHttpClientCreatedEpochMs(OpenAiCompatClient client, long epochMs)
      throws Exception {
    Field field = OpenAiCompatClient.class.getDeclaredField("httpClientCreatedEpochMs");
    field.setAccessible(true);
    field.setLong(client, epochMs);
  }

  private static HttpRequest captureRequest(HttpClient httpClient) throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    org.mockito.Mockito.verify(httpClient)
        .send(captor.capture(), any(HttpResponse.BodyHandler.class));
    return captor.getValue();
  }

  /** Reads the JSON body out of the request's {@link HttpRequest.BodyPublisher}. */
  private static String requestBody(HttpRequest request) throws Exception {
    HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
    StringBuilder sb = new StringBuilder();
    CountDownLatch latch = new CountDownLatch(1);
    publisher.subscribe(
        new Flow.Subscriber<ByteBuffer>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(ByteBuffer item) {
            sb.append(StandardCharsets.UTF_8.decode(item));
          }

          @Override
          public void onError(Throwable throwable) {
            latch.countDown();
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });
    latch.await(5, TimeUnit.SECONDS);
    return sb.toString();
  }
}
