package com.xgen.mongot.util.retry;

import com.xgen.mongot.util.Check;
import java.time.Duration;
import java.util.Optional;
import net.jodah.failsafe.RetryPolicy;

/** A backoff policy that uses a fixed delay per retry (along with some jitter). */
public class FixedBackoffPolicy implements BackoffPolicy {

  /** How long to wait after an attempt is unsuccessful before trying again. */
  public final Duration delay;

  /** The maximum number of times to retry before giving up. */
  public final Optional<Integer> maxRetries;

  /**
   * Amount of jitter (as a fraction) between retries, to reduce correlation. 0 indicates no jitter
   */
  public final double jitter;

  private FixedBackoffPolicy(Duration delay, Optional<Integer> maxRetries, double jitter) {
    Check.checkState(!delay.isNegative(), "delay cannot be negative");
    maxRetries.ifPresent(r -> Check.argNotNegative(r, "maxRetries"));
    Check.checkState(jitter >= 0.0 && jitter <= 1.0, "jitterFactor must be >= " + "0 and <= 1");

    this.delay = delay;
    this.maxRetries = maxRetries;
    this.jitter = jitter;
  }

  public static BackoffPolicyDelayBuilder builder() {
    return new BackoffPolicyDelayBuilder();
  }

  @Override
  public <T> RetryPolicy<T> applyParameters(RetryPolicy<T> retryPolicy) {
    this.maxRetries.ifPresent(retryPolicy::withMaxRetries);
    return retryPolicy.withDelay(this.delay).withJitter(this.jitter);
  }

  @Override
  public Duration delayFor(int consecutiveFailures) {
    if (consecutiveFailures <= 0) {
      return Duration.ZERO;
    }
    return applyJitter(this.delay, this.jitter);
  }

  public static class BackoffPolicyDelayBuilder {
    public BackoffPolicyBuilder delay(Duration delay) {
      return new BackoffPolicyBuilder(delay);
    }
  }

  public static class BackoffPolicyBuilder {

    public final double jitter;
    private final Duration delay;
    private final Optional<Integer> maxRetries;

    private BackoffPolicyBuilder(Duration delay, Optional<Integer> maxRetries, double jitter) {
      this.delay = delay;

      this.maxRetries = maxRetries;
      this.jitter = jitter;
    }

    private BackoffPolicyBuilder(Duration delay) {
      this(delay, Optional.empty(), 0);
    }

    public BackoffPolicyBuilder maxRetries(int maxRetries) {
      return new BackoffPolicyBuilder(this.delay, Optional.of(maxRetries), this.jitter);
    }

    public BackoffPolicyBuilder jitter(double jitter) {
      return new BackoffPolicyBuilder(this.delay, this.maxRetries, jitter);
    }

    public FixedBackoffPolicy build() {
      return new FixedBackoffPolicy(this.delay, this.maxRetries, this.jitter);
    }
  }
}
