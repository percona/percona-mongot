package com.xgen.mongot.index.lucene;

import static org.junit.Assert.assertEquals;

import com.xgen.mongot.index.query.collectors.MetricDefinition;
import org.bson.BsonDouble;
import org.junit.Test;

public class NumericMetricAccumulatorTest {

  private static final double INF = Double.POSITIVE_INFINITY;

  private static NumericMetricAccumulator accumulate(double... values) {
    NumericMetricAccumulator accumulator = new NumericMetricAccumulator();
    for (double value : values) {
      accumulator.add(value);
    }
    return accumulator;
  }

  @Test
  public void testCompensatedSumRecoversLowOrderBits() {
    // A naive sum loses the 1.0 against 1e16 and returns 0.0.
    assertEquals(1.0, accumulate(1e16, 1.0, -1e16).sum(), 0.0);
  }

  @Test
  public void testPositiveInfinityYieldsInfiniteSumAndAvg() {
    NumericMetricAccumulator accumulator = accumulate(1.0, INF, 2.0);

    assertEquals(INF, accumulator.sum(), 0.0);
    assertEquals(new BsonDouble(INF), accumulator.resultFor(MetricDefinition.Type.SUM));
    assertEquals(new BsonDouble(INF), accumulator.resultFor(MetricDefinition.Type.AVG));
    assertEquals(new BsonDouble(1.0), accumulator.resultFor(MetricDefinition.Type.MIN));
    assertEquals(new BsonDouble(INF), accumulator.resultFor(MetricDefinition.Type.MAX));
  }

  @Test
  public void testNegativeInfinityYieldsNegativeInfiniteSum() {
    assertEquals(-INF, accumulate(-INF, 2.0).sum(), 0.0);
  }

  @Test
  public void testOppositeInfinitiesYieldNan() {
    assertEquals(Double.NaN, accumulate(INF, 1.0, -INF).sum(), 0.0);
  }

  @Test
  public void testOverflowYieldsInfinity() {
    assertEquals(INF, accumulate(1e308, 1e308).sum(), 0.0);
    assertEquals(-INF, accumulate(-1e308, -1e308).sum(), 0.0);
  }

  @Test
  public void testMergeWithInfinitePartialYieldsInfinity() {
    NumericMetricAccumulator finite = accumulate(1.0, 2.0);
    finite.merge(accumulate(INF));
    assertEquals(INF, finite.sum(), 0.0);
    assertEquals(3, finite.count());

    NumericMetricAccumulator infinite = accumulate(INF);
    infinite.merge(accumulate(1.0, 2.0));
    assertEquals(INF, infinite.sum(), 0.0);
  }

  @Test
  public void testMergeOverflowYieldsInfinity() {
    NumericMetricAccumulator accumulator = accumulate(1e308);
    accumulator.merge(accumulate(1e308));
    assertEquals(INF, accumulator.sum(), 0.0);
  }

  @Test
  public void testMergeOppositeInfinitiesYieldsNan() {
    NumericMetricAccumulator accumulator = accumulate(INF);
    accumulator.merge(accumulate(-INF));
    assertEquals(Double.NaN, accumulator.sum(), 0.0);
  }
}
