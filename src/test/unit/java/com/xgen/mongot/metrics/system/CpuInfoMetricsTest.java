package com.xgen.mongot.metrics.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.ProcessorIdentifier;

public class CpuInfoMetricsTest {
  // ProcessorIdentifier(vendor, name, family, model, stepping, processorID, cpu64bit)
  private static CentralProcessor processorWith(ProcessorIdentifier id) {
    var processor = mock(CentralProcessor.class);
    when(processor.getProcessorIdentifier()).thenReturn(id);
    return processor;
  }

  @Test
  public void create_registersGaugeWithValueOne() {
    var meterRegistry = new SimpleMeterRegistry();
    var id =
        new ProcessorIdentifier(
            "GenuineIntel", "Intel(R) Xeon(R) Platinum 8488C", "6", "143", "1", "someId", true);

    CpuInfoMetrics.create(processorWith(id), meterRegistry);

    Gauge gauge = meterRegistry.get("system.cpu.info").gauge();
    assertNotNull(gauge);
    assertEquals(1.0, gauge.value(), 0.0);
  }

  @Test
  public void create_tagsReflectProcessorIdentifier() {
    var meterRegistry = new SimpleMeterRegistry();
    var id =
        new ProcessorIdentifier(
            "GenuineIntel", "Intel(R) Xeon(R) Platinum 8488C", "6", "143", "1", "someId", true);

    CpuInfoMetrics.create(processorWith(id), meterRegistry);

    var meterId = meterRegistry.get("system.cpu.info").gauge().getId();
    assertEquals("GenuineIntel", meterId.getTag("vendor"));
    assertEquals("Intel(R) Xeon(R) Platinum 8488C", meterId.getTag("name"));
    // microarchitecture is OSHI-derived; just verify it's present
    assertNotNull(meterId.getTag("microarchitecture"));
    assertNotNull(meterId.getTag("architecture"));
  }

  @Test
  public void create_emptyFields_fallBackToUnknown() {
    var meterRegistry = new SimpleMeterRegistry();
    // All-empty ProcessorIdentifier — vendor, name, and getMicroarchitecture() all return ""
    var id = new ProcessorIdentifier("", "", "", "", "", "", true);

    CpuInfoMetrics.create(processorWith(id), meterRegistry);

    var meterId = meterRegistry.get("system.cpu.info").gauge().getId();
    assertEquals("unknown", meterId.getTag("vendor"));
    assertEquals("unknown", meterId.getTag("name"));
    assertEquals("unknown", meterId.getTag("microarchitecture"));
  }

  @Test
  public void create_architectureTagMatchesSystemProperty() {
    var meterRegistry = new SimpleMeterRegistry();
    var id = new ProcessorIdentifier("ARM", "Neoverse-V1", "8", "0xd40", "0x1", "someId", true);

    CpuInfoMetrics.create(processorWith(id), meterRegistry);

    var meterId = meterRegistry.get("system.cpu.info").gauge().getId();
    assertEquals(System.getProperty("os.arch", "unknown"), meterId.getTag("architecture"));
  }
}
