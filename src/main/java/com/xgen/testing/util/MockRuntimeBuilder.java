package com.xgen.testing.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.Runtime;
import java.util.Optional;

public class MockRuntimeBuilder {

  private Optional<Integer> numCpus = Optional.empty();
  private Optional<Bytes> maxHeapSize = Optional.empty();
  private Optional<Bytes> totalMemory = Optional.empty();

  public MockRuntimeBuilder withNumCpus(int numCpus) {
    this.numCpus = Optional.of(numCpus);
    return this;
  }

  public MockRuntimeBuilder withMaxHeapSize(Bytes maxHeapSize) {
    this.maxHeapSize = Optional.of(maxHeapSize);
    return this;
  }

  public MockRuntimeBuilder withTotalMemory(Bytes totalMemory) {
    this.totalMemory = Optional.of(totalMemory);
    return this;
  }

  public Runtime build() {
    Check.isPresent(this.numCpus, "numCpus");
    Check.isPresent(this.maxHeapSize, "maxHeapSize");
    Runtime runtime = mock(Runtime.class);
    when(runtime.getNumCpus()).thenReturn(this.numCpus.get());
    when(runtime.getMaxHeapSize()).thenReturn(this.maxHeapSize.get());
    this.totalMemory.ifPresent(
        memory -> when(runtime.getTotalMemoryBytes()).thenReturn(memory.toBytes()));
    return runtime;
  }

  public static Runtime buildDefault() {
    return new MockRuntimeBuilder().withNumCpus(1).withMaxHeapSize(Bytes.ofMebi(512)).build();
  }
}
