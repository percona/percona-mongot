package com.xgen.mongot.index.lucene;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.cursor.batch.BatchCursorOptions;
import com.xgen.mongot.index.BatchProducer;
import com.xgen.mongot.index.MetaResults;
import com.xgen.mongot.index.query.counts.Count;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;

/**
 * Merges the metric results of multiple {@link LuceneMetricsCollectorMetaBatchProducer}s, each from
 * one index partition, into a single producer. Like {@link FacetMergingBatchProducer} it serves two
 * mutually exclusive purposes: 1) getNextBatch() for intermediate (sharded) results 2) draining
 * everything to a {@link MetaResults}.
 */
public class MetricsMergingBatchProducer implements BatchProducer {

  private final List<LuceneMetricsCollectorMetaBatchProducer> batchProducers;
  private final LuceneMetricsCollectorMetaBatchProducer merged;

  public MetricsMergingBatchProducer(List<LuceneMetricsCollectorMetaBatchProducer> batchProducers) {
    Check.argNotEmpty(batchProducers, "batchProducers");
    this.batchProducers = batchProducers;
    this.merged = merge(batchProducers);
  }

  private static LuceneMetricsCollectorMetaBatchProducer merge(
      List<LuceneMetricsCollectorMetaBatchProducer> batchProducers) {
    Map<String, NumericMetricAccumulator> mergedAccumulators = new HashMap<>();
    @Var long mergedTotalHits = 0;
    for (LuceneMetricsCollectorMetaBatchProducer producer : batchProducers) {
      mergedTotalHits += producer.getTotalHits();
      producer
          .getAccumulatorsByMetricName()
          .forEach(
              (name, accumulator) ->
                  mergedAccumulators
                      .computeIfAbsent(name, ignored -> new NumericMetricAccumulator())
                      .merge(accumulator));
    }
    return new LuceneMetricsCollectorMetaBatchProducer(
        mergedTotalHits, batchProducers.get(0).getMetricsCollector(), mergedAccumulators);
  }

  @Override
  public void execute(Bytes sizeLimit, BatchCursorOptions queryCursorOptions) throws IOException {}

  @Override
  public BsonArray getNextBatch(Bytes resultsSizeLimit) throws IOException {
    return this.merged.getNextBatch(resultsSizeLimit);
  }

  /** Drains the merged results to a {@link MetaResults} and closes this batch producer. */
  public MetaResults getMetaResultsAndClose(Count.Type countType) {
    MetaResults metaResults =
        LuceneMetaResultsBuilder.buildMetricsMetaResults(
            this.merged.getTotalHits(),
            countType,
            this.merged.getMetricsCollector(),
            this.merged.getAccumulatorsByMetricName());
    this.close();
    return metaResults;
  }

  @Override
  public boolean isExhausted() {
    return this.merged.isExhausted();
  }

  @Override
  public void close() {
    this.merged.close();
    for (var batchProducer : this.batchProducers) {
      batchProducer.close();
    }
  }
}
