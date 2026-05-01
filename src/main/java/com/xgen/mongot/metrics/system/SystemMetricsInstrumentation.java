package com.xgen.mongot.metrics.system;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedScheduledExecutorService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;

public class SystemMetricsInstrumentation {
  private final MeterRegistry meterRegistry;
  private final DiskMetrics diskMetrics;
  private final NetstatMetrics netstatMetrics;
  private final ProcessMetrics processMetrics;
  private final MemoryMetrics memoryMetrics;

  @SuppressWarnings("unused") // held to prevent GC of the gauge's referenced object
  private final CpuInfoMetrics cpuInfoMetrics;

  @SuppressWarnings("unused") // held to prevent GC of the gauge's referenced object
  private final NumaInfoMetrics numaInfoMetrics;

  private final NamedScheduledExecutorService updater;
  private static final Logger LOG = LoggerFactory.getLogger(SystemMetricsInstrumentation.class);

  /** Instrument system metrics and bind them to the input registry. */
  public static SystemMetricsInstrumentation create(MeterRegistry meterRegistry, Path dataPath) {
    SystemInfo systemInfo = new SystemInfo();
    return new SystemMetricsInstrumentation(
        meterRegistry,
        DiskMetrics.create(systemInfo, meterRegistry, dataPath),
        NetstatMetrics.create(systemInfo, meterRegistry),
        ProcessMetrics.create(systemInfo, meterRegistry),
        MemoryMetrics.create(systemInfo, meterRegistry),
        CpuInfoMetrics.create(systemInfo, meterRegistry),
        NumaInfoMetrics.create(systemInfo, meterRegistry));
  }

  @VisibleForTesting
  SystemMetricsInstrumentation(
      MeterRegistry meterRegistry,
      DiskMetrics diskMetrics,
      NetstatMetrics netstatMetrics,
      ProcessMetrics processMetrics,
      MemoryMetrics memoryMetrics,
      CpuInfoMetrics cpuInfoMetrics,
      NumaInfoMetrics numaInfoMetrics) {
    this.meterRegistry = meterRegistry;
    this.diskMetrics = diskMetrics;
    this.netstatMetrics = netstatMetrics;
    this.processMetrics = processMetrics;
    this.memoryMetrics = memoryMetrics;
    this.cpuInfoMetrics = cpuInfoMetrics;
    this.numaInfoMetrics = numaInfoMetrics;
    this.updater =
        Executors.singleThreadScheduledExecutor("system-metrics-updater", this.meterRegistry);
  }

  /** Start updating system metrics at a fixed delay. */
  public void start(long delay, TimeUnit unit) {
    LOG.atInfo()
        .addKeyValue("interval", delay)
        .addKeyValue("timeUnit", unit.name().toLowerCase())
        .log("Starting periodic system metrics update");
    this.updater.scheduleWithFixedDelay(
        () -> {
          this.diskMetrics.update();
          this.netstatMetrics.update();
          this.processMetrics.update();
          this.memoryMetrics.update();
        },
        delay,
        delay,
        unit);
  }

  /** Stops periodic scheduling. */
  public void stop() {
    Executors.shutdownOrFail(this.updater);
  }
}
