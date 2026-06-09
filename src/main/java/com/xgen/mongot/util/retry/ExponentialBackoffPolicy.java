package com.xgen.mongot.util.retry;

import com.xgen.mongot.util.Check;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import net.jodah.failsafe.RetryPolicy;

/** A backoff policy that uses exponential backoff for retries. */
public class ExponentialBackoffPolicy implements BackoffPolicy {

  /** How long to wait after a first attempt is unsuccessful before trying again. */
  public final Duration initialDelay;

  /** How much to backoff from the last delay between attempts before retrying again. */
  public final int backoffFactor;

  /**
   * The maximum amount of delay between retries you should back off to before not increasing the
   * retry duration any more.
   */
  public final Duration maxDelay;

  /** The maximum number of times to retry before giving up. */
  public final Optional<Integer> maxRetries;

  /**
   * Amount of jitter (as a fraction) between retries, to reduce correlation. 0 indicates no jitter
   */
  public final double jitter;

  private ExponentialBackoffPolicy(
      Duration initialDelay,
      int backoffFactor,
      Duration maxDelay,
      Optional<Integer> maxRetries,
      double jitter) {
    Check.checkState(!initialDelay.isNegative(), "initialDelay cannot be negative");
    Check.argIsPositive(backoffFactor, "backoffFactor");
    Check.checkState(!maxDelay.isNegative(), "maxDelay cannot be negative");
    maxRetries.ifPresent(r -> Check.argNotNegative(r, "maxRetries"));
    Check.checkState(jitter >= 0.0 && jitter <= 1.0, "jitterFactor must be >= " + "0 and <= 1");

    this.initialDelay = initialDelay;
    this.maxDelay = maxDelay;
    this.backoffFactor = backoffFactor;
    this.maxRetries = maxRetries;
    this.jitter = jitter;
  }

  public static BackoffPolicyInitialDelayBuilder builder() {
    return new BackoffPolicyInitialDelayBuilder();
  }

  @Override
  public <T> RetryPolicy<T> applyParameters(RetryPolicy<T> retryPolicy) {
    this.maxRetries.ifPresent(retryPolicy::withMaxRetries);
    return retryPolicy
        .withBackoff(
            this.initialDelay.toMillis(),
            this.maxDelay.toMillis(),
            ChronoUnit.MILLIS,
            this.backoffFactor)
        .withJitter(this.jitter);
  }

  @Override
  public Duration delayFor(int consecutiveFailures) {
    if (consecutiveFailures <= 0) {
      return Duration.ZERO;
    }
    long initialNanos = this.initialDelay.toNanos();
    long maxNanos = this.maxDelay.toNanos();
    double scaled = initialNanos * Math.pow(this.backoffFactor, consecutiveFailures - 1.0);
    long delayNanos;
    if (Double.isInfinite(scaled) || scaled >= maxNanos) {
      delayNanos = maxNanos;
    } else {
      delayNanos = Math.min(maxNanos, (long) scaled);
    }
    return applyJitter(Duration.ofNanos(delayNanos), this.jitter);
  }

  public static class BackoffPolicyInitialDelayBuilder {
    public BackoffPolicyBackoffFactorBuilder initialDelay(Duration initialDelay) {
      return new BackoffPolicyBackoffFactorBuilder(initialDelay);
    }
  }

  public static class BackoffPolicyBackoffFactorBuilder {

    private final Duration initialDelay;

    private BackoffPolicyBackoffFactorBuilder(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }

    public BackoffPolicyMaxDelayBuilder backoffFactor(int backoffFactor) {
      return new BackoffPolicyMaxDelayBuilder(this.initialDelay, backoffFactor);
    }
  }

  public static class BackoffPolicyMaxDelayBuilder {

    private final Duration initialDelay;
    private final int backoffFactor;

    private BackoffPolicyMaxDelayBuilder(Duration initialDelay, int backoffFactor) {
      this.initialDelay = initialDelay;
      this.backoffFactor = backoffFactor;
    }

    public BackoffPolicyBuilder maxDelay(Duration maxDelay) {
      return new BackoffPolicyBuilder(this.initialDelay, this.backoffFactor, maxDelay);
    }
  }

  public static class BackoffPolicyBuilder {

    public final double jitter;
    private final Duration initialDelay;
    private final int backoffFactor;
    private final Duration maxDelay;
    private final Optional<Integer> maxRetries;

    private BackoffPolicyBuilder(
        Duration initialDelay,
        int backoffFactor,
        Duration maxDelay,
        Optional<Integer> maxRetries,
        double jitter) {
      this.initialDelay = initialDelay;
      this.backoffFactor = backoffFactor;
      this.maxDelay = maxDelay;
      this.maxRetries = maxRetries;
      this.jitter = jitter;
    }

    private BackoffPolicyBuilder(Duration initialDelay, int backoffFactor, Duration maxDelay) {
      this(initialDelay, backoffFactor, maxDelay, Optional.empty(), 0);
    }

    public BackoffPolicyBuilder maxRetries(int maxRetries) {
      return new BackoffPolicyBuilder(
          this.initialDelay,
          this.backoffFactor,
          this.maxDelay,
          Optional.of(maxRetries),
          this.jitter);
    }

    public BackoffPolicyBuilder jitter(double jitter) {
      return new BackoffPolicyBuilder(
          this.initialDelay, this.backoffFactor, this.maxDelay, this.maxRetries, jitter);
    }

    public ExponentialBackoffPolicy build() {
      return new ExponentialBackoffPolicy(
          this.initialDelay, this.backoffFactor, this.maxDelay, this.maxRetries, this.jitter);
    }
  }
}
