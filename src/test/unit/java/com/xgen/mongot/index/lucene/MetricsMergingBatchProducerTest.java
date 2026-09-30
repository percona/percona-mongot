package com.xgen.mongot.index.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.index.CountResult;
import com.xgen.mongot.index.MetaResults;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.index.query.collectors.MetricsCollector;
import com.xgen.mongot.index.query.counts.Count;
import com.xgen.mongot.util.Bytes;
import com.xgen.testing.mongot.index.query.collectors.CollectorBuilder;
import com.xgen.testing.mongot.index.query.collectors.MetricDefinitionBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.junit.Test;

public class MetricsMergingBatchProducerTest {

  private static final double DELTA = 1e-9;

  private static final MetricsCollector COLLECTOR =
      CollectorBuilder.metrics()
          .metricDefinitions(
              Map.of(
                  "avgRating",
                  MetricDefinitionBuilder.builder()
                      .type(MetricDefinition.Type.AVG)
                      .path("rating")
                      .build(),
                  "minRating",
                  MetricDefinitionBuilder.builder()
                      .type(MetricDefinition.Type.MIN)
                      .path("rating")
                      .build(),
                  "sumPrice",
                  MetricDefinitionBuilder.builder()
                      .type(MetricDefinition.Type.SUM)
                      .path("price")
                      .build(),
                  "maxPrice",
                  MetricDefinitionBuilder.builder()
                      .type(MetricDefinition.Type.MAX)
                      .path("price")
                      .build()))
          .build();

  private static LuceneMetricsCollectorMetaBatchProducer partition(
      long totalHits, NumericMetricAccumulator rating, NumericMetricAccumulator price) {
    return new LuceneMetricsCollectorMetaBatchProducer(
        totalHits,
        COLLECTOR,
        Map.of("avgRating", rating, "minRating", rating, "sumPrice", price, "maxPrice", price));
  }

  private static MetricsMergingBatchProducer threePartitions() {
    return new MetricsMergingBatchProducer(
        List.of(
            partition(
                2,
                NumericMetricAccumulator.of(3.0, 2, 1.0, 2.0),
                NumericMetricAccumulator.of(10.0, 1, 10.0, 10.0)),
            // Partition that matched documents but saw no values for either field.
            partition(1, new NumericMetricAccumulator(), new NumericMetricAccumulator()),
            partition(
                3,
                NumericMetricAccumulator.of(9.0, 3, -1.0, 5.0),
                NumericMetricAccumulator.of(-2.5, 2, -4.0, 1.5))));
  }

  @Test
  public void testGetMetaResultsAndCloseMergesPartitions() {
    MetaResults metaResults = threePartitions().getMetaResultsAndClose(Count.Type.TOTAL);

    assertEquals(CountResult.totalCount(6), metaResults.count());
    assertTrue(metaResults.facet().isEmpty());
    var metrics = metaResults.metrics().orElseThrow();
    assertEquals(12.0 / 5, metrics.get("avgRating").asDouble().getValue(), DELTA);
    assertEquals(-1.0, metrics.get("minRating").asDouble().getValue(), DELTA);
    assertEquals(7.5, metrics.get("sumPrice").asDouble().getValue(), DELTA);
    assertEquals(10.0, metrics.get("maxPrice").asDouble().getValue(), DELTA);
  }

  @Test
  public void testEmptyPartitionsYieldNullAndZero() {
    var producer =
        new MetricsMergingBatchProducer(
            List.of(
                partition(0, new NumericMetricAccumulator(), new NumericMetricAccumulator()),
                partition(0, new NumericMetricAccumulator(), new NumericMetricAccumulator())));
    var metrics = producer.getMetaResultsAndClose(Count.Type.LOWER_BOUND).metrics().orElseThrow();

    assertEquals(BsonNull.VALUE, metrics.get("avgRating"));
    assertEquals(BsonNull.VALUE, metrics.get("minRating"));
    assertEquals(BsonNull.VALUE, metrics.get("maxPrice"));
    assertEquals(new BsonDouble(0.0), metrics.get("sumPrice"));
  }

  @Test
  public void testGetNextBatchEmitsCountThenMergedBucketsInNameOrder() throws IOException {
    MetricsMergingBatchProducer producer = threePartitions();
    BsonArray batch = producer.getNextBatch(Bytes.ofBytes(1 << 20));
    assertTrue(producer.isExhausted());

    BsonDocument count = batch.get(0).asDocument();
    assertEquals("count", count.getString("type").getValue());
    assertEquals(6, count.getInt64("count").getValue());

    // Four buckets (sum, count, min, max) per metric; metrics ordered by name.
    assertEquals(1 + 4 * 4, batch.size());
    List<String> tags =
        batch.subList(1, batch.size()).stream()
            .map(value -> value.asDocument().getString("tag").getValue())
            .distinct()
            .toList();
    assertEquals(List.of("avgRating", "maxPrice", "minRating", "sumPrice"), tags);

    BsonDocument avgSum = batch.get(1).asDocument();
    assertEquals("metric", avgSum.getString("type").getValue());
    assertEquals("sum", avgSum.getString("bucket").getValue());
    assertEquals(12.0, avgSum.getDouble("value").getValue(), DELTA);
    BsonDocument avgCount = batch.get(2).asDocument();
    assertEquals("count", avgCount.getString("bucket").getValue());
    assertEquals(5, avgCount.getInt64("count").getValue());
    assertEquals(-1.0, batch.get(3).asDocument().getDouble("value").getValue(), DELTA);
    assertEquals(5.0, batch.get(4).asDocument().getDouble("value").getValue(), DELTA);
  }

  @Test
  public void testMinMaxBucketsOmittedWhenNoValues() throws IOException {
    var producer =
        new LuceneMetricsCollectorMetaBatchProducer(
            0, COLLECTOR, Map.of("avgRating", new NumericMetricAccumulator()));
    BsonArray batch = producer.getNextBatch(Bytes.ofBytes(1 << 20));

    assertEquals(3, batch.size());
    assertEquals("sum", batch.get(1).asDocument().getString("bucket").getValue());
    assertEquals(0.0, batch.get(1).asDocument().getDouble("value").getValue(), DELTA);
    assertEquals("count", batch.get(2).asDocument().getString("bucket").getValue());
    assertEquals(0, batch.get(2).asDocument().getInt64("count").getValue());
  }
}
