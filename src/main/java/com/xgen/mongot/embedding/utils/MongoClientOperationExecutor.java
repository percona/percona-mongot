package com.xgen.mongot.embedding.utils;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCursorNotFoundException;
import com.mongodb.MongoException;
import com.mongodb.MongoNodeIsRecoveringException;
import com.mongodb.MongoNotPrimaryException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.xgen.mongot.embedding.exceptions.MaterializedViewTransientException;
import com.xgen.mongot.metrics.MetricsFactory;
import com.xgen.mongot.util.mongodb.Errors;
import com.xgen.mongot.util.retry.ExponentialBackoffPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience class for executing MongoDB operations with a retry policy and basic latency and
 * success/failure metrics. Ideally to be only used with idempotent operations where we know the
 * operation is retryable. Also note that the retry policy only works for top level exceptions /
 * errors - bulk operations like bulkWrite which can produce partial failures need to be handled
 * appropriately by the caller.
 */
public class MongoClientOperationExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(MongoClientOperationExecutor.class);
  private final RetryPolicy<Object> retryPolicy;
  private final MetricsFactory metricsFactory;
  private final String requestLatencyMetricName;
  private final String failedRequestsMetricName;
  private final String successfulRequestsMetricName;
  private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> successCounterCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> failureCounterCache = new ConcurrentHashMap<>();
  private final Counter mongodDiskFullErrors;
  private final Counter mongodUserWritesBlockedErrors;
  private final Counter mongodSystemOverloadErrors;

  /**
   * Creates a new MongoClientOperationExecutor.
   *
   * @param metricsFactory the metrics factory to use for creating metrics.
   * @param resourceName the mongoDB resource name. This will be used as the prefix for all metrics.
   */
  public MongoClientOperationExecutor(MetricsFactory metricsFactory, String resourceName) {
    this.metricsFactory = metricsFactory;

    this.retryPolicy =
        ExponentialBackoffPolicy.builder()
            .initialDelay(Duration.ofMillis(500))
            .backoffFactor(2)
            .maxDelay(Duration.ofMillis(5000))
            .maxRetries(5)
            .jitter(0.1)
            .build()
            .applyParameters(
                new RetryPolicy<>()
                    .handleIf(MongoClientOperationExecutor::isRetryable)
                    .onRetry(
                        ex ->
                            LOG.warn(
                                "Operation failed. Attempt count {}",
                                ex.getAttemptCount(),
                                ex.getLastFailure())));
    this.requestLatencyMetricName = resourceName + ".requestLatency";
    this.failedRequestsMetricName = resourceName + ".failedRequests";
    this.successfulRequestsMetricName = resourceName + ".successfulRequests";
    this.mongodDiskFullErrors = metricsFactory.counter("mongodDiskFullErrors");
    this.mongodUserWritesBlockedErrors = metricsFactory.counter("mongodUserWritesBlockedErrors");
    this.mongodSystemOverloadErrors = metricsFactory.counter("mongodSystemOverloadErrors");
  }

  /**
   * Execute a MongoDB operation with retries and operation-specific metrics. Note that the retries
   * will be performed on the caller's thread so this is best suited for short-lived operations.
   *
   * @param operationName Name of the operation (e.g., "bulkWrite", "readCheckpoint")
   * @param operation The MongoDB operation to execute
   */
  public <T> T execute(String operationName, CheckedSupplier<T> operation) throws Exception {

    var metricTags = Tags.of("operation", operationName);
    Timer timer =
        this.timerCache.computeIfAbsent(
            operationName,
            k ->
                this.metricsFactory.timer(
                    this.requestLatencyMetricName, metricTags, 0.5, 0.75, 0.9, 0.99));
    Counter successCounter =
        this.successCounterCache.computeIfAbsent(
            operationName,
            k -> this.metricsFactory.counter(this.successfulRequestsMetricName, metricTags));
    Counter failureCounter =
        this.failureCounterCache.computeIfAbsent(
            operationName,
            k -> this.metricsFactory.counter(this.failedRequestsMetricName, metricTags));

    // The latency metric here includes retries and is recorded for both success and failure.
    Timer.Sample sample = Timer.start();
    try {
      T result = Failsafe.with(this.retryPolicy).get(operation);
      successCounter.increment();
      return result;

    } catch (Exception e) {
      // Detect transient server conditions across all operation types and callsites.
      // These are temporary (disk can be freed, auto-scaling kicks in, load subsides), so wrap
      // as transient so the upper retry loop can back off and retry.
      if (isDiskFull(e)) {
        this.mongodDiskFullErrors.increment();
        throw new MaterializedViewTransientException(
            e, MaterializedViewTransientException.Reason.EXCEEDED_DISK_LIMIT);
      }
      if (isUserWritesBlocked(e)) {
        this.mongodUserWritesBlockedErrors.increment();
        throw new MaterializedViewTransientException(
            e, MaterializedViewTransientException.Reason.USER_WRITES_BLOCKED);
      }
      if (isSystemOverloaded(e)) {
        this.mongodSystemOverloadErrors.increment();
        throw new MaterializedViewTransientException(
            e, MaterializedViewTransientException.Reason.SYSTEM_OVERLOADED);
      }
      if (e instanceof MongoBulkWriteException bulkEx) {
        boolean hasDiskFull =
            bulkEx.getWriteErrors().stream()
                .anyMatch(err -> err.getCode() == Errors.EXCEEDED_DISK_LIMIT.code);
        boolean hasUserWritesBlocked =
            bulkEx.getWriteErrors().stream()
                .anyMatch(err -> err.getCode() == Errors.USER_WRITES_BLOCKED.code);
        boolean hasSystemOverload =
            bulkEx.getWriteErrors().stream()
                .anyMatch(err -> Errors.SYSTEM_OVERLOADED_ERROR_CODES.contains(err.getCode()));
        if (hasDiskFull) {
          this.mongodDiskFullErrors.increment();
        }
        if (hasUserWritesBlocked) {
          this.mongodUserWritesBlockedErrors.increment();
        }
        if (hasSystemOverload) {
          this.mongodSystemOverloadErrors.increment();
        }
        if (hasDiskFull || hasUserWritesBlocked || hasSystemOverload) {
          // Priority: disk-full > writes-blocked > system-overloaded.
          // A bulk write can contain errors from multiple categories; we
          // pick the most specific reason for the metric tag.
          MaterializedViewTransientException.Reason reason =
              hasDiskFull
                  ? MaterializedViewTransientException.Reason.EXCEEDED_DISK_LIMIT
                  : hasUserWritesBlocked
                      ? MaterializedViewTransientException.Reason.USER_WRITES_BLOCKED
                      : MaterializedViewTransientException.Reason.SYSTEM_OVERLOADED;
          throw new MaterializedViewTransientException(e, reason);
        }
      }
      failureCounter.increment();
      throw e;

    } finally {
      sample.stop(timer);
    }
  }

  public void execute(String operationName, CheckedRunnable operation) throws Exception {
    execute(
        operationName,
        () -> {
          operation.run();
          return null;
        });
  }

  private static boolean isDiskFull(Throwable e) {
    return e instanceof MongoException me && me.getCode() == Errors.EXCEEDED_DISK_LIMIT.code;
  }

  private static boolean isUserWritesBlocked(Throwable e) {
    return e instanceof MongoException me && me.getCode() == Errors.USER_WRITES_BLOCKED.code;
  }

  private static boolean isSystemOverloaded(Throwable e) {
    return e instanceof MongoException me
        && Errors.SYSTEM_OVERLOADED_ERROR_CODES.contains(me.getCode());
  }

  private static boolean isRetryable(Throwable e) {
    if (e instanceof IllegalStateException) {
      // When MongoClient is closed during sync source update, it will throw IllegalStateException.
      // This is not an ideal exception type check as MongoClient throws IllegalStateException when
      // it is closed instead of MongoException.
      return true;
    }
    if (e instanceof MaterializedViewTransientException) {
      // MaterializedViewTransientException may be thrown when sync source is missing.
      return true;
    }
    if (!(e instanceof MongoException mongoException)) {
      return false;
    }
    if (mongoException instanceof MongoSocketException
        || mongoException instanceof MongoNotPrimaryException
        || mongoException instanceof MongoNodeIsRecoveringException
        || mongoException instanceof MongoCursorNotFoundException
        || mongoException instanceof MongoTimeoutException) {
      return true;
    }
    return Errors.RETRYABLE_ERROR_CODES.contains(mongoException.getCode());
  }
}
