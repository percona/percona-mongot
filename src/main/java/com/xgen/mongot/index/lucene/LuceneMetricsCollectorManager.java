package com.xgen.mongot.index.lucene;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.xgen.mongot.index.lucene.util.LuceneDoubleConversionUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SimpleCollector;

/**
 * Accumulates a {@link NumericMetricAccumulator} per Lucene field over every matching document by
 * reading the {@code SortedNumericDocValues} that {@code number} fields index under {@link
 * com.xgen.mongot.index.lucene.field.FieldName.TypeField#NUMBER_DOUBLE_V2}. Values are decoded with
 * {@link LuceneDoubleConversionUtils#fromMqlSortableLong} and every value of a multi-valued
 * document contributes. NaN values are skipped, unlike MQL {@code $sum}/{@code $avg}/{@code $min},
 * which return NaN once one is present. Safe for concurrent segment search: each slice gets its
 * own collector and {@link #reduce} merges them.
 */
final class LuceneMetricsCollectorManager
    implements CollectorManager<
        LuceneMetricsCollectorManager.LuceneMetricsCollector,
        Map<String, NumericMetricAccumulator>> {

  private final ImmutableList<String> luceneFields;

  LuceneMetricsCollectorManager(Collection<String> luceneFields) {
    // Several metrics may target the same field; each field must be read once per document.
    this.luceneFields = ImmutableSet.copyOf(luceneFields).asList();
  }

  @Override
  public LuceneMetricsCollector newCollector() {
    return new LuceneMetricsCollector(this.luceneFields);
  }

  @Override
  public Map<String, NumericMetricAccumulator> reduce(
      Collection<LuceneMetricsCollector> collectors) {
    Map<String, NumericMetricAccumulator> merged = new HashMap<>();
    for (int i = 0; i < this.luceneFields.size(); i++) {
      NumericMetricAccumulator accumulator = new NumericMetricAccumulator();
      for (LuceneMetricsCollector collector : collectors) {
        accumulator.merge(collector.accumulators[i]);
      }
      merged.put(this.luceneFields.get(i), accumulator);
    }
    return merged;
  }

  static final class LuceneMetricsCollector extends SimpleCollector {
    private final ImmutableList<String> fields;
    private final NumericMetricAccumulator[] accumulators;
    private final SortedNumericDocValues[] leafValues;

    private LuceneMetricsCollector(ImmutableList<String> fields) {
      this.fields = fields;
      this.accumulators = new NumericMetricAccumulator[fields.size()];
      for (int i = 0; i < this.accumulators.length; i++) {
        this.accumulators[i] = new NumericMetricAccumulator();
      }
      this.leafValues = new SortedNumericDocValues[fields.size()];
    }

    @Override
    protected void doSetNextReader(LeafReaderContext context) throws IOException {
      for (int i = 0; i < this.fields.size(); i++) {
        this.leafValues[i] = DocValues.getSortedNumeric(context.reader(), this.fields.get(i));
      }
    }

    @Override
    public void collect(int doc) throws IOException {
      for (int i = 0; i < this.leafValues.length; i++) {
        SortedNumericDocValues values = this.leafValues[i];
        if (!values.advanceExact(doc)) {
          continue;
        }
        NumericMetricAccumulator accumulator = this.accumulators[i];
        int valueCount = values.docValueCount();
        for (int j = 0; j < valueCount; j++) {
          double value = LuceneDoubleConversionUtils.fromMqlSortableLong(values.nextValue());
          if (!Double.isNaN(value)) {
            accumulator.add(value);
          }
        }
      }
    }

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }
}
