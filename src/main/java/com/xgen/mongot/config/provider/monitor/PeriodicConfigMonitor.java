package com.xgen.mongot.config.provider.monitor;

import com.xgen.mongot.config.updater.ConfigUpdater;
import com.xgen.mongot.config.updater.RetriableConfigUpdateException;
import com.xgen.mongot.util.Crash;
import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedScheduledExecutorService;
import com.xgen.mongot.util.retry.BackoffPolicy;
import com.xgen.mongot.util.retry.ExponentialBackoffPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically invokes a {@link ConfigUpdater} on a dedicated single-thread scheduler.
 *
 * <p>Each {@code update()} call corresponds to one scheduled attempt. After a successful update the
 * next attempt is scheduled {@code period} later; after a {@link RetriableConfigUpdateException}
 * the next attempt is delayed by an exponential backoff that scales with {@code period}. Any other
 * exception or error is treated as unrecoverable and crashes the process via {@link Crash}.
 */
public class PeriodicConfigMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(PeriodicConfigMonitor.class);

  // Cap the backoff at 5 minutes or 30x the period, whichever is shorter, so recovery occurs
  // within a reasonable time during sustained outages but still reduces load.
  private static final int MAX_DELAY_PERIOD_MULTIPLIER = 30;
  private static final Duration MAX_DELAY_PERIOD = Duration.ofMinutes(5);

  private final NamedScheduledExecutorService executorService;
  private final ConfigUpdater configUpdater;
  private final Duration period;
  private final BackoffPolicy backoffPolicy;

  // Mutated only from the single-threaded executor thread, so it does not need synchronization.
  private int consecutiveFailures;

  private PeriodicConfigMonitor(
      NamedScheduledExecutorService executorService,
      ConfigUpdater configUpdater,
      Duration period,
      BackoffPolicy backoffPolicy) {
    this.executorService = executorService;
    this.configUpdater = configUpdater;
    this.period = period;
    this.backoffPolicy = backoffPolicy;
    this.consecutiveFailures = 0;
  }

  public static PeriodicConfigMonitor create(
      ConfigUpdater configUpdater, Duration period, MeterRegistry meterRegistry) {
    return new PeriodicConfigMonitor(
        Executors.singleThreadScheduledExecutor(
            "config-monitor", Thread.MAX_PRIORITY, meterRegistry),
        configUpdater,
        period,
        defaultBackoffPolicy(period));
  }

  private static BackoffPolicy defaultBackoffPolicy(Duration period) {
    Duration scaledMaxDelay = period.multipliedBy(MAX_DELAY_PERIOD_MULTIPLIER);
    Duration maxDelay =
        MAX_DELAY_PERIOD.compareTo(scaledMaxDelay) <= 0 ? MAX_DELAY_PERIOD : scaledMaxDelay;
    return ExponentialBackoffPolicy.builder()
        .initialDelay(period)
        .backoffFactor(2)
        .maxDelay(maxDelay)
        .jitter(0.1)
        .build();
  }

  /** Initializes the ConfigUpdater and begins the periodic calling of ConfigUpdater::update. */
  public void start() {
    LOG.info("Beginning periodic config monitoring");
    scheduleNext(Duration.ZERO);
  }

  /** Ceases the periodic calling of ConfigUpdater::update and closes the ConfigUpdater. */
  public void stop() {
    LOG.info("Stopping periodic config monitoring.");

    // singleThreadScheduledExecutor should not fail to shutdown even if there are scheduled tasks.
    Executors.shutdownOrFail(this.executorService);
    this.configUpdater.close();
  }

  private void scheduleNext(Duration delay) {
    try {
      this.executorService.schedule(
          this::runAndScheduleNext, delay.toMillis(), TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      LOG.info("Config monitor executor is shut down; abandoning rescheduling.");
    }
  }

  private void runAndScheduleNext() {
    try {
      this.configUpdater.update();
      this.consecutiveFailures = 0;
    } catch (RetriableConfigUpdateException e) {
      this.consecutiveFailures++;
    } catch (Throwable t) {
      Crash.because("failed to update config").withThrowable(t).now();
      return;
    }

    if (this.consecutiveFailures > 0) {
      Duration nextDelay = this.backoffPolicy.delayFor(this.consecutiveFailures);
      LOG.atInfo()
          .addKeyValue("consecutiveFailures", this.consecutiveFailures)
          .addKeyValue("nextAttemptInMs", nextDelay.toMillis())
          .log("Conf call failed; scheduling next attempt after backoff");
      scheduleNext(nextDelay);
    } else {
      scheduleNext(this.period);
    }
  }
}
