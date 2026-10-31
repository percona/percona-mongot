package com.xgen.mongot.index.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.analyzer.wrapper.LuceneAnalyzer;
import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.lucene.codec.LuceneCodec;
import com.xgen.mongot.index.lucene.field.FieldName.TypeField;
import com.xgen.mongot.index.lucene.searcher.LuceneIndexSearcher;
import com.xgen.mongot.index.lucene.searcher.LuceneSearcherFactory;
import com.xgen.mongot.index.lucene.searcher.LuceneSearcherManager;
import com.xgen.mongot.index.lucene.searcher.QueryCacheProvider;
import com.xgen.mongot.index.lucene.util.LuceneDoubleConversionUtils;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.mongot.index.analyzer.AnalyzerRegistryBuilder;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.junit.After;
import org.junit.Test;

public class LuceneMetricsCollectorSearchManagerTest {

  private static final String RATING =
      TypeField.NUMBER_DOUBLE_V2.getLuceneFieldName(FieldPath.parse("rating"), Optional.empty());
  private static final String PRICE =
      TypeField.NUMBER_DOUBLE_V2.getLuceneFieldName(FieldPath.parse("price"), Optional.empty());
  private static final double DELTA = 1e-9;

  private final Directory directory = new ByteBuffersDirectory();
  private final IndexWriter indexWriter;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  public LuceneMetricsCollectorSearchManagerTest() throws IOException {
    // No merges, so every commit stays its own segment and slice layouts are deterministic.
    this.indexWriter =
        new IndexWriter(
            this.directory,
            new IndexWriterConfig(
                    LuceneAnalyzer.indexAnalyzer(
                        SearchIndex.MOCK_INDEX_DEFINITION, AnalyzerRegistryBuilder.empty()))
                .setCodec(new LuceneCodec())
                .setMergePolicy(NoMergePolicy.INSTANCE));
  }

  @After
  public void after() throws IOException {
    this.executor.shutdownNow();
    this.indexWriter.close();
    this.directory.close();
  }

  /** Indexes {@code values} under {@code field} the same way a {@code number} field is indexed. */
  private static Document numericDoc(String field, double... values) {
    Document doc = new Document();
    for (double value : values) {
      doc.add(
          new LongField(
              field, LuceneDoubleConversionUtils.toMqlSortableLong(value), Field.Store.NO));
    }
    return doc;
  }

  private LuceneSearcherManager searcherManager() throws IOException {
    this.indexWriter.commit();
    return LuceneSearcherManager.create(
        this.indexWriter,
        new LuceneSearcherFactory(
            SearchIndex.MOCK_INDEX_DEFINITION,
            false,
            new QueryCacheProvider.DefaultQueryCacheProvider(),
            Optional.empty(),
            SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
        SearchIndex.mockMetricsFactory(),
        () -> false);
  }

  /** A searcher without an executor: every segment is collected in one slice. */
  private LuceneIndexSearcherReference searcherReference() throws IOException {
    return LuceneIndexSearcherReference.create(
        searcherManager(),
        SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH),
        FeatureFlags.getDefault());
  }

