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
import com.xgen.mongot.embedding.providers.configs.OpenAiApiSchema;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import com.xgen.mongot.metrics.MetricsFactory;
import com.xgen.mongot.util.bson.JsonCodec;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.concurrent.OneShotSingleThreadExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLException;
import org.bson.BsonDocument;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedding client for any server speaking the OpenAI {@code /v1/embeddings} protocol (OpenAI,
 * Ollama, vLLM, HF TEI, Together, ...). One instance per model + service tier.
 *
 * <p>Unlike {@link VoyageClient} there's no multi-tenant routing, flex tier / AIMD, or billing
 * metadata: just an HTTP/2 client (renewed periodically and on failure), optional API-key auth
 * ({@code Authorization: Bearer} or an Azure {@code api-key} header), base64-float decoding, and
 * key redaction in logs.
 *
 * <p>float vectors only; scalar/binary quantization requests fail fast (not implemented yet).
 */
public class OpenAiCompatClient implements ClientInterface {
  private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatClient.class);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  /** Wall-clock interval after which the {@link HttpClient} is replaced to refresh connections. */
  private static final Duration HTTP_CLIENT_REFRESH_INTERVAL = Duration.ofMinutes(10);

  /**
   * Minimum interval between connection-failure-triggered renewals. Without this, a sustained
   * outage (e.g. a down local engine) renews on every single failed request, spawning a shutdown
   * thread each time for a client that never got a chance to succeed.
   */
  private static final Duration CONNECTION_FAILURE_RENEWAL_COOLDOWN = Duration.ofSeconds(5);

  /**
   * Graceful-shutdown grace period for a replaced client, matching {@link #DEFAULT_TIMEOUT}: any
   * request still in flight on it will either succeed or hit its own request-level timeout within
   * this window, so a legitimate slow-but-successful request is never force-cancelled by renewal.
   */
  private static final Duration HTTP_CLIENT_SHUTDOWN_AWAIT = DEFAULT_TIMEOUT;

  private static final Duration HTTP_CLIENT_SHUTDOWN_NOW_AWAIT = Duration.ofSeconds(2);

  @VisibleForTesting
  public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/embeddings";

  private final String modelId;
  private final EmbeddingServiceConfig.ServiceTier serviceTier;
  private final Optional<MongotMetadata> mongotMetadata;
  private final DistributionSummary inputTokenDistribution;
  private final Counter invalidRequestCounter;

  private static final String DEFAULT_AUTH_HEADER_NAME = "Authorization";

  /**
   * Snapshot of the fields {@link #updateConfig} can change. Held behind a single {@code volatile}
   * reference and swapped atomically so a config reload (e.g. key rotation) can't be observed by a
   * concurrent {@link #embed} call as a torn combination (new endpoint with an old key) or go
   * unseen entirely.
   */
  private record RequestConfig(
      URI endpoint,
      Optional<String> apiKey,
      String authHeaderName,
      // whether to send the OpenAI `dimensions` field (Matryoshka shrink on OpenAI/Azure). off for
      // local engines, which reject it. the value sent is the index's resolved numDimensions,
      // taken per request from the context — not the catalog default (one client serves many
      // indexes).
      boolean forwardDimensions,
      // prepended to each input; query and document tiers differ (queryPrefix/documentPrefix).
      // empty = no prefix.
      String inputPrefix) {}

  private volatile RequestConfig requestConfig;

  private volatile HttpClient httpClient;

  /**
   * Epoch millis when {@link #httpClient} was created; compared to {@link
   * #HTTP_CLIENT_REFRESH_INTERVAL} for renewal.
   */
  private volatile long httpClientCreatedEpochMs;

  /**
   * Epoch millis of the last connection-failure-triggered renewal. Guarded by {@code
   * synchronized(this)}: every read and write happens inside {@link
   * #renewHttpClientAfterConnectionFailure} / {@link #replaceHttpClientLocked}, so no {@code
   * volatile} is needed. 0 means "never" — the first failure always renews immediately.
   */
  private long lastConnectionFailureRenewalEpochMs;

  OpenAiCompatClient(
      EmbeddingModelConfig embeddingModelConfig,
      EmbeddingServiceConfig.ServiceTier tier,
      EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams,
      MetricsFactory metricsFactory,
      Optional<MongotMetadata> metadata) {
    this.modelId = embeddingModelConfig.name();
    this.serviceTier = tier;
    this.mongotMetadata = metadata;
    this.inputTokenDistribution = metricsFactory.summary("inputTokenDistribution");
    this.invalidRequestCounter = metricsFactory.counter("invalidRequestCounter");
    this.httpClient = newHttpClient();
    this.httpClientCreatedEpochMs = System.currentTimeMillis();
    // assign directly, not via updateConfig, so NullAway sees the @NonNull field set on every path
    this.requestConfig = buildRequestConfig(workloadParams, tier);
    LOG.debug(
        "Initialized OpenAI-compatible client: model={}, endpoint={}, tier={}, apiKey={},"
            + " authHeader={}",
        this.modelId,
        this.requestConfig.endpoint(),
        tier,
        this.requestConfig.apiKey().isPresent() ? "set" : "none",
        this.requestConfig.authHeaderName());
  }

  @Override
  public List<VectorOrError> embed(List<String> inputs, EmbeddingRequestContext context)
      throws EmbeddingProviderTransientException, EmbeddingProviderNonTransientException {
    // float only: the OpenAI protocol can't request quantized output, so fail fast
    if (context.autoEmbedQuantization() != VectorAutoEmbedQuantization.FLOAT) {
      throw new EmbeddingProviderNonTransientException(
          "OPENAI_COMPATIBLE provider currently supports only float embeddings; quantization '"
              + context.autoEmbedQuantization().getName()
              + "' is not supported.");
    }

    // The embeddings endpoint can't handle empty strings, so filter them and back-fill errors.
    List<String> filteredInput = inputs.stream().filter(text -> !text.isEmpty()).toList();
    if (filteredInput.isEmpty()) {
      return inputs.stream().map(ignored -> VectorOrError.EMPTY_INPUT_ERROR).toList();
    }

    // One snapshot for this call: a concurrent updateConfig() must not be observed as a torn
    // combination (e.g. new endpoint with an old key) partway through building/sending a request.
    RequestConfig requestConfig = this.requestConfig;

    LOG.debug(
        "Sending OpenAI-compatible embedding request: model={}, endpoint={}, inputCount={},"
            + " database={}, collection={}",
        this.modelId,
        requestConfig.endpoint(),
        filteredInput.size(),
        context.database(),
        context.collectionName());

    HttpRequest request;
    try {
      request = buildRequest(filteredInput, context, requestConfig);
    } catch (IllegalArgumentException e) {
      String message = e.getMessage();
      String cleanedMessage =
          message != null ? redactApiKey(message, requestConfig.apiKey()) : null;
      // e's own message may contain the raw header value (e.g. the API key), so it must not be
      // attached as a cause; copy only the stack trace to preserve where the failure occurred.
      // e.getCause() is always null here (HttpRequest.Builder throws IllegalArgumentException
      // directly, never wrapping another exception).
      IllegalArgumentException cleanedException =
          new IllegalArgumentException(cleanedMessage, e.getCause());
      cleanedException.setStackTrace(e.getStackTrace());
      LOG.error("HTTP Request Error", cleanedException);
      throw new EmbeddingProviderTransientException(cleanedException);
    }

    renewHttpClientIfStale();
    HttpClient clientForRequest = this.httpClient;
    try {
      HttpResponse<String> response =
          clientForRequest.send(request, HttpResponse.BodyHandlers.ofString());
      LOG.debug(
          "Received OpenAI-compatible embedding response: model={}, statusCode={}, inputCount={}",
          this.modelId,
          response.statusCode(),
          filteredInput.size());
      return extractVectorsFromResponse(response, inputs, requestConfig.apiKey());
    } catch (HttpTimeoutException e) {
      if (e instanceof HttpConnectTimeoutException) {
        renewHttpClientAfterConnectionFailure(e, clientForRequest);
      }
      LOG.error("Got timeout error when sending OpenAI-compatible embedding request", e);
      throw new EmbeddingProviderTransientException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Got an error when sending OpenAI-compatible embedding request", e);
      throw new EmbeddingProviderTransientException(e);
    } catch (IOException e) {
      if (indicatesConnectionLayerFailure(e)) {
        renewHttpClientAfterConnectionFailure(e, clientForRequest);
      }
      LOG.error("Got an error when sending OpenAI-compatible embedding request", e);
      throw new EmbeddingProviderTransientException(e);
    }
  }

  @Override
  public void updateConfig(EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams) {
    // single atomic swap: readers of this.requestConfig never see a partially-updated snapshot
    this.requestConfig = buildRequestConfig(workloadParams, this.serviceTier);
  }

  private static RequestConfig buildRequestConfig(
      EmbeddingModelConfig.ConsolidatedWorkloadParams workloadParams,
      EmbeddingServiceConfig.ServiceTier tier) {
    return new RequestConfig(
        URI.create(workloadParams.providerEndpoint().orElse(DEFAULT_ENDPOINT)),
        extractApiKey(workloadParams.credentials()),
        extractAuthHeaderName(workloadParams.credentials()),
        extractForwardDimensions(workloadParams.modelConfig()),
        extractInputPrefix(workloadParams.modelConfig(), tier));
  }

  /** Whether this model opts into forwarding the {@code dimensions} field. */
  private static boolean extractForwardDimensions(EmbeddingServiceConfig.ModelConfig modelConfig) {
    return modelConfig instanceof EmbeddingServiceConfig.OpenAiModelConfig openAiConfig
        && openAiConfig.shouldForwardDimensions();
  }

  /** Query/document prefix for this tier, or empty string when none is set. */
  private static String extractInputPrefix(
      EmbeddingServiceConfig.ModelConfig modelConfig, EmbeddingServiceConfig.ServiceTier tier) {
    if (modelConfig instanceof EmbeddingServiceConfig.OpenAiModelConfig openAiConfig) {
      return openAiConfig.inputPrefixForTier(tier);
    }
    return "";
  }

  private static Optional<String> extractApiKey(
      EmbeddingServiceConfig.EmbeddingCredentials credentials) {
    if (credentials instanceof EmbeddingServiceConfig.OpenAiEmbeddingCredentials openAiCreds) {
      return openAiCreds.apiKey.filter(key -> !key.isBlank());
    }
    return Optional.empty();
  }

  /** Auth header name, default {@code Authorization}; Azure uses {@code api-key}. */
  private static String extractAuthHeaderName(
      EmbeddingServiceConfig.EmbeddingCredentials credentials) {
    if (credentials instanceof EmbeddingServiceConfig.OpenAiEmbeddingCredentials openAiCreds) {
      return openAiCreds
          .authHeaderName
          .filter(name -> !name.isBlank())
          .orElse(DEFAULT_AUTH_HEADER_NAME);
    }
    return DEFAULT_AUTH_HEADER_NAME;
  }

  private HttpRequest buildRequest(
      List<String> inputs, EmbeddingRequestContext context, RequestConfig requestConfig) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder()
            .uri(requestConfig.endpoint())
            .timeout(DEFAULT_TIMEOUT)
            .header("Content-Type", "application/json");

    requestConfig
        .apiKey()
        .ifPresent(
            key ->
                requestBuilder.header(
                    requestConfig.authHeaderName(),
                    formatAuthHeaderValue(key, requestConfig.authHeaderName())));

    String userAgent =
        this.mongotMetadata
            .map(
                metadata ->
                    String.format(
                        "mongot/%s (%s)", metadata.mongotVersion(), metadata.mongotHostName()))
            .orElse("mongot/UNKNOWN (UNKNOWN)");
    requestBuilder.header("User-Agent", userAgent);

    // prepend the tier prefix when set (e.g. nomic "search_query: "). inputs are already non-empty.
    List<String> prefixedInputs =
        requestConfig.inputPrefix().isEmpty()
            ? inputs
            : inputs.stream().map(text -> requestConfig.inputPrefix() + text).toList();

    // only send `dimensions` when forwardDimensions is on; local engines reject it. the value is
    // the index's resolved numDimensions (per request), so Matryoshka models shrink to the size
    // the index actually wants — not the catalog default.
    Optional<Integer> dimensions =
        requestConfig.forwardDimensions()
            ? Optional.of(context.outputDimension())
            : Optional.empty();
    BsonDocument body =
        new OpenAiApiSchema.EmbedRequest(
                this.modelId, prefixedInputs, OpenAiApiSchema.DEFAULT_ENCODING_FORMAT, dimensions)
            .toBson();

    return requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body.toJson())).build();
  }

  /**
   * Authorization uses the Bearer scheme; other headers (Azure {@code api-key}) send the raw key.
   */
  private static String formatAuthHeaderValue(String key, String authHeaderName) {
    return authHeaderName.equalsIgnoreCase(DEFAULT_AUTH_HEADER_NAME) ? "Bearer " + key : key;
  }

  private List<VectorOrError> extractVectorsFromResponse(
      HttpResponse<String> response, List<String> inputs, Optional<String> apiKey)
      throws EmbeddingProviderTransientException, HttpTimeoutException {
    int statusCode = response.statusCode();
    if (statusCode == 400 || statusCode == 422) {
      String errorMessage =
          String.format(
              "Got invalid request, fail fast and give up retries. Response body: %s.",
              redactApiKey(response.body(), apiKey));
      LOG.warn(errorMessage);
      this.invalidRequestCounter.increment();
      // Empty inputs are a local validation error, not a provider error: keep them mapped to the
      // EMPTY_INPUT_ERROR singleton so callers that check for it by identity see the same result
      // as on the success path.
      return inputs.stream()
          .map(
              input ->
                  input.isEmpty()
                      ? VectorOrError.EMPTY_INPUT_ERROR
                      : new VectorOrError(errorMessage))
          .toList();
    }
    if (statusCode == 429) {
      throw new EmbeddingProviderTransientException(
          String.format(
              "Rate limit exceeded (HTTP 429). Response body: %s",
              redactApiKey(response.body(), apiKey)));
    }
    if (statusCode == 408) {
      throw new HttpTimeoutException(
          String.format(
              "Timeout exception (HTTP 408). Response body: %s",
              redactApiKey(response.body(), apiKey)));
    }
    if (statusCode == 401 || statusCode == 403) {
      // bad/missing key or no access — retrying won't help, so fail fast instead of burning retries
      this.invalidRequestCounter.increment();
      throw new EmbeddingProviderNonTransientException(
          String.format(
              "Authentication failed (HTTP %d): check the API key and authHeaderName"
                  + " (Authorization: Bearer vs Azure api-key). Response body: %s",
              statusCode, redactApiKey(response.body(), apiKey)));
    }
    if (statusCode >= 400 && statusCode < 500) {
      // Any other 4xx (e.g. 404 wrong endpoint path, 405, 410) is a permanent misconfiguration:
      // retrying the identical request gets the identical result, so fail fast instead of burning
      // retries.
      this.invalidRequestCounter.increment();
      throw new EmbeddingProviderNonTransientException(
          String.format(
              "Got client error from response (check the providerEndpoint path), status code: %s",
              statusCode));
    }
    if (statusCode < 200 || statusCode >= 300) {
      throw new EmbeddingProviderTransientException(
          String.format("Got non OK status from response, status code: %s", statusCode));
    }
    try {
      var embedResponse =
          OpenAiApiSchema.EmbedResponse.fromBson(
              BsonDocumentParser.fromRoot(JsonCodec.fromJson(response.body()))
                  .allowUnknownFields(true)
                  .build());
      embedResponse
          .usage
          .flatMap(usage -> usage.totalTokens.or(() -> usage.promptTokens))
          .ifPresent(tokens -> this.inputTokenDistribution.record(tokens));

      // Match vectors back to inputs by the response's own `index` field, not array order: some
      // OpenAI-compatible backends (batching/parallelizing proxies) return `data[]` out of
      // request order.
      Map<Integer, Vector> vectorsByRequestIndex = new HashMap<>();
      for (OpenAiApiSchema.EmbedVector vector : embedResponse.data) {
        vectorsByRequestIndex.put(vector.index, vector.embedding);
      }

      List<VectorOrError> results = new ArrayList<>();
      @Var int requestIndex = 0;
      for (String input : inputs) {
        if (input.isEmpty()) {
          results.add(VectorOrError.EMPTY_INPUT_ERROR);
        } else {
          Vector vector = vectorsByRequestIndex.get(requestIndex);
          if (vector == null) {
            throw new EmbeddingProviderTransientException(
                "Embedding response is missing a vector for request index " + requestIndex);
          }
          results.add(new VectorOrError(vector));
          requestIndex++;
        }
      }
      return results;
    } catch (BsonParseException e) {
      throw new EmbeddingProviderTransientException(e);
    }
  }

  /** Redact the API key from error messages: both the {@code Bearer <key>} form and the raw key. */
  private static String redactApiKey(String message, Optional<String> apiKey) {
    String bearerRedacted = message.replaceAll("Bearer [^\"\\s]+", "Bearer <REDACTED-API-KEY>");
    return apiKey
        .map(key -> bearerRedacted.replace(key, "<REDACTED-API-KEY>"))
        .orElse(bearerRedacted);
  }

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
  }

  /**
   * Replace the client periodically so HTTP/2 + TLS connections aren't held forever (DNS, idle
   * limits).
   */
  private void renewHttpClientIfStale() {
    long refreshMs = HTTP_CLIENT_REFRESH_INTERVAL.toMillis();
    if (System.currentTimeMillis() - this.httpClientCreatedEpochMs < refreshMs) {
      return;
    }
    synchronized (this) {
      if (System.currentTimeMillis() - this.httpClientCreatedEpochMs < refreshMs) {
        return;
      }
      replaceHttpClientLocked(false, null);
    }
  }

  /**
   * True if the error (or a cause) is a TLS/connection failure where a fresh client may help the
   * retry.
   */
  private static boolean indicatesConnectionLayerFailure(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      if (t instanceof SSLException || t instanceof ConnectException) {
        return true;
      }
      if (t instanceof IOException) {
        String message = t.getMessage();
        if (message != null) {
          String lower = message.toLowerCase(Locale.ROOT);
          if (lower.contains("connection reset")
              || lower.contains("broken pipe")
              || lower.contains("connection refused")
              || lower.contains("forcibly closed")
              || lower.contains("unexpected end of stream")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Replace the client after a connection/TLS failure so retries don't reuse bad state. If another
   * thread already renewed, {@link #httpClient} differs and we skip the duplicate. During a
   * sustained outage, {@link #CONNECTION_FAILURE_RENEWAL_COOLDOWN} also skips renewing again too
   * soon after the last connection-failure renewal, so every failed request doesn't spawn its own
   * shutdown thread.
   */
  private void renewHttpClientAfterConnectionFailure(Throwable cause, HttpClient culpritClient) {
    synchronized (this) {
      if (this.httpClient != culpritClient) {
        return;
      }
      if (System.currentTimeMillis() - this.lastConnectionFailureRenewalEpochMs
          < CONNECTION_FAILURE_RENEWAL_COOLDOWN.toMillis()) {
        return;
      }
      replaceHttpClientLocked(true, cause);
    }
  }

  private void replaceHttpClientLocked(boolean afterConnectionFailure, @Nullable Throwable cause) {
    HttpClient previous = this.httpClient;
    this.httpClient = newHttpClient();
    this.httpClientCreatedEpochMs = System.currentTimeMillis();
    if (afterConnectionFailure) {
      this.lastConnectionFailureRenewalEpochMs = this.httpClientCreatedEpochMs;
      LOG.warn(
          "Renewed OpenAI-compatible HttpClient for model {} after connection/TLS failure: {}",
          this.modelId,
          cause != null ? cause.toString() : "unknown");
    } else {
      LOG.debug(
          "Renewed OpenAI-compatible HttpClient for model {} after {} wall-clock interval",
          this.modelId,
          HTTP_CLIENT_REFRESH_INTERVAL);
    }
    new OneShotSingleThreadExecutor("openai-compat-http-client-shutdown-" + this.modelId)
        .execute(() -> shutdownReplacedHttpClient(previous));
  }

  /**
   * Shut down the replaced client to release its connection pools and threads (JDK 21+ lifecycle).
   */
  private void shutdownReplacedHttpClient(HttpClient previous) {
    if (previous == null) {
      return;
    }
    try {
      previous.shutdown();
      if (!previous.awaitTermination(HTTP_CLIENT_SHUTDOWN_AWAIT)) {
        LOG.warn(
            "Previous OpenAI-compatible HttpClient for model {} did not terminate within {};"
                + " forcing shutdownNow",
            this.modelId,
            HTTP_CLIENT_SHUTDOWN_AWAIT);
        previous.shutdownNow();
        if (!previous.awaitTermination(HTTP_CLIENT_SHUTDOWN_NOW_AWAIT)) {
          LOG.warn(
              "Previous OpenAI-compatible HttpClient for model {} still not terminated after"
                  + " shutdownNow",
              this.modelId);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn(
          "Interrupted while shutting down previous OpenAI-compatible HttpClient for model {}",
          this.modelId,
          e);
      previous.shutdownNow();
    }
  }

  @VisibleForTesting
  void renewHttpClientIfStaleForTesting() {
    renewHttpClientIfStale();
  }

  @VisibleForTesting
  void renewHttpClientAfterConnectionFailureForTesting(Throwable cause, HttpClient culpritClient) {
    renewHttpClientAfterConnectionFailure(cause, culpritClient);
  }

  // For test only.
  @VisibleForTesting
  static void injectHttpClient(OpenAiCompatClient target, HttpClient mockHttpClient) {
    HttpClient previous = target.httpClient;
    target.httpClient = mockHttpClient;
    target.httpClientCreatedEpochMs = System.currentTimeMillis();
    target.shutdownReplacedHttpClient(previous);
  }
}
