package com.xgen.mongot.embedding.providers.clients;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.xgen.mongot.config.util.DeploymentEnvironment;
import com.xgen.mongot.embedding.EmbeddingRequestContext;
import com.xgen.mongot.embedding.MongotMetadata;
import com.xgen.mongot.embedding.VectorOrError;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderNonTransientException;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderTransientException;
import com.xgen.mongot.embedding.providers.configs.EmbeddingModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.EmbeddingProvider;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.HuggingFaceEmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.HuggingFaceModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.ServiceTier;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import com.xgen.mongot.metrics.MetricsFactory;
import com.xgen.mongot.util.bson.FloatVector;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class HuggingFaceClientTest {

  private static final EmbeddingServiceConfig.ErrorHandlingConfig RETRY_CONFIG =
      new EmbeddingServiceConfig.ErrorHandlingConfig(3, 10L, 10L, 0.1);

  private static final String TOKEN = "hf_TestToken123";

  private static EmbeddingRequestContext context(int dims) {
    return new EmbeddingRequestContext(
        "testdb", "testIndex", "testCollection", dims, VectorAutoEmbedQuantization.FLOAT);
  }

  private static HuggingFaceModelConfig modelConfig(
      Optional<Boolean> normalize,
      Optional<Boolean> truncate,
      Optional<String> queryPrefix,
      Optional<String> documentPrefix) {
    return new HuggingFaceModelConfig(
        Optional.of("BAAI/bge-small-en-v1.5"),
        Optional.of(3),
        Optional.of(32),
        Optional.of(120_000),
        Optional.of(VectorAutoEmbedQuantization.FLOAT),
        normalize,
        truncate,
        queryPrefix,
        documentPrefix);
  }

  private static HuggingFaceModelConfig defaultModelConfig() {
    return modelConfig(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static EmbeddingModelConfig hfModel(
      Optional<String> token, Optional<String> endpoint, HuggingFaceModelConfig modelConfig) {
    EmbeddingServiceConfig.EmbeddingConfig config =
        new EmbeddingServiceConfig.EmbeddingConfig(
            Optional.empty(),
            modelConfig,
            RETRY_CONFIG,
            new HuggingFaceEmbeddingCredentials(token),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            endpoint,
            false,
            Optional.empty());
    return EmbeddingModelConfig.create(
        "bge-small-en-v1.5", EmbeddingProvider.HUGGINGFACE_INFERENCE, config);
  }

  private static EmbeddingModelConfig hfModel() {
    return hfModel(Optional.of(TOKEN), Optional.empty(), defaultModelConfig());
  }

  private static EmbeddingModelConfig.ConsolidatedWorkloadParams paramsFor(
      EmbeddingModelConfig model, ServiceTier tier) {
    return switch (tier) {
      case QUERY -> model.query();
      case CHANGE_STREAM -> model.changeStream();
      case COLLECTION_SCAN -> model.collectionScan();
    };
  }

  private static HuggingFaceClient newClient(EmbeddingModelConfig model, ServiceTier tier) {
    ClientInterface client =
        new EmbeddingClientFactory(new SimpleMeterRegistry(), DeploymentEnvironment.COMMUNITY)
            .createEmbeddingClient(model, tier, paramsFor(model, tier));
    assertTrue(client instanceof HuggingFaceClient);
    return (HuggingFaceClient) client;
  }

  private static HuggingFaceClient newClient(EmbeddingModelConfig model) {
    return newClient(model, ServiceTier.QUERY);
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

  private static HttpClient throwingHttpClient(Exception exception) throws Exception {
    HttpClient mockClient = mock(HttpClient.class);
    doThrow(exception)
        .when(mockClient)
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    doReturn(true).when(mockClient).awaitTermination(any(Duration.class));
    return mockClient;
  }

  private static HuggingFaceClient clientReturning(int status, String body) throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HuggingFaceClient.injectHttpClient(client, mockHttpClient(status, body));
    return client;
  }

  private static float[] floats(VectorOrError result) {
    return ((FloatVector) result.vector.orElseThrow()).getFloatVector();
  }

  // ---- request shape ----

  @Test
  public void embed_defaultEndpoint_bearerToken_truncateOnByDefault() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient http = mockHttpClient(200, "[[0.1, 0.2, 0.3]]");
    HuggingFaceClient.injectHttpClient(client, http);

    List<VectorOrError> results = client.embed(List.of("hello"), context(3));

    assertEquals(1, results.size());
    assertEquals(0.2f, floats(results.get(0))[1], 1e-6f);
    HttpRequest request = captureRequest(http);
    // modelId keeps its case even though the catalog name is lowercased
    assertEquals(
        URI.create(
            "https://router.huggingface.co/hf-inference/models/BAAI/bge-small-en-v1.5"
                + "/pipeline/feature-extraction"),
        request.uri());
    assertEquals(Optional.of("Bearer " + TOKEN), request.headers().firstValue("Authorization"));
    assertEquals(Optional.of("application/json"), request.headers().firstValue("Content-Type"));
    assertEquals(
        Optional.of("mongot/UNKNOWN (UNKNOWN)"), request.headers().firstValue("User-Agent"));
    assertEquals(
        BsonDocument.parse("{inputs: ['hello'], truncate: true}"),
        BsonDocument.parse(requestBody(request)));
  }

  @Test
  public void embed_normalizeAndTruncateFlags_forwarded() throws Exception {
    HuggingFaceClient client =
        newClient(
            hfModel(
                Optional.of(TOKEN),
                Optional.empty(),
                modelConfig(
                    Optional.of(false), Optional.of(false), Optional.empty(), Optional.empty())));
    HttpClient http = mockHttpClient(200, "[[1, 2, 3]]");
    HuggingFaceClient.injectHttpClient(client, http);

    client.embed(List.of("x"), context(3));

    assertEquals(
        BsonDocument.parse("{inputs: ['x'], normalize: false, truncate: false}"),
        BsonDocument.parse(requestBody(captureRequest(http))));
  }

  @Test
  public void embed_modelIdDefaultsToCatalogName() {
    HuggingFaceClient client =
        newClient(
            hfModel(
                Optional.of(TOKEN),
                Optional.empty(),
                new HuggingFaceModelConfig(
                    Optional.empty(),
                    Optional.of(3),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())));
    assertEquals(
        URI.create(HuggingFaceClient.defaultEndpoint("bge-small-en-v1.5")),
        client.requestConfigForTesting().endpoint());
  }

  @Test
  public void embed_providerEndpointOverride_keylessSendsNoAuthorization() throws Exception {
    HuggingFaceClient client =
        newClient(
            hfModel(
                Optional.empty(),
                Optional.of("http://localhost:8080/embed"),
                defaultModelConfig()));
    HttpClient http = mockHttpClient(200, "[[1, 2, 3]]");
    HuggingFaceClient.injectHttpClient(client, http);

    client.embed(List.of("x"), context(3));

    HttpRequest request = captureRequest(http);
    assertEquals(URI.create("http://localhost:8080/embed"), request.uri());
    assertTrue(request.headers().firstValue("Authorization").isEmpty());
  }

  @Test
  public void embed_blankToken_treatedAsKeyless() {
    HuggingFaceClient client =
        newClient(hfModel(Optional.of("  "), Optional.empty(), defaultModelConfig()));
    assertTrue(client.requestConfigForTesting().apiToken().isEmpty());
  }

  @Test
  public void embed_tierPrefixes() throws Exception {
    EmbeddingModelConfig model =
        hfModel(
            Optional.of(TOKEN),
            Optional.empty(),
            modelConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.of("query: "),
                Optional.of("passage: ")));

    HuggingFaceClient queryClient = newClient(model, ServiceTier.QUERY);
    HttpClient queryHttp = mockHttpClient(200, "[[1, 2, 3]]");
    HuggingFaceClient.injectHttpClient(queryClient, queryHttp);
    queryClient.embed(List.of("hello"), context(3));
    assertTrue(requestBody(captureRequest(queryHttp)).contains("query: hello"));

    HuggingFaceClient scanClient = newClient(model, ServiceTier.COLLECTION_SCAN);
    HttpClient scanHttp = mockHttpClient(200, "[[1, 2, 3]]");
    HuggingFaceClient.injectHttpClient(scanClient, scanHttp);
    scanClient.embed(List.of("hello"), context(3));
    assertTrue(requestBody(captureRequest(scanHttp)).contains("passage: hello"));
  }

  @Test
  public void embed_userAgentFromMetadata() throws Exception {
    EmbeddingModelConfig model = hfModel();
    HuggingFaceClient client =
        new HuggingFaceClient(
            model,
            ServiceTier.QUERY,
            model.query(),
            new MetricsFactory("embeddingClient", new SimpleMeterRegistry(), Tags.empty()),
            Optional.of(new MongotMetadata("1.2.3", "host-a")));
    HttpClient http = mockHttpClient(200, "[[1, 2, 3]]");
    HuggingFaceClient.injectHttpClient(client, http);

    client.embed(List.of("x"), context(3));

    assertEquals(
        Optional.of("mongot/1.2.3 (host-a)"),
        captureRequest(http).headers().firstValue("User-Agent"));
  }

  @Test
  public void updateConfig_swapsTokenAndEndpoint() {
    HuggingFaceClient client = newClient(hfModel());
    EmbeddingModelConfig rotated =
        hfModel(
            Optional.of("hf_rotated"), Optional.of("http://tei:80/embed"), defaultModelConfig());

    client.updateConfig(rotated.query());

    assertEquals(Optional.of("hf_rotated"), client.requestConfigForTesting().apiToken());
    assertEquals(URI.create("http://tei:80/embed"), client.requestConfigForTesting().endpoint());
  }

  @Test
  public void embed_tokenWithControlCharacter_failsWithoutLeakingToken() {
    String badToken = "hf_bad\ntoken";
    HuggingFaceClient client =
        newClient(hfModel(Optional.of(badToken), Optional.empty(), defaultModelConfig()));

    EmbeddingProviderNonTransientException e =
        assertThrows(
            EmbeddingProviderNonTransientException.class,
            () -> client.embed(List.of("x"), context(3)));
    assertFalse(e.getMessage().contains("hf_bad"));
  }

  // ---- input handling ----

  @Test
  public void embed_emptyInputsBackfilledInOrder() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient http = mockHttpClient(200, "[[1, 1, 1], [2, 2, 2]]");
    HuggingFaceClient.injectHttpClient(client, http);

    List<VectorOrError> results = client.embed(List.of("a", "", "b"), context(3));

    assertEquals(3, results.size());
    assertEquals(1f, floats(results.get(0))[0], 0f);
    assertSame(VectorOrError.EMPTY_INPUT_ERROR, results.get(1));
    assertEquals(2f, floats(results.get(2))[0], 0f);
    assertEquals(
        BsonDocument.parse("{inputs: ['a', 'b'], truncate: true}"),
        BsonDocument.parse(requestBody(captureRequest(http))));
  }

  @Test
  public void embed_allEmptyInputs_noHttpCall() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient http = mockHttpClient(200, "[]");
    HuggingFaceClient.injectHttpClient(client, http);

    List<VectorOrError> results = client.embed(List.of("", ""), context(3));

    assertEquals(
        List.of(VectorOrError.EMPTY_INPUT_ERROR, VectorOrError.EMPTY_INPUT_ERROR), results);
    verify(http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
  }

  @Test
  public void embed_nonFloatQuantization_failsFast() {
    HuggingFaceClient client = newClient(hfModel());
    EmbeddingRequestContext scalar =
        new EmbeddingRequestContext("db", "idx", "coll", 3, VectorAutoEmbedQuantization.SCALAR);
    assertThrows(
        EmbeddingProviderNonTransientException.class, () -> client.embed(List.of("x"), scalar));
  }

  // ---- response handling ----

  @Test
  public void embed_400and422_perInputErrorsWithRedactedToken() throws Exception {
    for (int status : new int[] {400, 422}) {
      HuggingFaceClient client =
          clientReturning(status, "{\"error\":\"bad input, token " + TOKEN + "\"}");

      List<VectorOrError> results = client.embed(List.of("a", ""), context(3));

      assertEquals(2, results.size());
      String message = results.get(0).errorMessage.orElseThrow();
      assertTrue(message.contains("bad input"));
      assertFalse(message.contains(TOKEN));
      assertSame(VectorOrError.EMPTY_INPUT_ERROR, results.get(1));
    }
  }

  @Test
  public void embed_401and403_nonTransientWithRedactedToken() throws Exception {
    for (int status : new int[] {401, 403}) {
      HuggingFaceClient client =
          clientReturning(status, "{\"error\":\"Invalid credentials in Bearer " + TOKEN + "\"}");
      EmbeddingProviderNonTransientException e =
          assertThrows(
              EmbeddingProviderNonTransientException.class,
              () -> client.embed(List.of("a"), context(3)));
      assertTrue(e.getMessage().contains("HTTP " + status));
      assertTrue(e.getMessage().contains("Inference Providers"));
      assertFalse(e.getMessage().contains(TOKEN));
    }
  }

  @Test
  public void embed_402_nonTransientMentionsCredits() throws Exception {
    HuggingFaceClient client = clientReturning(402, "{\"error\":\"exceeded monthly credits\"}");
    EmbeddingProviderNonTransientException e =
        assertThrows(
            EmbeddingProviderNonTransientException.class,
            () -> client.embed(List.of("a"), context(3)));
    assertTrue(e.getMessage().contains("credits"));
  }

  @Test
  public void embed_other4xx_nonTransient() throws Exception {
    for (int status : new int[] {404, 413}) {
      HuggingFaceClient client = clientReturning(status, "Not Found");
      EmbeddingProviderNonTransientException e =
          assertThrows(
              EmbeddingProviderNonTransientException.class,
              () -> client.embed(List.of("a"), context(3)));
      assertTrue(e.getMessage().contains("HTTP " + status));
      // exception text can surface beyond logs; the endpoint may carry credentials
      assertFalse(
          e.getMessage().contains(client.requestConfigForTesting().endpoint().toString()));
    }
  }

  @Test
  public void embed_429_transientRateLimit() throws Exception {
    HuggingFaceClient client = clientReturning(429, "slow down");
    EmbeddingProviderTransientException e =
        assertThrows(
            EmbeddingProviderTransientException.class,
            () -> client.embed(List.of("a"), context(3)));
    assertEquals(EmbeddingProviderTransientException.Reason.RATE_LIMIT_EXCEEDED, e.getReason());
  }

  @Test
  public void embed_408and5xx_transient() throws Exception {
    for (int status : new int[] {408, 500, 503}) {
      HuggingFaceClient client = clientReturning(status, "{\"error\":\"Model is loading\"}");
      assertThrows(
          "status " + status,
          EmbeddingProviderTransientException.class,
          () -> client.embed(List.of("a"), context(3)));
    }
  }

  @Test
  public void embed_status1xx_transient() throws Exception {
    HuggingFaceClient client = clientReturning(102, "");
    assertThrows(
        EmbeddingProviderTransientException.class, () -> client.embed(List.of("a"), context(3)));
  }

  @Test
  public void embed_malformedBody_transient() throws Exception {
    HuggingFaceClient client = clientReturning(200, "<html>oops</html>");
    assertThrows(
        EmbeddingProviderTransientException.class, () -> client.embed(List.of("a"), context(3)));
  }

  @Test
  public void embed_tokenLevelOutput_nonTransient() throws Exception {
    HuggingFaceClient client = clientReturning(200, "[[[1, 2, 3], [4, 5, 6]]]");
    EmbeddingProviderNonTransientException e =
        assertThrows(
            EmbeddingProviderNonTransientException.class,
            () -> client.embed(List.of("a"), context(3)));
    assertTrue(e.getMessage().contains("token-level"));
  }

  @Test
  public void embed_vectorCountMismatch_transient() throws Exception {
    HuggingFaceClient client = clientReturning(200, "[[1, 2, 3]]");
    assertThrows(
        EmbeddingProviderTransientException.class,
        () -> client.embed(List.of("a", "b"), context(3)));
  }

  @Test
  public void embed_dimensionMismatch_nonTransient() throws Exception {
    HuggingFaceClient client = clientReturning(200, "[[1, 2, 3]]");
    EmbeddingProviderNonTransientException e =
        assertThrows(
            EmbeddingProviderNonTransientException.class,
            () -> client.embed(List.of("a"), context(384)));
    assertTrue(e.getMessage().contains("outputDimensions"));
  }

  // ---- transport failures ----

  @Test
  public void embed_connectionLayerFailure_transientAndRenewsClient() throws Exception {
    for (IOException failure :
        List.of(
            new IOException("wrapped", new ConnectException("no")),
            new HttpConnectTimeoutException("timeout"),
            new IOException("Connection reset by peer"))) {
      HuggingFaceClient client = newClient(hfModel());
      HttpClient failing = throwingHttpClient(failure);
      HuggingFaceClient.injectHttpClient(client, failing);

      assertThrows(
          EmbeddingProviderTransientException.class,
          () -> client.embed(List.of("a"), context(3)));
      assertNotSame(failure.getMessage(), failing, client.httpClientForTesting());
    }
  }

  @Test
  public void embed_plainIoException_transientWithoutRenewal() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient failing = throwingHttpClient(new IOException("stream reset"));
    HuggingFaceClient.injectHttpClient(client, failing);

    assertThrows(
        EmbeddingProviderTransientException.class, () -> client.embed(List.of("a"), context(3)));
    assertSame(failing, client.httpClientForTesting());
  }

  @Test
  public void embed_interrupted_transientAndRestoresInterruptFlag() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HuggingFaceClient.injectHttpClient(client, throwingHttpClient(new InterruptedException()));
    try {
      assertThrows(
          EmbeddingProviderTransientException.class,
          () -> client.embed(List.of("a"), context(3)));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void renewAfterConnectionFailure_skipsWhenAlreadyRenewedOrWithinCooldown()
      throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient first = mockHttpClient(200, "[]");
    HuggingFaceClient.injectHttpClient(client, first);

    client.renewHttpClientAfterConnectionFailureForTesting(new ConnectException("x"), first);
    HttpClient renewed = client.httpClientForTesting();
    assertNotSame(first, renewed);

    // stale culprit: another thread already renewed
    client.renewHttpClientAfterConnectionFailureForTesting(new ConnectException("x"), first);
    assertSame(renewed, client.httpClientForTesting());

    // current culprit but inside the cooldown window
    client.renewHttpClientAfterConnectionFailureForTesting(new ConnectException("x"), renewed);
    assertSame(renewed, client.httpClientForTesting());
  }

  @Test
  public void renewIfStale_replacesOnlyExpiredClient() throws Exception {
    HuggingFaceClient client = newClient(hfModel());
    HttpClient fresh = mockHttpClient(200, "[]");
    HuggingFaceClient.injectHttpClient(client, fresh);

    client.renewHttpClientIfStaleForTesting();
    assertSame(fresh, client.httpClientForTesting());

    client.expireHttpClientForTesting();
    client.renewHttpClientIfStaleForTesting();
    assertNotSame(fresh, client.httpClientForTesting());
  }

  @Test
  public void redactToken_removesRawTokenBearerAndHfShapedStrings() {
    String redacted =
        HuggingFaceClient.redactToken(
            "raw=secret-abc bearer=Bearer secret-abc other=hf_SomeOtherToken9",
            Optional.of("secret-abc"));
    assertFalse(redacted.contains("secret-abc"));
    assertFalse(redacted.contains("hf_SomeOtherToken9"));
    assertEquals(
        "no secrets here", HuggingFaceClient.redactToken("no secrets here", Optional.empty()));
  }

  // ---- helpers ----

  private static HttpRequest captureRequest(HttpClient httpClient) throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    return captor.getValue();
  }

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
