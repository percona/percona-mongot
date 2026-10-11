package com.xgen.mongot.index.lucene;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.index.IntermediateMetricBucket;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import java.util.List;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.bson.BsonValue;

/**
 * Accumulates the algebraic components (sum, count, min, max) needed to answer every {@link
 * MetricDefinition.Type} over a stream of doubles. Summation is Neumaier-compensated so results
 * depend as little as possible on segment and partition visiting order. Not thread safe; each
 * Lucene leaf collector owns its own instance and instances are merged afterwards.
 */
final class NumericMetricAccumulator {

  private double sum;
  private double compensation;
  private long count;
  private double min = Double.POSITIVE_INFINITY;
  private double max = Double.NEGATIVE_INFINITY;

  void add(double value) {
    addToSum(value);
    this.count++;
    this.min = Math.min(this.min, value);
    this.max = Math.max(this.max, value);
  }

  /** Folds {@code other} into this accumulator. Exact for count/min/max; compensated for sum. */
  void merge(NumericMetricAccumulator other) {
    if (other.count == 0) {
      return;
    }
    addToSum(other.sum());
    this.count += other.count;
    this.min = Math.min(this.min, other.min);
    this.max = Math.max(this.max, other.max);
  }

  /** One Neumaier step: adds {@code value} to the sum, keeping the lost low-order bits. */
  private void addToSum(double value) {
    double t = this.sum + value;
    if (Math.abs(this.sum) >= Math.abs(value)) {
      this.compensation += (this.sum - t) + value;
    } else {
      this.compensation += (value - t) + this.sum;
    }
    this.sum = t;
  }

  /** Test factory for an accumulator already holding the given components. */
  @VisibleForTesting
  static NumericMetricAccumulator of(double sum, long count, double min, double max) {
    NumericMetricAccumulator accumulator = new NumericMetricAccumulator();
    accumulator.sum = sum;
    accumulator.count = count;
    accumulator.min = min;
    accumulator.max = max;
    return accumulator;
  }

  double sum() {
    double compensatedSum = this.sum + this.compensation;
    // Once the running sum is infinite (an infinite value, or overflow) the compensation becomes
    // NaN (inf - inf); the running sum is then the IEEE result. Same as DoubleSummaryStatistics.
    if (Double.isNaN(compensatedSum) && Double.isInfinite(this.sum)) {
      return this.sum;
    }
    return compensatedSum;
  }

  @VisibleForTesting
  long count() {
    return this.count;
  }

  @VisibleForTesting
  double min() {
    return this.min;
  }

  @VisibleForTesting
  double max() {
    return this.max;
  }

  /**
   * Final value for {@code type}: BSON null for avg/min/max when nothing was accumulated (mirroring
   * MQL {@code $avg}/{@code $min}/{@code $max}), {@code 0.0} for an empty sum (mirroring {@code
   * $sum}); a BSON double otherwise.
   */
  BsonValue resultFor(MetricDefinition.Type type) {
    return switch (type) {
      case SUM -> new BsonDouble(sum());
      case AVG -> this.count == 0 ? BsonNull.VALUE : new BsonDouble(sum() / this.count);
      case MIN -> this.count == 0 ? BsonNull.VALUE : new BsonDouble(this.min);
      case MAX -> this.count == 0 ? BsonNull.VALUE : new BsonDouble(this.max);
    };
  }

  List<IntermediateMetricBucket> decompose(String tag) {
    return IntermediateMetricBucket.decompose(tag, sum(), this.count, this.min, this.max);
  }
}
