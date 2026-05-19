package com.xgen.mongot.index.lucene.query.values;

import java.io.IOException;
import java.util.Objects;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;

public class GaussianDecayValuesSource extends DoubleValuesSource {
  private final DoubleValuesSource pathValue;
  private final double originValue;
  private final double scaleValue;
  private final double offsetValue;
  private final double decayValue;

  private GaussianDecayValuesSource(
      DoubleValuesSource pathValue,
      double originValue,
      double scaleValue,
      double offsetValue,
      double decayValue) {
    this.pathValue = pathValue;
    this.originValue = originValue;
    this.scaleValue = scaleValue;
    this.offsetValue = offsetValue;
    this.decayValue = decayValue;
  }

  public static GaussianDecayValuesSource create(
      DoubleValuesSource pathValue,
      double originValue,
      double scaleValue,
      double offsetValue,
      double decayValue) {
    return new GaussianDecayValuesSource(
        pathValue, originValue, scaleValue, offsetValue, decayValue);
  }

  /** Implements standard Gaussian Decay Formula */
  @Override
  public DoubleValues getValues(LeafReaderContext ctx, DoubleValues scores) throws IOException {
    DoubleValues fieldValue = this.pathValue.getValues(ctx, scores);

    return new DoubleValues() {
      @Override
      public double doubleValue() throws IOException {
        double num =
            Math.pow(
                Math.max(
                    0,
                    Math.abs(fieldValue.doubleValue() - GaussianDecayValuesSource.this.originValue)
                        - GaussianDecayValuesSource.this.offsetValue),
                2);

        double den =
            Math.pow(GaussianDecayValuesSource.this.scaleValue, 2)
                / Math.log(GaussianDecayValuesSource.this.decayValue);

        return Math.exp(num / den);
      }

      @Override
      public boolean advanceExact(int doc) throws IOException {
        return fieldValue.advanceExact(doc);
      }
    };
  }

  @Override
  public boolean needsScores() {
    return this.pathValue.needsScores();
  }

  @Override
  public DoubleValuesSource rewrite(IndexSearcher reader) throws IOException {
    return new GaussianDecayValuesSource(
        this.pathValue.rewrite(reader),
        this.originValue,
        this.scaleValue,
        this.offsetValue,
        this.decayValue);
  }

  @Override
  public String toString() {
    return String.format(
        "exp((max(0, |%s - %s| - %s)^2) / 2 * (%s^2 / 2 * ln(%s)))",
        this.pathValue.toString(),
        this.originValue,
        this.offsetValue,
        this.scaleValue,
        this.decayValue);
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    return this.pathValue.isCacheable(ctx);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof GaussianDecayValuesSource that)) {
      return false;
    }
    return this.pathValue.equals(that.pathValue)
        && this.originValue == that.originValue
        && this.scaleValue == that.scaleValue
        && this.offsetValue == that.offsetValue
        && this.decayValue == that.decayValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.pathValue, this.originValue, this.scaleValue, this.offsetValue, this.decayValue);
  }
}
