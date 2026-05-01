package com.xgen.mongot.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.sun.management.ThreadMXBean;
import com.xgen.mongot.util.concurrent.NamedExecutorService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-pool ThreadMXBean attribution metrics. Sums heap allocation and CPU time across every thread
 * the pool has ever created, exposing the totals as Micrometer counters tagged with the pool name.
 *
 * <p>Used to attribute heap allocation rate and CPU consumption to a specific workload
 * ({@code blocking-server-worker} vs {@code guaranteed-blocking-server-worker} vs
 * {@code concurrent-search} for query workloads, and similarly for replication / merges).
 *
 * <p>Per-thread allocation tracking is a JVM-wide concept that does not include direct
 * {@link java.nio.ByteBuffer} or native (JNI) allocations. It only counts objects allocated into
 * the Java heap.
 */
public final class ThreadPoolResourceMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(ThreadPoolResourceMetrics.class);
  private static final FluentLogger FLOGGER = FluentLogger.forEnclosingClass();

  private static final String ALLOCATED_BYTES_METRIC = "executor.thread.allocatedBytes";
  private static final String CPU_TIME_METRIC = "executor.thread.cpuTime";
  private static final String NAME_TAG = "name";
  private static final String SUBSYSTEM_TAG = "subsystem";

  private final Optional<ThreadMXBean> threadMxBean;

  private final String subsystem;

  private ThreadPoolResourceMetrics(Optional<ThreadMXBean> threadMxBean, String subsystem) {
    this.threadMxBean = threadMxBean;
    this.subsystem = subsystem;
  }

  /**
   * Creates an instance backed by the platform {@link java.lang.management.ThreadMXBean}, scoped
   * by {@code subsystem}. The subsystem is emitted as a meter tag, so two callers using distinct
   * subsystems can register pools that share a name without colliding in the meter registry.
   */
  public static ThreadPoolResourceMetrics create(String subsystem) {
    Optional<ThreadMXBean> mxBean =
        (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean s)
            ? Optional.of(s)
            : Optional.empty();

    if (mxBean.isPresent()
        && mxBean.get().isThreadAllocatedMemorySupported()
        && mxBean.get().isThreadCpuTimeSupported()) {
      try {
        // Enable both tracking knobs explicitly (though HotSpot enables them by default).
        mxBean.get().setThreadAllocatedMemoryEnabled(true);
        mxBean.get().setThreadCpuTimeEnabled(true);
        return new ThreadPoolResourceMetrics(mxBean, subsystem);
      } catch (UnsupportedOperationException e) {
        LOG.warn("Per-thread allocation/CPU tracking unsupported on this JVM, counters will be 0.");
      }
    } else {
      LOG.warn(
          "Per-thread allocation or CPU tracking is unavailable on this JVM, counters will be 0.");
    }

    return new ThreadPoolResourceMetrics(Optional.empty(), subsystem);
  }

  /**
   * Registers per-pool allocation and CPU-time counters for {@code executor}.
   */
  public void register(NamedExecutorService executor, MeterRegistry meterRegistry) {
    String poolName = executor.getName();

    FunctionCounter.builder(ALLOCATED_BYTES_METRIC, executor, this::allocatedBytes)
        .description(
            "Cumulative bytes allocated into the Java heap by all threads this pool has ever"
                + " created. Excludes direct ByteBuffers and native allocations.")
        .baseUnit("bytes")
        .tag(SUBSYSTEM_TAG, this.subsystem)
        .tag(NAME_TAG, poolName)
        .register(meterRegistry);

    FunctionCounter.builder(CPU_TIME_METRIC, executor, this::cpuTimeNanos)
        .description(
            "Cumulative on-CPU nanoseconds across all threads this pool has ever created.")
        .baseUnit("nanoseconds")
        .tag(SUBSYSTEM_TAG, this.subsystem)
        .tag(NAME_TAG, poolName)
        .register(meterRegistry);
  }

  @VisibleForTesting
  double allocatedBytes(NamedExecutorService executor) {
    if (this.threadMxBean.isEmpty()) {
      return 0.0;
    }
    return sumPerThread(executor, this.threadMxBean.get()::getThreadAllocatedBytes);
  }

  @VisibleForTesting
  double cpuTimeNanos(NamedExecutorService executor) {
    if (this.threadMxBean.isEmpty()) {
      return 0.0;
    }
    return sumPerThread(executor, this.threadMxBean.get()::getThreadCpuTime);
  }

  /**
   * Sums the MXBean data across the executor's threads and prunes any thread ids the JVM reports
   * as dead.
   */
  private double sumPerThread(
      NamedExecutorService executor, Function<long[], long[]> beanReader) {
    Optional<Collection<Long>> maybeLiveIds = executor.getMutableThreadIds();
    if (maybeLiveIds.isEmpty()) {
      logUnsupportedExecutor(executor);
      return 0.0;
    }
    Collection<Long> liveIds = maybeLiveIds.get();
    long[] ids = liveIds.stream().mapToLong(Long::longValue).toArray();
    if (ids.length == 0) {
      return 0.0;
    }
    long[] beanValues;
    try {
      beanValues = beanReader.apply(ids);
    } catch (UnsupportedOperationException e) {
      return 0.0;
    }
    // -1 means the thread has terminated, prune it so the set stays bounded.
    IntStream.range(0, ids.length)
        .filter(i -> beanValues[i] < 0)
        .forEach(i -> liveIds.remove(ids[i]));
    return Arrays.stream(beanValues).filter(v -> v >= 0).sum();
  }

  private static void logUnsupportedExecutor(NamedExecutorService executor) {
    FLOGGER
        .atWarning()
        .atMostEvery(1, TimeUnit.HOURS)
        .log(
            "Per-thread attribution unavailable for executor '%s', counter will report 0.",
            executor.getName());
  }

}
