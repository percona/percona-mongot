package com.xgen.mongot.embedding.providers.clients;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.embedding.EmbeddingRequestContext;
import com.xgen.mongot.embedding.MongotMetadata;
import com.xgen.mongot.embedding.VectorOrError;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderNonTransientException;
import com.xgen.mongot.embedding.exceptions.EmbeddingProviderTransientException;
import com.xgen.mongot.embedding.providers.configs.EmbeddingModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.HuggingFaceApiSchema;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import com.xgen.mongot.metrics.MetricsFactory;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.concurrent.OneShotSingleThreadExecutor;
import io.micrometer.core.instrument.Counter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding client for Hugging Face's hosted Inference API (Inference Providers, {@code
 * hf-inference} provider, feature-extraction pipeline). One instance per model + service tier.
 *
 * <p>Default endpoint: {@code
 * https://router.huggingface.co/hf-inference/models/{modelId}/pipeline/feature-extraction}. The
 * legacy {@code api-inference.huggingface.co} host is retired. {@code providerEndpoint} overrides
 * the URL verbatim, e.g. for a dedicated Inference Endpoint or a self-hosted TEI {@code /embed},
 * which speak the same request/response shape.
 *
 * <p>Auth is {@code Authorization: Bearer <hf token>}. float vectors only.
 */
public class HuggingFaceClient implements ClientInterface {
  private static final Logger LOG = LoggerFactory.getLogger(HuggingFaceClient.class);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  /** Wall-clock interval after which the {@link HttpClient} is replaced to refresh connections. */
  private static final Duration HTTP_CLIENT_REFRESH_INTERVAL = Duration.ofMinutes(10);

  /** Minimum interval between connection-failure-triggered renewals during a sustained outage. */
  private static final Duration CONNECTION_FAILURE_RENEWAL_COOLDOWN = Duration.ofSeconds(5);

  @VisibleForTesting static final String ROUTER_BASE_URL = "https://router.huggingface.co";

  private static final String REDACTED = "<REDACTED-API-TOKEN>";
  private static final Pattern BEARER_TOKEN = Pattern.compile("Bearer [^\"\\s]+");
  private static final Pattern HF_TOKEN = Pattern.compile("hf_[A-Za-z0-9]+");

  private final String catalogModelName;
  private final EmbeddingServiceConfig.ServiceTier serviceTier;
  private final String userAgent;
  private final Counter invalidRequestCounter;

  /**
   * Fields {@link #updateConfig} can change, swapped atomically so a concurrent {@link #embed}
   * never sees e.g. a new endpoint with an old token.
   */
  @VisibleForTesting
  record RequestConfig(
      URI endpoint,
      Optional<String> apiToken,
      Optional<Boolean> normalize,
      boolean truncate,
      String inputPrefix) {}

  private volatile RequestConfig requestConfig;

  private volatile HttpClient httpClient;
  private volatile long httpClientCreatedEpochMs;

  /** Guarded by {@code synchronized(this)}. 0 means never. */
  private long lastConnectionFailureRenewalEpochMs;

  HuggingFaceClient(
      EmbeddingModelConfig embeddingModelConfig,
      EmbeddingServiceConfig.ServiceTier tier,
      EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams,
      MetricsFactory metricsFactory,
      Optional<MongotMetadata> metadata) {
    this.catalogModelName = embeddingModelConfig.name();
    this.serviceTier = tier;
    this.userAgent =
        metadata
            .map(m -> String.format("mongot/%s (%s)", m.mongotVersion(), m.mongotHostName()))
            .orElse("mongot/UNKNOWN (UNKNOWN)");
    this.invalidRequestCounter = metricsFactory.counter("invalidRequestCounter");
    this.httpClient = newHttpClient();
    this.httpClientCreatedEpochMs = System.currentTimeMillis();
    this.requestConfig = buildRequestConfig(this.catalogModelName, workloadParams, tier);
    LOG.debug(
        "Initialized Hugging Face client: model={}, endpoint={}, tier={}, apiToken={}",
        this.catalogModelName,
        this.requestConfig.endpoint(),
        tier,
        this.requestConfig.apiToken().isPresent() ? "set" : "none");
  }

  @Override
  public List<VectorOrError> embed(List<String> inputs, EmbeddingRequestContext context)
      throws EmbeddingProviderTransientException, EmbeddingProviderNonTransientException {
    if (context.autoEmbedQuantization() != VectorAutoEmbedQuantization.FLOAT) {
      throw new EmbeddingProviderNonTransientException(
          "HUGGINGFACE_INFERENCE provider supports only float embeddings; quantization '"
              + context.autoEmbedQuantization().getName()
              + "' is not supported.");
    }

    // empty strings are rejected by the API: drop them and back-fill EMPTY_INPUT_ERROR
    List<String> filteredInput = inputs.stream().filter(text -> !text.isEmpty()).toList();
    if (filteredInput.isEmpty()) {
      return inputs.stream().map(ignored -> VectorOrError.EMPTY_INPUT_ERROR).toList();
    }

    RequestConfig config = this.requestConfig;
    LOG.debug(
        "Sending Hugging Face embedding request: model={}, endpoint={}, inputCount={},"
            + " database={}, collection={}",
        this.catalogModelName,
        config.endpoint(),
        filteredInput.size(),
        context.database(),
        context.collectionName());

    HttpRequest request;
    try {
      request = buildRequest(filteredInput, config);
    } catch (IllegalArgumentException e) {
      // the JDK's message can echo the offending header value (the token): don't attach it
      throw new EmbeddingProviderNonTransientException(
          "Invalid Hugging Face request: check credentials.apiToken for stray whitespace or"
              + " control characters");
    }

    renewHttpClientIfStale();
    HttpClient clientForRequest = this.httpClient;
    try {
      HttpResponse<String> response =
          clientForRequest.send(request, HttpResponse.BodyHandlers.ofString());
      return extractVectorsFromResponse(response, inputs, filteredInput.size(), context, config);
    } catch (HttpTimeoutException e) {
      if (e instanceof HttpConnectTimeoutException) {
        renewHttpClientAfterConnectionFailure(e, clientForRequest);
      }
      LOG.error("Timed out sending Hugging Face embedding request", e);
      throw new EmbeddingProviderTransientException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EmbeddingProviderTransientException(e);
    } catch (IOException e) {
      if (ConnectionFailures.indicatesConnectionLayerFailure(e)) {
        renewHttpClientAfterConnectionFailure(e, clientForRequest);
      }
      LOG.error("Got an error sending Hugging Face embedding request", e);
      throw new EmbeddingProviderTransientException(e);
    }
  }

  @Override
  public void updateConfig(EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams) {
    this.requestConfig =
        buildRequestConfig(this.catalogModelName, workloadParams, this.serviceTier);
  }

  @VisibleForTesting
  RequestConfig requestConfigForTesting() {
    return this.requestConfig;
  }

  private static RequestConfig buildRequestConfig(
      String catalogModelName,
      EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams,
      EmbeddingServiceConfig.ServiceTier tier) {
    Optional<EmbeddingServiceConfig.HuggingFaceModelConfig> modelConfig =
        workloadParams.modelConfig() instanceof EmbeddingServiceConfig.HuggingFaceModelConfig hf
            ? Optional.of(hf)
            : Optional.empty();
    String modelId =
        modelConfig.map(hf -> hf.modelIdOrDefault(catalogModelName)).orElse(catalogModelName);
    Optional<String> apiToken =
        workloadParams.credentials()
                instanceof EmbeddingServiceConfig.HuggingFaceEmbeddingCredentials hfCreds
                && hfCreds.hasToken()
            ? hfCreds.apiToken
            : Optional.empty();
    return new RequestConfig(
        URI.create(workloadParams.providerEndpoint().orElseGet(() -> defaultEndpoint(modelId))),
        apiToken,
        modelConfig.flatMap(hf -> hf.normalize),
        modelConfig.map(EmbeddingServiceConfig.HuggingFaceModelConfig::shouldTruncate).orElse(true),
        modelConfig.map(hf -> hf.inputPrefixForTier(tier)).orElse(""));
  }

  @VisibleForTesting
  static String defaultEndpoint(String modelId) {
    return ROUTER_BASE_URL + "/hf-inference/models/" + modelId + "/pipeline/feature-extraction";
  }

  private HttpRequest buildRequest(List<String> inputs, RequestConfig config) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(config.endpoint())
            .timeout(DEFAULT_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("User-Agent", this.userAgent);
    config.apiToken().ifPresent(token -> builder.header("Authorization", "Bearer " + token));

    List<String> prefixed =
        config.inputPrefix().isEmpty()
            ? inputs
            : inputs.stream().map(text -> config.inputPrefix() + text).toList();
    String body =
        new HuggingFaceApiSchema.EmbedRequest(
                prefixed, config.normalize(), Optional.of(config.truncate()))
            .toBson()
            .toJson();
    return builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
  }

  private List<VectorOrError> extractVectorsFromResponse(
      HttpResponse<String> response,
      List<String> inputs,
      int requestedCount,
      EmbeddingRequestContext context,
      RequestConfig config)
      throws EmbeddingProviderTransientException,
          EmbeddingProviderNonTransientException,
          HttpTimeoutException {
    int statusCode = response.statusCode();
    // only error bodies end up in messages; a 2xx body is the whole embedding array
    String body =
        statusCode >= 200 && statusCode < 300
            ? ""
            : redactToken(response.body(), config.apiToken());
    if (statusCode == 400 || statusCode == 422) {
      String errorMessage =
          String.format(
              "Got invalid request, fail fast and give up retries. Response body: %s.", body);
      LOG.warn(errorMessage);
      this.invalidRequestCounter.increment();
      return inputs.stream()
          .map(
              input ->
                  input.isEmpty()
                      ? VectorOrError.EMPTY_INPUT_ERROR
                      : new VectorOrError(errorMessage))
          .toList();
    }
    if (statusCode == 408) {
      throw new HttpTimeoutException("Timeout exception (HTTP 408). Response body: " + body);
    }
    if (statusCode == 429) {
      throw new EmbeddingProviderTransientException(
          "Rate limit exceeded (HTTP 429). Response body: " + body,
          EmbeddingProviderTransientException.Reason.RATE_LIMIT_EXCEEDED);
    }
    if (statusCode == 401 || statusCode == 403) {
      this.invalidRequestCounter.increment();
      throw new EmbeddingProviderNonTransientException(
          String.format(
              "Authentication failed (HTTP %d): check credentials.apiToken is a valid Hugging Face"
                  + " access token with the \"Make calls to Inference Providers\" permission."
                  + " Response body: %s",
              statusCode, body));
    }
    if (statusCode == 402) {
      this.invalidRequestCounter.increment();
      throw new EmbeddingProviderNonTransientException(
          "Payment required (HTTP 402): the Hugging Face account's monthly Inference Providers"
              + " credits are likely exhausted; add credits or upgrade the plan. Response body: "
              + body);
    }
    if (statusCode >= 400 && statusCode < 500) {
      // 404: model not served by hf-inference (or a wrong providerEndpoint); 413: batch too big.
      // Retrying the identical request gets the identical answer.
      this.invalidRequestCounter.increment();
      throw new EmbeddingProviderNonTransientException(
          String.format(
              "Got client error (HTTP %d): check modelId/providerEndpoint (404) or lower"
                  + " batchSize (413). Response body: %s",
              statusCode, body));
    }
    if (statusCode < 200 || statusCode >= 300) {
      // 5xx, including 503 while a cold model loads
      throw new EmbeddingProviderTransientException(
          String.format("Got non OK status (HTTP %d). Response body: %s", statusCode, body));
    }

    List<Vector> vectors;
    try {
      vectors = HuggingFaceApiSchema.decodeEmbeddings(response.body());
    } catch (HuggingFaceApiSchema.MalformedResponseException e) {
      if (e.isTokenLevelOutput()) {
        throw new EmbeddingProviderNonTransientException(String.valueOf(e.getMessage()));
      }
      throw new EmbeddingProviderTransientException(e);
    }
    if (vectors.size() != requestedCount) {
      throw new EmbeddingProviderTransientException(
          String.format(
              "Embedding response has %d vectors for %d inputs", vectors.size(), requestedCount));
    }
    int expectedDimensions = context.outputDimension();
    int actualDimensions = vectors.get(0).numDimensions();
    if (actualDimensions != expectedDimensions) {
      throw new EmbeddingProviderNonTransientException(
          String.format(
              "Model %s returned %d-dimensional vectors but the index expects %d; set the catalog"
                  + " outputDimensions to the model's native dimension",
              this.catalogModelName, actualDimensions, expectedDimensions));
    }

    List<VectorOrError> results = new ArrayList<>(inputs.size());
    @Var int next = 0;
    for (String input : inputs) {
      if (input.isEmpty()) {
        results.add(VectorOrError.EMPTY_INPUT_ERROR);
      } else {
        results.add(new VectorOrError(vectors.get(next)));
        next++;
      }
    }
    return results;
  }

  /** Redact both the raw token and anything shaped like an HF token from provider messages. */
  @VisibleForTesting
  static String redactToken(String message, Optional<String> apiToken) {
    String patternRedacted =
        HF_TOKEN
            .matcher(BEARER_TOKEN.matcher(message).replaceAll("Bearer " + REDACTED))
            .replaceAll(REDACTED);
    return apiToken.map(token -> patternRedacted.replace(token, REDACTED)).orElse(patternRedacted);
  }

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
  }

  private void renewHttpClientIfStale() {
    long refreshMs = HTTP_CLIENT_REFRESH_INTERVAL.toMillis();
    if (System.currentTimeMillis() - this.httpClientCreatedEpochMs < refreshMs) {
      return;
    }
    synchronized (this) {
      if (System.currentTimeMillis() - this.httpClientCreatedEpochMs < refreshMs) {
        return;
      }
      replaceHttpClientLocked();
    }
  }

  private void renewHttpClientAfterConnectionFailure(Throwable cause, HttpClient culpritClient) {
    synchronized (this) {
      if (this.httpClient != culpritClient
          || System.currentTimeMillis() - this.lastConnectionFailureRenewalEpochMs
              < CONNECTION_FAILURE_RENEWAL_COOLDOWN.toMillis()) {
        return;
      }
      replaceHttpClientLocked();
      this.lastConnectionFailureRenewalEpochMs = this.httpClientCreatedEpochMs;
      LOG.warn(
          "Renewed Hugging Face HttpClient for model {} after connection/TLS failure: {}",
          this.catalogModelName,
          cause.toString());
    }
  }

  private void replaceHttpClientLocked() {
    HttpClient previous = this.httpClient;
    this.httpClient = newHttpClient();
    this.httpClientCreatedEpochMs = System.currentTimeMillis();
    new OneShotSingleThreadExecutor("huggingface-http-client-shutdown-" + this.catalogModelName)
        .execute(() -> shutdownReplacedHttpClient(previous));
  }

  private static void shutdownReplacedHttpClient(HttpClient previous) {
    try {
      previous.shutdown();
      // in-flight requests finish or hit their own timeout within DEFAULT_TIMEOUT
      if (!previous.awaitTermination(DEFAULT_TIMEOUT)) {
        previous.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      previous.shutdownNow();
    }
  }

  @VisibleForTesting
  HttpClient httpClientForTesting() {
    return this.httpClient;
  }

  @VisibleForTesting
  void renewHttpClientAfterConnectionFailureForTesting(Throwable cause, HttpClient culpritClient) {
    renewHttpClientAfterConnectionFailure(cause, culpritClient);
  }

  @VisibleForTesting
  void expireHttpClientForTesting() {
    this.httpClientCreatedEpochMs = 0;
  }

  @VisibleForTesting
  void renewHttpClientIfStaleForTesting() {
    renewHttpClientIfStale();
  }

  @VisibleForTesting
  static void injectHttpClient(HuggingFaceClient target, HttpClient mockHttpClient) {
    HttpClient previous = target.httpClient;
    target.httpClient = mockHttpClient;
    target.httpClientCreatedEpochMs = System.currentTimeMillis();
    shutdownReplacedHttpClient(previous);
  }
}
