package com.xgen.mongot.index.lucene;

import static org.junit.Assert.assertEquals;

import com.xgen.mongot.index.IntermediateMetricBucket;
import com.xgen.mongot.index.IntermediateMetricBucket.Component;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import java.util.List;
import java.util.Optional;
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
  public void testMergeKeepsCompensationAcrossPartials() {
    // The 1.0 lives only in the first partial's compensation; rounding it into the sum before
    // merging would lose it and return 0.0.
    NumericMetricAccumulator merged = new NumericMetricAccumulator();
    merged.merge(accumulate(1e16, 1.0));
    merged.merge(accumulate(-1e16));

    assertEquals(1.0, merged.sum(), 0.0);
    assertEquals(3, merged.count());
    assertEquals(new BsonDouble(1.0 / 3), merged.resultFor(MetricDefinition.Type.AVG));
  }

  @Test
  public void testMergeIsIndependentOfPartitioning() {
    // Credits and debits that net to a small balance, split unevenly across partials.
    double[] values = {1e16, 0.25, -1e16, 3e15, 0.5, -3e15, 0.125};
    double expected = accumulate(values).sum();

    NumericMetricAccumulator merged = new NumericMetricAccumulator();
    merged.merge(accumulate(values[0], values[1]));
    merged.merge(accumulate(values[2], values[3], values[4]));
    merged.merge(accumulate(values[5], values[6]));

    assertEquals(0.875, expected, 0.0);
    assertEquals(expected, merged.sum(), 0.0);
  }

  @Test
  public void testDecomposeSendsCompensationAsSecondSumBucket() {
    // sum() would round 1e16 + 1.0 to 1e16; the sharded merge needs the 1.0 to cancel against a
    // -1e16 on another shard.
    assertEquals(
        List.of(
            sumBucket(1e16),
            sumBucket(1.0),
            countBucket(2),
            valueBucket(Component.MIN, 1.0),
            valueBucket(Component.MAX, 1e16)),
        accumulate(1e16, 1.0).decompose("t"));
  }

  @Test
  public void testDecomposeOmitsZeroCompensation() {
    assertEquals(
        List.of(
            sumBucket(3.0),
            countBucket(2),
            valueBucket(Component.MIN, 1.0),
            valueBucket(Component.MAX, 2.0)),
        accumulate(1.0, 2.0).decompose("t"));
  }

  @Test
  public void testDecomposeSendsOnlyInfiniteSum() {
    assertEquals(
        List.of(
            sumBucket(INF),
            countBucket(2),
            valueBucket(Component.MIN, 1.0),
            valueBucket(Component.MAX, INF)),
        accumulate(1.0, INF).decompose("t"));
  }

  @Test
  public void testDecomposeEmptyAccumulator() {
    assertEquals(
        List.of(sumBucket(0.0), countBucket(0)), new NumericMetricAccumulator().decompose("t"));
  }

  private static IntermediateMetricBucket sumBucket(double value) {
    return valueBucket(Component.SUM, value);
  }

  private static IntermediateMetricBucket valueBucket(Component component, double value) {
    return new IntermediateMetricBucket("t", component, Optional.of(value), Optional.empty());
  }

  private static IntermediateMetricBucket countBucket(long count) {
    return new IntermediateMetricBucket("t", Component.COUNT, Optional.empty(), Optional.of(count));
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
