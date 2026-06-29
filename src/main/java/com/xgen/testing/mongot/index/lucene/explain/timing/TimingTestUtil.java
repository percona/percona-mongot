package com.xgen.testing.mongot.index.lucene.explain.timing;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.base.Ticker;
import com.google.common.truth.Truth;
import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import com.xgen.mongot.util.timers.ThreadSafeInvocationCountingTimer;
import java.util.Random;

public class TimingTestUtil {
  public static final String RESOURCES_PATH =
      "src/test/unit/resources/index/lucene/explain/timing/";

  private static final Random RANDOM = new Random();

  public static ExplainTimings randomTimings() {
    ThreadSafeInvocationCountingTimer mockedTimer =
        spy(new ThreadSafeInvocationCountingTimer(Ticker.systemTicker()));
    when(mockedTimer.getElapsedNanos()).thenReturn(RANDOM.nextLong(1000));
    // Must be non-zero: merged ExplainTimings use SamplingInvocationCountingTimer for
    // high-frequency types, and getElapsedNanos() returns 0 when measurementCount is 0.
    when(mockedTimer.getInvocationCount()).thenReturn(1 + RANDOM.nextLong(999));
    return new ExplainTimings(ignored -> mockedTimer);
  }

  public static void assertTimings(ExplainTimings first, ExplainTimings second) {
    Truth.assertThat(first.stream().collect(ExplainTimings.toExplainTimingData()))
        .isEqualTo(second.stream().collect(ExplainTimings.toExplainTimingData()));
  }
}
