package com.xgen.mongot.metrics.system;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.LogicalProcessor;

public class NumaInfoMetrics {
  private static final Path PROC_SELF_STATUS = Path.of("/proc/self/status");
  private static final Path NUMA_BALANCING = Path.of("/proc/sys/kernel/numa_balancing");
  private static final String UNKNOWN = "unknown";

  private final Object holder = new Object();

  NumaInfoMetrics() {}

  static NumaInfoMetrics create(SystemInfo systemInfo, MeterRegistry meterRegistry) {
    return create(
        systemInfo.getHardware().getProcessor(),
        meterRegistry,
        readMemsAllowedList(PROC_SELF_STATUS),
        readNumaBalancing(NUMA_BALANCING));
  }

  @VisibleForTesting
  static NumaInfoMetrics create(
      CentralProcessor processor,
      MeterRegistry meterRegistry,
      String allowedNodes,
      String numaBalancing) {
    long nodes =
        processor.getLogicalProcessors().stream()
            .map(LogicalProcessor::getNumaNode)
            .distinct()
            .count();
    int sockets = Math.max(processor.getPhysicalPackageCount(), 0);

    NumaInfoMetrics metrics = new NumaInfoMetrics();
    Gauge.builder("system.numa.info", metrics.holder, h -> 1.0)
        .description("NUMA topology and process NUMA configuration")
        .tag("nodes", String.valueOf(Math.max(nodes, 1)))
        .tag("sockets", String.valueOf(Math.max(sockets, 1)))
        .tag("allowed_nodes", allowedNodes)
        .tag("numa_balancing", numaBalancing)
        .register(meterRegistry);
    return metrics;
  }

  @VisibleForTesting
  static String readMemsAllowedList(Path procSelfStatus) {
    if (!Files.isReadable(procSelfStatus)) {
      return UNKNOWN;
    }
    try (Stream<String> lines = Files.lines(procSelfStatus)) {
      return lines
          .filter(l -> l.startsWith("Mems_allowed_list:"))
          .map(l -> l.substring("Mems_allowed_list:".length()).trim())
          .findFirst()
          .filter(s -> !s.isEmpty())
          .orElse(UNKNOWN);
    } catch (IOException e) {
      return UNKNOWN;
    }
  }

  @VisibleForTesting
  static String readNumaBalancing(Path numaBalancing) {
    if (!Files.isReadable(numaBalancing)) {
      return UNKNOWN;
    }
    try {
      String s = Files.readString(numaBalancing).trim();
      return s.isEmpty() ? UNKNOWN : s;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
