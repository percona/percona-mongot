package com.xgen.mongot.metrics.system;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;

// TODO(CLOUDP-285787): investigate moving metrics (especially gauges) to MetricsFactory
public class CpuInfoMetrics {
  private final ProcessorIdentifier processorIdentifier;

  CpuInfoMetrics(ProcessorIdentifier processorIdentifier) {
    this.processorIdentifier = processorIdentifier;
  }

  static CpuInfoMetrics create(SystemInfo systemInfo, MeterRegistry meterRegistry) {
    return create(systemInfo.getHardware().getProcessor(), meterRegistry);
  }

  @VisibleForTesting
  static CpuInfoMetrics create(CentralProcessor processor, MeterRegistry meterRegistry) {
    ProcessorIdentifier id = processor.getProcessorIdentifier();
    CpuInfoMetrics cpuInfoMetrics = new CpuInfoMetrics(id);

    Gauge.builder("system.cpu.info", cpuInfoMetrics.processorIdentifier, p -> 1.0)
        .description("CPU identification information")
        .tag("vendor", emptyToUnknown(id.getVendor()))
        .tag("name", emptyToUnknown(id.getName()))
        .tag("microarchitecture", emptyToUnknown(id.getMicroarchitecture()))
        .tag("architecture", System.getProperty("os.arch", "unknown"))
        .register(meterRegistry);

    return cpuInfoMetrics;
  }

  private static String emptyToUnknown(String value) {
    return Optional.ofNullable(value).filter(s -> !s.isBlank()).orElse("unknown");
  }
}
