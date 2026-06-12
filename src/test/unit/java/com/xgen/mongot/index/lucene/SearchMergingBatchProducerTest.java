package com.xgen.mongot.index.lucene;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.cursor.CursorConfig;
import com.xgen.mongot.cursor.batch.AdjustableBatchSizeStrategy;
import com.xgen.mongot.cursor.batch.BatchCursorOptions;
import com.xgen.mongot.cursor.batch.BatchSizeStrategy;
import com.xgen.mongot.cursor.batch.ConstantBatchSizeStrategy;
import com.xgen.mongot.cursor.batch.QueryCursorOptions;
import com.xgen.mongot.index.BatchProducer;
import com.xgen.mongot.index.ResultFactory;
import com.xgen.mongot.index.SearchResult;
import com.xgen.mongot.index.VectorSearchResult;
import com.xgen.mongot.index.definition.StoredSourceDefinition;
import com.xgen.mongot.index.lucene.LuceneSearchManager.QueryInfo;
import com.xgen.mongot.index.lucene.explain.information.LuceneQuerySpecification;
import com.xgen.mongot.index.lucene.explain.information.SearchExplainInformation;
import com.xgen.mongot.index.lucene.explain.information.SearchExplainInformationBuilder;
import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.mongot.index.lucene.query.pushdown.project.ProjectFactory;
import com.xgen.mongot.index.lucene.query.pushdown.project.ProjectSpec;
import com.xgen.mongot.index.lucene.searcher.LuceneIndexSearcher;
import com.xgen.mongot.index.lucene.util.LuceneDocumentIdEncoder;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import com.xgen.testing.fakes.FakeStoredFields;
import com.xgen.testing.mongot.cursor.batch.BatchCursorOptionsBuilder;
import com.xgen.testing.mongot.index.IndexMetricsUpdaterBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.QueryExplainInformationBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.TermQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.tracing.FakeExplain;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class SearchMergingBatchProducerTest {

  @DataPoints
  public static ResultFactory[] testData = {SearchResult::new, VectorSearchResult::create};

  @Theory
  public void testGetSingleBatch(ResultFactory resultFactory) throws Exception {
    int batchSize = 25;
    int numBatchProducers = 11;
    int numDocs = 10_000;
    var cursorOptions = BatchCursorOptionsBuilder.builder().batchSize(batchSize).build();
    List<LuceneSearchBatchProducer> luceneSearchBatchProducers = new ArrayList<>();
    List<MockSearchManager> searchManagers = new ArrayList<>();
    for (int i = 0; i < numBatchProducers; i++) {
      var searchManager = new MockSearchManager(numDocs);
      searchManagers.add(searchManager);
      var producer =
          createProducer(
              searchManager, new AdjustableBatchSizeStrategy(batchSize, false), resultFactory, i);
      luceneSearchBatchProducers.add(producer);
    }
    var searchAggregationBatchProducer = new SearchMergingBatchProducer(luceneSearchBatchProducers);

    searchAggregationBatchProducer.execute(
        CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT, cursorOptions);
    BsonArray batchResult =
        searchAggregationBatchProducer.getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT);
    Assert.assertEquals(batchSize, batchResult.size());
    searchAggregationBatchProducer.close();

    List<Float> scores = new ArrayList<>();
    for (BsonValue bsonValue : batchResult) {
      scores.add(getScoreFromOneResult(bsonValue));
    }
    float scoreComparisonError = 1e-4f;
    for (int i = 0; i < scores.size(); i++) {
      int scoreFactor = i / numBatchProducers;
      Assert.assertEquals(1.0f - 0.5f / numDocs * scoreFactor, scores.get(i), scoreComparisonError);
    }

    // Verify that each searchManager only fetches from Lucene once (no prefetch.)
    for (var searchManager : searchManagers) {
      Assert.assertEquals(1, searchManager.initialSearchCount);
      Assert.assertEquals(0, searchManager.getMoreCount);
    }
  }

  @Test
  public void testCloseClosesAllProducersWhenOneThrows() throws Exception {
    // Each producer holds a searcher reference; close() must release all of them even when an
    // earlier producer fails to close, otherwise the remaining searcher references leak.
    LuceneSearchBatchProducer first = mock(LuceneSearchBatchProducer.class);
    LuceneSearchBatchProducer second = mock(LuceneSearchBatchProducer.class);
    doThrow(new IOException("simulated close failure")).when(first).close();

    var producer = new SearchMergingBatchProducer(Arrays.asList(first, second));

    Assert.assertThrows(IOException.class, producer::close);
    verify(second).close();
  }

  @Theory
  public void testGetAllBatchesWithExplain(ResultFactory resultFactory) throws Exception {
    int docsRequested = 25;
    int numBatchProducers = 11;
    int numDocs = 10_000;
    var cursorOptions = BatchCursorOptionsBuilder.builder().docsRequested(docsRequested).build();
    List<LuceneSearchBatchProducer> luceneSearchBatchProducers = new ArrayList<>();
    for (int i = 0; i < numBatchProducers; i++) {
      var producer =
          createProducer(
              new MockSearchManager(numDocs),
              new AdjustableBatchSizeStrategy(docsRequested, false),
              resultFactory,
              i);
      luceneSearchBatchProducers.add(producer);
    }

    SearchExplainInformation explain =
        SearchExplainInformationBuilder.newBuilder()
            .queryExplainInfos(
                List.of(
                    QueryExplainInformationBuilder.builder()
                        .type(LuceneQuerySpecification.Type.TERM_QUERY)
                        .args(TermQueryBuilder.builder().path("a").value("hello").build())
                        .build()))
            .build();

    try (var unused =
        FakeExplain.setup(Explain.Verbosity.EXECUTION_STATS, numBatchProducers, explain)) {
      var searchAggregationBatchProducer =
          new SearchMergingBatchProducer(luceneSearchBatchProducers);
      @Var int returnedNumDocs = 0;
      List<Float> scores = new ArrayList<>();
      while (!searchAggregationBatchProducer.isExhausted()) {
        searchAggregationBatchProducer.execute(
            CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT, cursorOptions);

        BsonArray batchResult =
            searchAggregationBatchProducer.getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT);
        returnedNumDocs += batchResult.size();
        for (BsonValue bsonValue : batchResult) {
          scores.add(getScoreFromOneResult(bsonValue));
        }
      }
      // After exhausted, calling an extra getNextBatch() will return empty.
      Assert.assertTrue(
          searchAggregationBatchProducer
              .getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT)
              .isEmpty());
      int totalDocs = numDocs * numBatchProducers;
      Assert.assertEquals(totalDocs, returnedNumDocs);
      Assert.assertEquals(totalDocs, scores.size());
      searchAggregationBatchProducer.close();

      float scoreComparisonError = 1e-4f;
      for (int i = 0; i < scores.size(); i++) {
        int scoreFactor = i / numBatchProducers;
        Assert.assertEquals(
            1.0f - 0.5f / numDocs * scoreFactor, scores.get(i), scoreComparisonError);
      }

      Assert.assertEquals(Explain.collect().get(), explain);
    }
  }

  @Theory
  public void testZeroHit(ResultFactory resultFactory) throws Exception {
    int docsRequested = 25;
    int numBatchProducers = 11;
    int numDocs = 0;
    List<LuceneSearchBatchProducer> luceneSearchBatchProducers = new ArrayList<>();
    for (int i = 0; i < numBatchProducers; i++) {
      var producer =
          createProducer(
              new MockSearchManager(numDocs),
              new AdjustableBatchSizeStrategy(docsRequested, false),
              resultFactory,
              i);
      luceneSearchBatchProducers.add(producer);
    }
    var searchAggregationBatchProducer = new SearchMergingBatchProducer(luceneSearchBatchProducers);

    List<Integer> docsInBatches = new ArrayList<>();
    while (!searchAggregationBatchProducer.isExhausted()) {
      searchAggregationBatchProducer.execute(
          CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT, BatchCursorOptionsBuilder.empty());

      var batch =
          searchAggregationBatchProducer.getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT);
      docsInBatches.add(batch.size());
    }
    // After exhausted, calling an extra getNextBatch() will return empty.
    Assert.assertTrue(
        searchAggregationBatchProducer
            .getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT)
            .isEmpty());
    Integer[] expectedDocsInBatches = {0};
    Assert.assertEquals(Arrays.asList(expectedDocsInBatches), docsInBatches);
  }

  @Theory
  public void testGetAllResultsFromIndexPartitionInOneBatch(ResultFactory resultFactory)
      throws Exception {
    int docsRequested = BatchSizeStrategy.DEFAULT_BATCH_SIZE;
    int numBatchProducers = 11;
    int numDocs = docsRequested;
    var cursorOptions = BatchCursorOptionsBuilder.empty();
    List<LuceneSearchBatchProducer> luceneSearchBatchProducers = new ArrayList<>();
    for (int i = 0; i < numBatchProducers; i++) {
      var producer =
          createProducer(
              new MockSearchManager(numDocs), new ConstantBatchSizeStrategy(), resultFactory, i);
      luceneSearchBatchProducers.add(producer);
    }
    var searchAggregationBatchProducer = new SearchMergingBatchProducer(luceneSearchBatchProducers);
    @Var int returnedNumDocs = 0;
    List<Float> scores = new ArrayList<>();
    while (!searchAggregationBatchProducer.isExhausted()) {
      searchAggregationBatchProducer.execute(
          CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT, cursorOptions);

      BsonArray batchResult =
          searchAggregationBatchProducer.getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT);
      returnedNumDocs += batchResult.size();
      for (BsonValue bsonValue : batchResult) {
        scores.add(getScoreFromOneResult(bsonValue));
      }
    }
    // After exhausted, calling an extra getNextBatch() will return empty.
    Assert.assertTrue(
        searchAggregationBatchProducer
            .getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT)
            .isEmpty());
    int totalDocs = numDocs * numBatchProducers;
    Assert.assertEquals(totalDocs, returnedNumDocs);
    Assert.assertEquals(totalDocs, scores.size());
    searchAggregationBatchProducer.close();

    float scoreComparisonError = 1e-4f;
    for (int i = 0; i < scores.size(); i++) {
      int scoreFactor = i / numBatchProducers;
      Assert.assertEquals(1.0f - 0.5f / numDocs * scoreFactor, scores.get(i), scoreComparisonError);
    }
  }

  @Theory
  public void testWrappingSingleProducerYieldsIdenticalResults(ResultFactory resultFactory)
      throws Exception {
    int docsRequested = 27;
    int numDocs = 10_000;
    var cursorOptions = BatchCursorOptionsBuilder.builder().docsRequested(docsRequested).build();
    Bytes sizeLimit = CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT;
    var producer1 =
        createProducer(
            new MockSearchManager(numDocs),
            new AdjustableBatchSizeStrategy(docsRequested, false),
            resultFactory,
            0);
    List<BsonArray> batchResult1 = getAllBatches(producer1, sizeLimit, cursorOptions);

    var producer2 =
        createProducer(
            new MockSearchManager(numDocs),
            new AdjustableBatchSizeStrategy(docsRequested, false),
            resultFactory,
            0);
    var aggregationProducer = new SearchMergingBatchProducer(List.of(producer2));
    List<BsonArray> batchResult2 = getAllBatches(aggregationProducer, sizeLimit, cursorOptions);
    Assert.assertEquals(batchResult1.size(), batchResult2.size());
    Assert.assertEquals(batchResult1, batchResult2);
  }

  @Theory
  public void testWrappingSingleProducerYieldsIdenticalResults_batchesAreRounded(
      ResultFactory resultFactory) throws Exception {
    int docsRequested = 5;
    int numDocs = 5;
    var cursorOptions = BatchCursorOptionsBuilder.builder().docsRequested(docsRequested).build();
    Bytes sizeLimit = CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT;
    var producer1 =
        createProducer(
            new MockSearchManager(numDocs),
            new AdjustableBatchSizeStrategy(docsRequested, false),
            resultFactory,
            0);
    List<BsonArray> batchResult1 = getAllBatches(producer1, sizeLimit, cursorOptions);

    var producer2 =
        createProducer(
            new MockSearchManager(numDocs),
            new AdjustableBatchSizeStrategy(docsRequested, false),
            resultFactory,
            0);
    var aggregationProducer = new SearchMergingBatchProducer(List.of(producer2));
    List<BsonArray> batchResult2 = getAllBatches(aggregationProducer, sizeLimit, cursorOptions);
    Assert.assertEquals(batchResult1, batchResult2);
  }

  @Theory
  public void testMergedIterator(ResultFactory resultFactory) throws Exception {
    int docsRequested = 25;
    int numBatchProducers = 21;
    int numDocs = 10_000;
    List<LuceneSearchBatchProducer.SearchResultsIter> iters = new ArrayList<>();
    List<MockSearchManager> searchManagers = new ArrayList<>();
    var cursorOptions = BatchCursorOptionsBuilder.builder().docsRequested(docsRequested).build();
    for (int i = 0; i < numBatchProducers; i++) {
      var mockSearchManager = new MockSearchManager(numDocs);
      searchManagers.add(mockSearchManager);
      var producer =
          createProducer(
              mockSearchManager,
              new AdjustableBatchSizeStrategy(docsRequested, false),
              resultFactory,
              i);
      producer.execute(Bytes.ofMebi(16), cursorOptions);
      var iter = producer.getSearchResultsIter();
      iters.add(iter);
    }
    var mergedIter = new SearchMergingBatchProducer.MergedSearchResultIter(iters, cursorOptions);
    @Var int docsReturned = 0;
    List<ScoreDoc> scoreDocs = new ArrayList<>();
    Bytes sizeLimit = Bytes.ofMebi(16);
    while (mergedIter.peek(sizeLimit).isPresent()) {
      docsReturned++;
      scoreDocs.add(mergedIter.peek(sizeLimit).get().scoreDoc());
      mergedIter.acceptAndAdvance();
    }
    Assert.assertTrue(mergedIter.isExhausted());
    int docsExpected = numDocs * numBatchProducers;
    Assert.assertEquals(docsExpected, docsReturned);

    float scoreComparisonError = 1e-4f;
    for (int i = 0; i < scoreDocs.size(); i++) {
      int scoreFactor = i / numBatchProducers;
      Assert.assertEquals(
          1.0f - 0.5f / numDocs * scoreFactor, scoreDocs.get(i).score, scoreComparisonError);
      Assert.assertEquals(i % numBatchProducers, scoreDocs.get(i).shardIndex);
    }
  }

  @Theory
  public void testMergedIteratorOnlyFetchesLuceneOnce(ResultFactory resultFactory)
      throws Exception {
    int batchSize = 25;
    int numBatchProducers = 21;
    int numDocs = 10_000;
    List<LuceneSearchBatchProducer.SearchResultsIter> iters = new ArrayList<>();
    // All searchManagers have identical docs in them, so the merged result depends on the tie
    // breaker on index partition id.
    List<MockSearchManager> searchManagers = new ArrayList<>();
    Bytes sizeLimit = Bytes.ofMebi(16);
    var cursorOptions = BatchCursorOptionsBuilder.builder().batchSize(batchSize).build();
    for (int i = 0; i < numBatchProducers; i++) {
      var mockSearchManager = new MockSearchManager(numDocs);
      searchManagers.add(mockSearchManager);
      var producer =
          createProducer(
              mockSearchManager,
              new AdjustableBatchSizeStrategy(batchSize, false),
              resultFactory,
              i);
      producer.execute(sizeLimit, cursorOptions);
      var iter = producer.getSearchResultsIter();
      iters.add(iter);
    }
    var mergedIter = new SearchMergingBatchProducer.MergedSearchResultIter(iters, cursorOptions);
    @Var int docsReturned = 0;
    List<ScoreDoc> scoreDocs = new ArrayList<>();
    while (docsReturned < batchSize && mergedIter.peek(sizeLimit).isPresent()) {
      docsReturned++;
      scoreDocs.add(mergedIter.peek(sizeLimit).get().scoreDoc());
      mergedIter.acceptAndAdvance();
    }
    Assert.assertFalse(mergedIter.isExhausted());
    Assert.assertEquals(batchSize, docsReturned);

    float scoreComparisonError = 1e-4f;
    for (int i = 0; i < scoreDocs.size(); i++) {
      int scoreFactor = i / numBatchProducers;
      Assert.assertEquals(
          1.0f - 0.5f / numDocs * scoreFactor, scoreDocs.get(i).score, scoreComparisonError);
      Assert.assertEquals(i % numBatchProducers, scoreDocs.get(i).shardIndex);
    }

    // Verify that each searchManager only fetches from Lucene once (no prefetch.)
    for (var searchManager : searchManagers) {
      Assert.assertEquals(1, searchManager.initialSearchCount);
      Assert.assertEquals(0, searchManager.getMoreCount);
    }
  }

  private List<BsonArray> getAllBatches(
      BatchProducer batchProducer, Bytes sizeLimit, BatchCursorOptions queryCursorOptions)
      throws IOException {
    List<BsonArray> ret = new ArrayList<>();
    while (!batchProducer.isExhausted()) {
      batchProducer.execute(sizeLimit, queryCursorOptions);
      ret.add(batchProducer.getNextBatch(sizeLimit));
    }
    // After exhausted, calling an extra getNextBatch() will return empty.
    batchProducer.execute(sizeLimit, queryCursorOptions);
    Assert.assertTrue(
        batchProducer.getNextBatch(CursorConfig.DEFAULT_BSON_SIZE_SOFT_LIMIT).isEmpty());
    return ret;
  }

  private static float getScoreFromOneResult(BsonValue bsonValue) {
    BsonDocument bsonDocument = (BsonDocument) bsonValue;
    if (bsonDocument.get("$vectorSearchScore") != null) {
      return (float) bsonDocument.get("$vectorSearchScore").asDouble().getValue();
    }
    if (bsonDocument.get("$searchScore") != null) {
      return (float) bsonDocument.get("$searchScore").asDouble().getValue();
    }
    return Check.unreachable();
  }

  private static LuceneSearchBatchProducer createProducer(
      LuceneSearchManager<QueryInfo> manager,
      BatchSizeStrategy batchSizeStrategy,
      ResultFactory resultFactory,
      int indexPartitionId)
      throws IOException, InvalidQueryException {
    LuceneIndexSearcherReference searcherRef = getSearcherRef();
    QueryInfo queryInfo =
        manager.initialSearch(searcherRef, batchSizeStrategy.adviseNextBatchSize());
    // TODO(CLOUDP-280897): Add test for the isGaSort=true codepath.
    return new LuceneSearchBatchProducer(
        searcherRef,
        manager,
        queryInfo.topDocs,
        queryInfo.luceneExhausted,
        Optional.empty(),
        Optional.empty(),
        batchSizeStrategy,
        ProjectFactory.build(
            new ProjectSpec(false, StoredSourceDefinition.createIncludeAll()),
            searcherRef.getIndexSearcher().getIndexReader()),
        IndexMetricsUpdaterBuilder.QueryingMetricsUpdaterBuilder.empty(),
        QueryCursorOptions.empty(),
        false,
        Optional.empty(),
        false,
        resultFactory,
        indexPartitionId,
        false);
  }

  private static LuceneIndexSearcherReference getSearcherRef() throws IOException {
    var searcher = mock(LuceneIndexSearcher.class);
    IntFunction<Document> docGenerator =
        docId -> {
          var doc = new Document();
          var id =
              LuceneDocumentIdEncoder.encodeDocumentId(
                  new BsonDocument("_id", new BsonString(String.valueOf(docId))));
          doc.add(LuceneDocumentIdEncoder.documentIdField(id));
          return doc;
        };

    StoredFields mockStoredFields = new FakeStoredFields(docGenerator);
    IndexReader mockReader = mock(LeafReader.class);
    when(mockReader.storedFields()).thenReturn(mockStoredFields);

    LuceneIndexSearcherReference ref = mock(LuceneIndexSearcherReference.class);
    when(searcher.storedFields()).thenReturn(mockStoredFields);
    when(searcher.getIndexReader()).thenReturn(mockReader);
    when(ref.getIndexSearcher()).thenReturn(searcher);
    return ref;
  }

  private static class MockSearchManager implements LuceneSearchManager<QueryInfo> {

    private final int numDocs;
    private final float scoreDelta;
    public int initialSearchCount = 0;
    public int getMoreCount = 0;

    private MockSearchManager(int numDocs) {
      this.numDocs = numDocs;
      this.scoreDelta = 0.5f / numDocs;
    }

    private float generateDocScore(int docId) {
      float ret = 1.0f - docId * this.scoreDelta;
      Check.argIsPositive(ret, "docSore");
      return ret;
    }

    @Override
    public QueryInfo initialSearch(LuceneIndexSearcherReference searcherReference, int batchSize) {
      this.initialSearchCount++;
      ScoreDoc[] scoreDocs = new ScoreDoc[Math.min(batchSize, this.numDocs)];
      for (int i = 0; i < scoreDocs.length; i++) {
        scoreDocs[i] = new ScoreDoc(i, generateDocScore(i));
      }
      return new QueryInfo(
          new TopDocs(new TotalHits(this.numDocs, TotalHits.Relation.EQUAL_TO), scoreDocs),
          scoreDocs.length < batchSize);
    }

    @Override
    public TopDocs getMoreTopDocs(
        LuceneIndexSearcherReference searcherReference, ScoreDoc lastScoreDoc, int batchSize) {
      this.getMoreCount++;
      ScoreDoc[] scoreDocs = new ScoreDoc[Math.min(batchSize, this.numDocs - lastScoreDoc.doc - 1)];
      for (int i = lastScoreDoc.doc + 1; i < lastScoreDoc.doc + 1 + scoreDocs.length; i++) {
        scoreDocs[i - lastScoreDoc.doc - 1] = new ScoreDoc(i, generateDocScore(i));
      }
      return new TopDocs(new TotalHits(this.numDocs, TotalHits.Relation.EQUAL_TO), scoreDocs);
    }
  }
}