  /** A concurrent searcher: segments are grouped into slices, each with its own collector. */
  private LuceneIndexSearcherReference concurrentSearcherReference() throws IOException {
    return LuceneIndexSearcherReference.create(
        searcherManager(),
        this.executor,
        SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH),
        FeatureFlags.getDefault());
  }

  @Test
  public void testAccumulatesSumCountMinMax() throws IOException {
    this.indexWriter.addDocument(numericDoc(RATING, 1.5));
    this.indexWriter.addDocument(numericDoc(RATING, 2.5));
    this.indexWriter.addDocument(numericDoc(RATING, -4.0));
    // Document without the field must not contribute to the count.
    this.indexWriter.addDocument(numericDoc(PRICE, 10.0));

    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING, PRICE));
    var queryInfo = manager.initialSearch(searcherReference(), 2);

    assertEquals(TotalHits.Relation.EQUAL_TO, queryInfo.topDocs.totalHits.relation());
    assertEquals(4, queryInfo.topDocs.totalHits.value());
    assertEquals(2, queryInfo.topDocs.scoreDocs.length);

    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(3, rating.count());
    assertEquals(0.0, rating.sum(), DELTA);
    assertEquals(-4.0, rating.min(), DELTA);
    assertEquals(2.5, rating.max(), DELTA);
    assertEquals(new BsonDouble(0.0), rating.resultFor(MetricDefinition.Type.AVG));

    NumericMetricAccumulator price = queryInfo.accumulatorsByLuceneField.get(PRICE);
    assertEquals(1, price.count());
    assertEquals(new BsonDouble(10.0), price.resultFor(MetricDefinition.Type.SUM));
  }

  @Test
  public void testDuplicateFieldsAreReadOncePerDocument() throws IOException {
    this.indexWriter.addDocument(numericDoc(RATING, 2.0));
    this.indexWriter.addDocument(numericDoc(RATING, 4.0));

    // Several metrics (avg, sum, ...) on the same path resolve to the same Lucene field.
    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(),
            Optional.empty(),
            Optional.empty(),
            List.of(RATING, RATING, RATING));
    var queryInfo = manager.initialSearch(searcherReference(), 10);

    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(2, rating.count());
    assertEquals(6.0, rating.sum(), DELTA);
  }

  @Test
  public void testMultiValuedDocumentContributesEveryValueAndNanIsSkipped() throws IOException {
    this.indexWriter.addDocument(numericDoc(RATING, 1.0, 3.0));
    this.indexWriter.addDocument(numericDoc(RATING, Double.NaN));

    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING));
    var queryInfo = manager.initialSearch(searcherReference(), 10);

    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(2, rating.count());
    assertEquals(4.0, rating.sum(), DELTA);
    assertEquals(new BsonDouble(2.0), rating.resultFor(MetricDefinition.Type.AVG));
  }

  @Test
  public void testInfinityIsAccumulatedAcrossSegments() throws IOException {
    // Unlike NaN, infinity is indexed as an ordinary value. Separate segments make the collector
    // switch leaves mid-search.
    this.indexWriter.addDocument(numericDoc(RATING, 1.0));
    this.indexWriter.commit();
    this.indexWriter.addDocument(numericDoc(RATING, Double.POSITIVE_INFINITY));

    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING));
    var queryInfo = manager.initialSearch(searcherReference(), 10);

    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(2, rating.count());
    assertEquals(
        new BsonDouble(Double.POSITIVE_INFINITY), rating.resultFor(MetricDefinition.Type.SUM));
    assertEquals(
        new BsonDouble(Double.POSITIVE_INFINITY), rating.resultFor(MetricDefinition.Type.AVG));
    assertEquals(new BsonDouble(1.0), rating.resultFor(MetricDefinition.Type.MIN));
  }

  @Test
  public void testEmptyIndexYieldsNullForAvgMinMaxAndZeroForSum() throws IOException {
    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING));
    var queryInfo = manager.initialSearch(searcherReference(), 10);

    assertEquals(0, queryInfo.topDocs.totalHits.value());
    assertTrue(queryInfo.luceneExhausted);
    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(BsonNull.VALUE, rating.resultFor(MetricDefinition.Type.AVG));
    assertEquals(BsonNull.VALUE, rating.resultFor(MetricDefinition.Type.MIN));
    assertEquals(BsonNull.VALUE, rating.resultFor(MetricDefinition.Type.MAX));
    assertEquals(new BsonDouble(0.0), rating.resultFor(MetricDefinition.Type.SUM));
  }

  @Test
  public void testConcurrentSegmentSearchMergesAccumulators() throws IOException {
    // One document per segment. Lucene groups at most 5 segments into a slice, so 6 segments give
    // 2 slices, each collected by its own collector that reduce() must merge. 1e16 and 1.0 land in
    // the first slice and -1e16 in the second: the 1.0 survives only if the merge keeps the sum's
    // compensation.
    double[] values = {1e16, 1.0, 0.0, 0.0, 0.0, -1e16};
    for (double value : values) {
      this.indexWriter.addDocument(numericDoc(RATING, value));
      this.indexWriter.commit();
    }
    LuceneIndexSearcherReference searcherReference = concurrentSearcherReference();
    LuceneIndexSearcher searcher = searcherReference.getIndexSearcher();
    assertEquals(values.length, searcher.getIndexReader().leaves().size());
    assertTrue(searcher.getSlices().length > 1);

    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING));
    var queryInfo = manager.initialSearch(searcherReference, 10);

    assertEquals(values.length, queryInfo.topDocs.totalHits.value());
    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(values.length, rating.count());
    assertEquals(1.0, rating.sum(), 0.0);
    assertEquals(-1e16, rating.min(), 0.0);
    assertEquals(1e16, rating.max(), 0.0);
  }
}
