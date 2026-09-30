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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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

  public LuceneMetricsCollectorSearchManagerTest() throws IOException {
    this.indexWriter =
        new IndexWriter(
            this.directory,
            new IndexWriterConfig(
                    LuceneAnalyzer.indexAnalyzer(
                        SearchIndex.MOCK_INDEX_DEFINITION, AnalyzerRegistryBuilder.empty()))
                .setCodec(new LuceneCodec()));
  }

  @After
  public void after() throws IOException {
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

  private LuceneIndexSearcherReference searcherReference() throws IOException {
    this.indexWriter.commit();
    var searcherManager =
        LuceneSearcherManager.create(
            this.indexWriter,
            new LuceneSearcherFactory(
                SearchIndex.MOCK_INDEX_DEFINITION,
                false,
                new QueryCacheProvider.DefaultQueryCacheProvider(),
                Optional.empty(),
                SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
            SearchIndex.mockMetricsFactory(),
            () -> false);
    return LuceneIndexSearcherReference.create(
        searcherManager,
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
    // Unlike NaN, infinity is indexed as an ordinary value; the separate segments exercise reduce().
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
    // Several commits produce several segments; the metrics collector manager must reduce them.
    for (int i = 1; i <= 5; i++) {
      this.indexWriter.addDocument(numericDoc(RATING, i));
      this.indexWriter.commit();
    }
    LuceneIndexSearcherReference searcherReference = searcherReference();
    LuceneIndexSearcher searcher = searcherReference.getIndexSearcher();
    assertTrue(searcher.getIndexReader().leaves().size() > 1);

    var manager =
        new LuceneMetricsCollectorSearchManager(
            new MatchAllDocsQuery(), Optional.empty(), Optional.empty(), List.of(RATING));
    var queryInfo = manager.initialSearch(searcherReference, 10);

    NumericMetricAccumulator rating = queryInfo.accumulatorsByLuceneField.get(RATING);
    assertEquals(5, rating.count());
    assertEquals(15.0, rating.sum(), DELTA);
    assertEquals(1.0, rating.min(), DELTA);
    assertEquals(5.0, rating.max(), DELTA);
  }
}
