package com.xgen.mongot.metrics.system;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class SystemMetricsInstrumentationTest {
  @Test
  public void testDoesNotUpdateBeforeStart() {
    var mockDiskMetrics = mock(DiskMetrics.class);
    var mockNetstatMetrics = mock(NetstatMetrics.class);
    var mockProcessMetrics = mock(ProcessMetrics.class);
    var mockMemoryMetrics = mock(MemoryMetrics.class);
    var mockCpuInfoMetrics = mock(CpuInfoMetrics.class);
    var mockNumaInfoMetrics = mock(NumaInfoMetrics.class);
    new SystemMetricsInstrumentation(
        new SimpleMeterRegistry(),
        mockDiskMetrics,
        mockNetstatMetrics,
        mockProcessMetrics,
        mockMemoryMetrics,
        mockCpuInfoMetrics,
        mockNumaInfoMetrics);

    verifyNoMoreInteractions(mockDiskMetrics);
    verifyNoMoreInteractions(mockNetstatMetrics);
  }

  @Test
  public void testStartAndStopUpdater() throws Exception {
    var mockDiskMetrics = mock(DiskMetrics.class);
    var mockNetstatMetrics = mock(NetstatMetrics.class);
    var mockProcessMetrics = mock(ProcessMetrics.class);
    var mockMemoryMetrics = mock(MemoryMetrics.class);
    var mockCpuInfoMetrics = mock(CpuInfoMetrics.class);
    var mockNumaInfoMetrics = mock(NumaInfoMetrics.class);
    var systemMetricsInstrumentation =
        new SystemMetricsInstrumentation(
            new SimpleMeterRegistry(),
            mockDiskMetrics,
            mockNetstatMetrics,
            mockProcessMetrics,
            mockMemoryMetrics,
            mockCpuInfoMetrics,
            mockNumaInfoMetrics);
    systemMetricsInstrumentation.start(10, TimeUnit.MILLISECONDS);
    // Metrics are updated at least 10 times in one second.
    verify(mockDiskMetrics, timeout(1000).atLeast(10)).update();
    verify(mockNetstatMetrics, timeout(1000).atLeast(10)).update();
    verify(mockProcessMetrics, timeout(1000).atLeast(10)).update();

    systemMetricsInstrumentation.stop();
    clearInvocations(mockDiskMetrics);
    clearInvocations(mockNetstatMetrics);
    clearInvocations(mockProcessMetrics);
    // should not be updating after executor has stopped
    Thread.sleep(100);
    verifyNoInteractions(mockDiskMetrics);
    verifyNoInteractions(mockNetstatMetrics);
    verifyNoInteractions(mockProcessMetrics);
  }
}
