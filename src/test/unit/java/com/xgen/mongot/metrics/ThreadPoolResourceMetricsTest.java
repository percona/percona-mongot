package com.xgen.mongot.metrics;

import static com.google.common.truth.Truth.assertThat;

import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedExecutorService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.Test;

public class ThreadPoolResourceMetricsTest {

  private static final String SUBSYSTEM = "test";
  private static final String POOL_NAME = "tp-resource-test";
  private static final String ALLOCATED_BYTES = "executor.thread.allocatedBytes";
  private static final String CPU_TIME = "executor.thread.cpuTime";

  @Test
  public void register_addsBothCountersTaggedWithSubsystemAndPoolName() {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService executor = Executors.fixedSizeThreadPool(POOL_NAME, 1, registry)) {
      ThreadPoolResourceMetrics.create(SUBSYSTEM).register(executor, registry);

      FunctionCounter allocated =
          registry
              .find(ALLOCATED_BYTES)
              .tag("subsystem", SUBSYSTEM)
              .tag("name", POOL_NAME)
              .functionCounter();
      FunctionCounter cpu =
          registry
              .find(CPU_TIME)
              .tag("subsystem", SUBSYSTEM)
              .tag("name", POOL_NAME)
              .functionCounter();

      assertThat(allocated).isNotNull();
      assertThat(cpu).isNotNull();
    }
  }

  @Test
  public void allocatedBytes_prunesDeadThreadIdsFromMutableSet() throws Exception {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService executor = Executors.fixedSizeThreadPool(POOL_NAME, 1, registry)) {
      // Make sure the real worker is alive so its id stays in the set.
      var ready = new CountDownLatch(1);
      executor.submit(ready::countDown);
      ready.await(1, TimeUnit.MINUTES);

      var liveIds = executor.getMutableThreadIds().orElseThrow();
      // Inject a synthetic id the JVM cannot match, so getThreadAllocatedBytes returns -1 for it.
      long fakeDeadId = Long.MAX_VALUE - 1;
      liveIds.add(fakeDeadId);
      assertThat(liveIds).contains(fakeDeadId);

      var metrics = ThreadPoolResourceMetrics.create(SUBSYSTEM);
      metrics.allocatedBytes(executor); // triggers the prune

      assertThat(liveIds).doesNotContain(fakeDeadId);
    }
  }

  @Test
  public void cpuTimeNanos_prunesDeadThreadIdsFromMutableSet() throws Exception {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService executor = Executors.fixedSizeThreadPool(POOL_NAME, 1, registry)) {
      var ready = new CountDownLatch(1);
      executor.submit(ready::countDown);
      ready.await(1, TimeUnit.MINUTES);

      var liveIds = executor.getMutableThreadIds().orElseThrow();
      long fakeDeadId = Long.MAX_VALUE - 1;
      liveIds.add(fakeDeadId);
      assertThat(liveIds).contains(fakeDeadId);

      var metrics = ThreadPoolResourceMetrics.create(SUBSYSTEM);
      metrics.cpuTimeNanos(executor);

      assertThat(liveIds).doesNotContain(fakeDeadId);
    }
  }

  @Test
  public void register_sameNameAcrossSubsystems_doesNotCollide() {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService poolA = Executors.fixedSizeThreadPool(POOL_NAME, 1, registry);
        NamedExecutorService poolB = Executors.fixedSizeThreadPool(POOL_NAME + "-b", 1, registry)) {
      ThreadPoolResourceMetrics.create("query").register(poolA, registry);
      ThreadPoolResourceMetrics.create("replication").register(poolB, registry);

      assertThat(registry.find(ALLOCATED_BYTES).tag("subsystem", "query").functionCounter())
          .isNotNull();
      assertThat(registry.find(ALLOCATED_BYTES).tag("subsystem", "replication").functionCounter())
          .isNotNull();
    }
  }

  @Test
  public void allocatedBytes_increasesAfterPoolAllocates() throws Exception {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService executor = Executors.fixedSizeThreadPool(POOL_NAME, 2, registry)) {
      var metrics = ThreadPoolResourceMetrics.create(SUBSYSTEM);
      metrics.register(executor, registry);

      // Touch threads first so the pool actually creates them and the counter has IDs to read.
      var ready = new CountDownLatch(2);
      executor.submit(ready::countDown);
      executor.submit(ready::countDown);
      ready.await(1, TimeUnit.MINUTES);

      double before = metrics.allocatedBytes(executor);

      // Allocate ~1 MB on a pool thread, the precise byte count is JVM-internal but must increase.
      executor
          .submit(
              () -> {
                byte[] junk = new byte[1024 * 1024];
                junk[0] = 1;
              })
          .get(1, TimeUnit.MINUTES);

      double after = metrics.allocatedBytes(executor);

      // On JVMs without thread allocation tracking the counter is constant 0, skip the
      // monotonicity assertion in that case rather than flaking.
      if (before != 0.0 || after != 0.0) {
        assertThat(after).isGreaterThan(before);
      }
    }
  }

  @Test
  public void cpuTime_increasesAfterPoolBurnsCpu() throws Exception {
    var registry = new SimpleMeterRegistry();
    try (NamedExecutorService executor = Executors.fixedSizeThreadPool(POOL_NAME, 1, registry)) {
      var metrics = ThreadPoolResourceMetrics.create(SUBSYSTEM);
      metrics.register(executor, registry);

      var ready = new CountDownLatch(1);
      executor.submit(ready::countDown);
      ready.await(1, TimeUnit.MINUTES);

      double before = metrics.cpuTimeNanos(executor);

      executor.submit(() -> LongStream.range(0, 5_000_000L).sum()).get(1, TimeUnit.MINUTES);

      double after = metrics.cpuTimeNanos(executor);

      if (before != 0.0 || after != 0.0) {
        assertThat(after).isGreaterThan(before);
      }
    }
  }
}
