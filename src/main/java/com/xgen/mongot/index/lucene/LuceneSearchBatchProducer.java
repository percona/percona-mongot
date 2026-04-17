package com.xgen.mongot.index.lucene;

import static com.xgen.mongot.index.lucene.Comparators.RELEVANCE_COMPARATOR;
import static com.xgen.mongot.util.Check.checkState;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.cursor.batch.BatchCursorOptions;
import com.xgen.mongot.cursor.batch.BatchSizeStrategy;
import com.xgen.mongot.cursor.batch.QueryCursorOptions;
import com.xgen.mongot.index.BatchProducer;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.ResultFactory;
import com.xgen.mongot.index.ScoreDetails;
import com.xgen.mongot.index.SearchHighlight;
import com.xgen.mongot.index.SearchSortValues;
import com.xgen.mongot.index.lucene.LuceneSearchManager.QueryInfo;
import com.xgen.mongot.index.lucene.explain.explainers.ResultMaterializationFeatureExplainer;
import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.mongot.index.lucene.explain.tracing.ExplainQueryState;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.lucene.query.pushdown.project.ProjectFactory;
import com.xgen.mongot.index.lucene.query.pushdown.project.ProjectStage;
import com.xgen.mongot.index.lucene.query.util.MetaIdRetriever;
import com.xgen.mongot.index.query.sort.SequenceToken;
import com.xgen.mongot.trace.Tracing;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.BsonArrayBuilder;
import com.xgen.mongot.util.timers.InvocationCountingTimer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;

/**
 * Not thread safe implementation of {@link BatchProducer}. All access to public methods must be
 * synchronized and batches should be requested sequentially. Responsible for one index partition.
 */
class LuceneSearchBatchProducer implements BatchProducer {

  private static final FluentLogger FLOGGER = FluentLogger.forEnclosingClass();

  private enum State {
    INITIALIZED,
    GET_MORE,
    CLOSED,
  }

  private record InitInfo(TopDocs topDocs, boolean luceneExhausted) {}

  private final InitInfo initInfo;
  private final BatchSizeStrategy batchSizeStrategy;

  private final LuceneIndexSearcherReference searcherReference;
  private final Optional<LuceneUnifiedHighlighter> unifiedHighlighter;
  private final Optional<LuceneScoreDetailsManager> scoreDetailsManager;
  private final ProjectFactory projectFactory;
  private final IndexMetricsUpdater.QueryingMetricsUpdater metricsUpdater;
  private final LuceneSearchManager<? extends QueryInfo> searchManager;
  private final ResultFactory resultFactory;

  // The ID of the index partition, starting from 0. Used for tie breaking during merging.
  private final int indexPartitionId;

  private final SortValuesProvider sortProvider;

  /**
   * Cursor Options for the first batch of a query. Used for oversubscription metric calculations.
   * More information here: https://tinyurl.com/y5b649a8
   */
  private final QueryCursorOptions firstBatchCursorOptions;

  private final Optional<SequenceToken> sequenceToken;

  /** True if we have not reported oversubscription metrics for this query yet. */
  private boolean shouldReportOversubsribptionMetrics = true;

  private int firstBatchDocsReturned;
  private boolean firstBatchExceeds;

  /**
   * True until this BatchProducer processes the first record, which could potentially be skipped.
   * More details: CLOUDP-245829
   */
  private boolean firstHit;

  /**
   * True until this BatchProducer processes the first batch. It is used to ignore the first batch
   * in the duplicate batch tracking logic.
   */
  private boolean firstBatch;

  // All the Lucene hit results have been consumed, and this.lastScoreDoc points to the last hit
  // doc for the query. I.e. If we run another Lucene's
  // indexSearcher.searchAfter(this.lastScoreDoc),
  // or LuceneSearchBatchProducer.getNextBatch(), it will return empty result.
  private boolean exhausted;

  private State state;
  private Optional<Bytes> avgDocSize;
  private Optional<IterValue> lastIterValue;
  private Optional<SearchResultsIter> searchResultsIter;
  private final boolean isReturnScopePresent;
  private final boolean hasIndexSort;

  public LuceneSearchBatchProducer(
      LuceneIndexSearcherReference searcherReference,
      LuceneSearchManager<? extends QueryInfo> searchManager,
      TopDocs initDocs,
      boolean luceneExhausted,
      Optional<LuceneUnifiedHighlighter> unifiedHighlighter,
      Optional<LuceneScoreDetailsManager> scoreDetailsManager,
      BatchSizeStrategy batchSizeStrategy,
      ProjectFactory projectFactory,
      IndexMetricsUpdater.QueryingMetricsUpdater metricsUpdater,
      QueryCursorOptions firstBatchCursorOptions,
      boolean isGaSort,
      Optional<SequenceToken> sequenceToken,
      boolean isReturnScopePresent,
      ResultFactory resultFactory,
      int indexPartitionId,
      boolean hasIndexSort) {
    this.searcherReference = searcherReference;
    this.unifiedHighlighter = unifiedHighlighter;
    this.scoreDetailsManager = scoreDetailsManager;
    this.batchSizeStrategy = batchSizeStrategy;
    this.projectFactory = projectFactory;
    this.metricsUpdater = metricsUpdater;
    this.searchManager = searchManager;
    this.sortProvider =
        isGaSort
            ? new SortValuesProvider.RealSortValuesProvider()
            : new SortValuesProvider.NoopSortValuesProvider();
    this.firstBatchCursorOptions = firstBatchCursorOptions;

    this.exhausted = (initDocs.scoreDocs.length == 0);
    this.firstBatchExceeds = false;
    this.firstHit = true;
    this.firstBatch = true;
    this.initInfo = new InitInfo(initDocs, luceneExhausted);
    this.lastIterValue = Optional.empty();
    this.avgDocSize = Optional.empty();
    this.state = State.INITIALIZED;
    this.resultFactory = resultFactory;
    this.sequenceToken = sequenceToken;
    this.isReturnScopePresent = isReturnScopePresent;
    this.indexPartitionId = indexPartitionId;
    this.hasIndexSort = hasIndexSort;
    this.searchResultsIter = Optional.empty();
  }

  // Fetches the entire next batch, and returns the beginning iterator to the batch.
  @Override
  @SuppressWarnings({"removal"})
  public final void execute(Bytes sizeLimitInBytes, BatchCursorOptions queryCursorOptions)
      throws IOException {
    checkState(this.state != State.CLOSED, "cannot call getNextBatch() after close()");

    TopDocs topDocs;
    // Whether the last Lucene search that returned the topDocs has exhausted the hit results.
    boolean luceneExhausted;
    int batchSize;
    switch (this.state) {
      case INITIALIZED -> {
        // We have been initialized with metaResults and cachedInitialDocs already.  Use the
        // cached docs for topDocs, clear the cached docs, and moved to GET_MORE state.
        topDocs = this.initInfo.topDocs;
        luceneExhausted = this.initInfo.luceneExhausted;
        this.state = State.GET_MORE;
        // if cursor options are populated, calculate oversubscription metrics
        this.firstBatchCursorOptions
            .getDocsRequested()
            .ifPresent(unused -> this.metricsUpdater.getExtractableLimitQueryCounter().increment());
      }
      case GET_MORE -> {
        // When the docsRequested field is present, re-create a new batchSizeStrategy for this
        // getMore query. Otherwise, fallback to the previous available batchSizeStrategy. The
        // batchSizeStrategy in the ctor arg is for the first batch.
        if (queryCursorOptions.getDocsRequested().isPresent()) {
          reportOversubscriptionMetrics(queryCursorOptions);
        }
        checkState(
            this.lastIterValue.isPresent(), "lastIterValue should be present in state GET_MORE");

        batchSize = batchSize(sizeLimitInBytes);
        topDocs =
            this.searchManager.getMoreTopDocs(
                this.searcherReference, this.lastIterValue.get().scoreDoc, batchSize);
        luceneExhausted = isLuceneExhausted(topDocs, batchSize);
      }
      default -> throw Check.unreachableError();
    }

    this.searchResultsIter =
        Optional.of(
            new SearchResultsIter(
                this.searcherReference.getIndexSearcher(), topDocs, luceneExhausted));
  }

  @Override
  public final BsonArray getNextBatch(Bytes sizeLimit) throws IOException {
    if (this.searchResultsIter.isEmpty()) {
      throw new IllegalStateException(
          "this.searchResultsIter is null. You must call execute() right before this "
              + "getNextBatch().");
    }

    Optional<ResultMaterializationFeatureExplainer> resultMaterializationExplainer =
        Explain.getExplainQueryState()
            .map(ExplainQueryState::getRootQueryInfo)
            .map(
                queryInfo ->
                    queryInfo.getFeatureExplainer(
                        ResultMaterializationFeatureExplainer.class,
                        ResultMaterializationFeatureExplainer::new));

    try (var unused =
        new InvocationCountingTimer.AutocloseableOptional<>(
            resultMaterializationExplainer
                .map(ResultMaterializationFeatureExplainer::getTimings)
                .map(t -> t.split(ExplainTimings.Type.RETRIEVE_AND_SERIALIZE)))) {
      return getSearchResultsFromIter(sizeLimit, this.searchResultsIter.get());
    }
  }

  @SuppressWarnings({"removal"})
  private void reportOversubscriptionMetrics(BatchCursorOptions queryCursorOptions) {
    if (!this.shouldReportOversubsribptionMetrics) {
      return;
    }
    // do not report oversubscription metrics if:
    // 1. first batch exceeded 16MB
    // 2. currently not the 2nd batch
    // 3. 2nd batch does not request a specific number of documents (violates batch protocol)
    if (this.firstBatchExceeds
        || this.firstBatchCursorOptions.getDocsRequested().isEmpty()
        || queryCursorOptions.getDocsRequested().isEmpty()) {
      return;
    }

    this.metricsUpdater.getExtractableLimitQuerySecondBatchCounter().increment();
    // Number of documents from first batch that are unusable is calculated as below:
    // docs returned in first batch + docs still needed after first batch - total docs requested
    int numFirstBatchUnusableDocs =
        this.firstBatchDocsReturned
            + queryCursorOptions.getDocsRequested().get()
            - this.firstBatchCursorOptions.getDocsRequested().get();
    this.metricsUpdater
        .getOrphanedDeletedDocsRatio()
        .record((double) numFirstBatchUnusableDocs / this.firstBatchDocsReturned);
    // Do not track oversubscription metrics after the first getMore
    // since we already have an estimate of orphan/delete proportion
    this.shouldReportOversubsribptionMetrics = false;
  }

  @Override
  public final boolean isExhausted() {
    return this.exhausted;
  }

  public BatchSizeStrategy getBatchSizeStrategy() {
    return this.batchSizeStrategy;
  }

  public int getIndexPartitionId() {
    return this.indexPartitionId;
  }

  public SearchResultsIter getSearchResultsIter() {
    return Check.isPresent(this.searchResultsIter, "searchResultIter");
  }

  @Override
  public void close() throws IOException {
    if (this.state == State.CLOSED) {
      return;
    }

    this.searcherReference.close();
    this.state = State.CLOSED;
  }

  @SuppressWarnings({"removal"})
  private BsonArray getSearchResultsFromIter(Bytes sizeLimit, SearchResultsIter iter)
      throws IOException {
    try (var sp = Tracing.simpleSpanGuard("LuceneSearchBatchProducer.getSearchResults")) {
      var previousBatchLastValue = this.lastIterValue;
      var builder = BsonArrayBuilder.withLimit(sizeLimit);
      @Var boolean tieDetected = false;
      @Var Optional<IterValue> currentBatchLastValue = Optional.empty();
      while (iter.peek().isPresent()) {
        IterValue currentValue = iter.peek().get();
        if (!builder.append(currentValue.bsonDoc)) {
          break;
        }

        if (currentBatchLastValue.isPresent() && !tieDetected) {
          tieDetected = detectTies(currentValue, currentBatchLastValue.get());
        }
        currentBatchLastValue = Optional.of(currentValue);

        iter.acceptAndAdvance();
      }

      if (tieDetected) {
        this.metricsUpdater.getBatchWithTiesCounter().increment();
      }

      if (this.firstBatch) {
        this.firstBatch = false;
      } else {

        // check for duplicated / no progress batches only starting from the second batch
        boolean batchOrderIsWrong =
            batchOrderIsWrong(iter.topDocs, iter.firstValue, previousBatchLastValue);
        boolean batchIsDuplicated =
            batchIsDuplicated(previousBatchLastValue, currentBatchLastValue);
        if (batchOrderIsWrong || batchIsDuplicated) {
          this.metricsUpdater.getNoProgressBatchCounter().increment();
          FLOGGER.atWarning().atMostEvery(1, TimeUnit.MINUTES).log(
              "The batch has failed to make progress. "
                  + "Batch order is wrong == %s, batch is duplicated == %s",
              batchOrderIsWrong, batchIsDuplicated);
        }
      }

      TopDocs topDocs = iter.topDocs;
      // TODO(CLOUDP-280897): This is not called if there are more than one index partitions. Fix
      // these metrics.
      this.metricsUpdater.getBatchDocumentCount().record(builder.getDocumentCount());
      this.metricsUpdater.getBatchDataSize().record(builder.getDataSize().toBytes());
      this.metricsUpdater.getTotalHitsLowerBoundCount().increment(topDocs.totalHits.value());

      if (builder.getDocumentCount() > 0) {
        this.avgDocSize =
            Optional.of(
                Bytes.ofBytes(builder.getDataSize().toBytes() / builder.getDocumentCount()));
      }

      BsonArray results = builder.build();

      boolean allDocsFitInTheBatch = allDocsFitInTheBatch(topDocs, results, iter.skippedHead);

      if (this.firstBatchCursorOptions.getDocsRequested().isPresent()) {
        this.firstBatchExceeds = !allDocsFitInTheBatch;
        this.firstBatchDocsReturned = results.size();
      }

      checkState(
          topDocs.scoreDocs.length == 0
              || !results.isEmpty()
              // results could be empty because we skipped head to fix duplicate issue
              // (see: CLOUDP-245829)
              || (topDocs.scoreDocs.length == 1 && iter.skippedHead),
          "Search result output exceeds BSON size limit");

      FLOGGER.atFine().log("Prepared %s search results in %s", results.size(), sp.getSpan());
      sp.getSpan().setAttribute("num search results", results.size());
      return results;
    }
  }

  private static boolean batchIsDuplicated(
      Optional<IterValue> lastDocFromPreviousBatch, Optional<IterValue> lastDocFromCurrentBatch) {
    int previousBatchLastDoc = lastDocFromPreviousBatch.map(s -> s.scoreDoc.doc).orElse(-1);
    int currentBatchLastDoc = lastDocFromCurrentBatch.map(s -> s.scoreDoc.doc).orElse(-1);

    return previousBatchLastDoc == currentBatchLastDoc;
  }

  /**
   * Checks the first document of the current batch against the last document of the previous batch
   * to ensure they are in the correct order.
   */
  private static boolean batchOrderIsWrong(
      TopDocs topDocs,
      Optional<IterValue> currentBatchStart,
      Optional<IterValue> previousBatchEnd) {
    if (previousBatchEnd.isEmpty() || currentBatchStart.isEmpty()) {
      return false;
    }

    IterValue previousBatchEndVal = previousBatchEnd.get();
    Comparator<IterValue> comparator;
    if (previousBatchEndVal.sortValues.isPresent()) {
      if (previousBatchEndVal.scoreDoc instanceof FieldDoc) {
        comparator = new Comparators.CustomSortComparator(((TopFieldDocs) topDocs).fields);
      } else {
        FLOGGER.atWarning().atMostEvery(1, TimeUnit.MINUTES).log(
            "ScoreDoc must be an instance of FieldDoc for sort. "
                + "Falling back to the relevance comparator.");
        comparator = RELEVANCE_COMPARATOR;
      }
    } else {
      comparator = RELEVANCE_COMPARATOR;
    }

    return comparator.compare(previousBatchEndVal, currentBatchStart.get()) >= 0;
  }

  private boolean detectTies(IterValue current, IterValue previous) {
    // Also not a pagination query
    if (this.sequenceToken.isEmpty() && !this.firstBatchCursorOptions.requireSequenceTokens()) {
      return false;
    }
    checkState(
        current.sortValues.isEmpty() == previous.sortValues.isEmpty(),
        "One of the sortValues is empty while another is not.");

    // pagination query without sort clause
    if (current.sortValues.isEmpty() && previous.sortValues.isEmpty()) {
      return current.scoreDoc.score == previous.scoreDoc.score;
    }

    var currentSortValues = current.sortValues.get();
    var previousSortValues = previous.sortValues.get();
    checkState(
        currentSortValues.size() == previousSortValues.size(), "Sort values have different size");

    // reverse, because its better chance to exit earlier
    return previousSortValues.reversed().equals(currentSortValues.reversed());
  }

  private static boolean isLuceneExhausted(TopDocs topDocs, int batchSize) {
    return topDocs.scoreDocs.length < batchSize;
  }

  private boolean allDocsFitInTheBatch(TopDocs topDocs, BsonArray results, boolean skippedHead) {
    return (results.size() == topDocs.scoreDocs.length)
        // account for the case, when we dropped head to fix duplicate record. see: CLOUDP-245829
        || (results.size() == topDocs.scoreDocs.length - 1 && skippedHead);
  }

  /**
   * Returns docsLimit using the current {@link BatchSizeStrategy}. Clamp the sizeLimit to the
   * maximum batch size calculated using the avg document size from previous batches.
   */
  private int batchSize(Bytes sizeLimit) {

    if (this.avgDocSize.isEmpty() || this.avgDocSize.get().toBytes() == 0) {
      return this.batchSizeStrategy.adviseNextBatchSize();
    }

    long maxBatchSize =
        Math.min(sizeLimit.toBytes() / this.avgDocSize.get().toBytes(), Integer.MAX_VALUE);

    return Math.min(this.batchSizeStrategy.adviseNextBatchSize(), Math.toIntExact(maxBatchSize));
  }

  /**
   * The single value that the {@link SearchResultsIter} returns. It contains information for a
   * single doc with hydrated information.
   */
  public record IterValue(
      ScoreDoc scoreDoc, Optional<List<BsonValue>> sortValues, RawBsonDocument bsonDoc) {}

  /**
   * This iterator can scan through the batch results by every scoreDoc. It handles the {@code
   * lastIterValue} automatically. Typical usage: while (iter.peek().isPresent()) { // Do something
   * with the iter value. iter.acceptAndAdvance(); }
   */
  public class SearchResultsIter {
    private final TopDocs topDocs;
    private final ScoreDoc[] scoreDocs;
    private final Optional<List<List<SearchHighlight>>> searchHighlightsDocs;
    private final Optional<List<ScoreDetails>> scoreDetailsAllDocs;
    // Whether the last Lucene search that returned topDocs has exhausted the hit results.
    private final boolean luceneExhausted;
    private final ProjectStage projectStage;
    private final MetaIdRetriever metaIdRetriever;
    private final ImmutableSet<Integer> nullnessFieldIndexes;
    private int documentIndex; // The index of the current value in the scoreDocs array.
    private Optional<IterValue> firstValue; // first value processed by the current iterator
    private Optional<IterValue> currentValue;
    private boolean skippedHead;

    SearchResultsIter(IndexSearcher searcher, TopDocs topDocs, boolean luceneExhausted)
        throws IOException {
      this.topDocs = topDocs;
      this.scoreDocs = topDocs.scoreDocs;
      this.luceneExhausted = luceneExhausted;
      this.nullnessFieldIndexes =
          LuceneSearchBatchProducer.this.hasIndexSort
              ? computeNullnessFieldIndexes(topDocs)
              : ImmutableSet.of();
      // If there are highlights called for in the query, get them.
      this.searchHighlightsDocs =
          LuceneSearchBatchProducer.this.unifiedHighlighter.isPresent()
              ? Optional.of(
                  LuceneSearchBatchProducer.this
                      .unifiedHighlighter
                      .get()
                      .highlightsAsSearchHighlightsArray(topDocs))
              : Optional.empty();
      this.projectStage = LuceneSearchBatchProducer.this.projectFactory.create(topDocs.scoreDocs);
      this.scoreDetailsAllDocs =
          LuceneSearchBatchProducer.this.scoreDetailsManager.map(
              scoreDetailsManager -> scoreDetailsManager.getScoreDetails(searcher, topDocs));
      this.documentIndex = 0;

      this.firstValue = Optional.empty();
      this.currentValue = Optional.empty();
      this.metaIdRetriever =
          MetaIdRetriever.create(
              LuceneSearchBatchProducer.this.searcherReference.getIndexSearcher().getIndexReader());
      updateExhausted();
      maybeUpdate();
    }

    public Optional<SortField[]> sortFields() {
      return LuceneSearchBatchProducer.this.sortProvider.sortFields(this.topDocs);
    }

    // If the currentValue is present, do nothing. Otherwise, hydrate the current doc and populate
    // it into currentValue.
    private void maybeUpdate() throws IOException {
      if (this.currentValue.isPresent()) {
        return;
      }
      int index = this.documentIndex;
      if (index < 0 || index >= this.scoreDocs.length) {
        // Index out of bound, leave currentValue as empty.
        return;
      }
      ScoreDoc scoreDoc = this.scoreDocs[index];
      // Hydrate the scoreDoc.
      // Used for tie breaking during query result merging for index partitions.
      scoreDoc.shardIndex = LuceneSearchBatchProducer.this.indexPartitionId;
      Optional<List<SearchHighlight>> highlights =
          this.searchHighlightsDocs.map(lists -> lists.get(index));

      int docId = scoreDoc.doc;

      @Var Optional<BsonValue> fetchedMetaId = Optional.empty();
      @Var Optional<SequenceToken> token = Optional.empty();
      if (LuceneSearchBatchProducer.this.firstBatchCursorOptions.requireSequenceTokens()) {
        fetchedMetaId = Optional.of(this.metaIdRetriever.getRootMetaId(docId));
        token = Optional.of(SequenceToken.of(fetchedMetaId.get(), scoreDoc));
      }

      /*
        Invariant: exactly one of (projectStage.project or projectStage.getId) will be present
        In case 'projectStage.project' is empty, 'projectStage.getId has to be present,
        but we try to reuse _id, which possibly was fetched before
      */
      Optional<BsonDocument> storedSource = this.projectStage.project(docId);

      @Var Optional<BsonValue> id = Optional.empty();
      if (storedSource.isEmpty()) {
        if (fetchedMetaId.isEmpty()) {
          fetchedMetaId = this.projectStage.getId(docId);
        }
        id = fetchedMetaId;
      }

      @Var Optional<BsonValue> rootDocumentid = Optional.empty();
      if (storedSource.isPresent() && LuceneSearchBatchProducer.this.isReturnScopePresent) {
        rootDocumentid = Optional.of(this.metaIdRetriever.getRootMetaId(docId));
      }

      Optional<List<BsonValue>> sortValues =
          LuceneSearchBatchProducer.this.sortProvider.sortValues(scoreDoc);
      Optional<ScoreDetails> scoreDetailsForDoc = this.scoreDetailsAllDocs.map(sd -> sd.get(index));
      Optional<List<BsonValue>> sortValuesForMongos = stripNullnessSortValues(sortValues);
      this.currentValue =
          Optional.of(
              new IterValue(
                  scoreDoc,
                  sortValues,
                  LuceneSearchBatchProducer.this
                      .resultFactory
                      .create(
                          id,
                          scoreDoc.score,
                          highlights,
                          scoreDetailsForDoc,
                          storedSource,
                          sortValuesForMongos.map(SearchSortValues::create),
                          token,
                          rootDocumentid)
                      .toRawBson()));
      // We need to explicitly check the first result, because it might be a duplicate.
      // see: CLOUDP-245829
      if (LuceneSearchBatchProducer.this.firstHit) {
        LuceneSearchBatchProducer.this.firstHit = false;
        Optional<SequenceToken> sequenceToken = LuceneSearchBatchProducer.this.sequenceToken;
        if (sequenceToken.isPresent() && sequenceToken.get().id().isPresent()) {
          BsonValue metaId =
              fetchedMetaId.isPresent()
                  ? fetchedMetaId.get()
                  : this.metaIdRetriever.getRootMetaId(docId);

          // Skip the first result because its _id matches the one in the SequenceToken,
          // thus it is a duplicate.
          if (metaId.equals(sequenceToken.get().id().get())) {
            this.skippedHead = true;
            acceptAndAdvanceWithoutCheck();
          }
        }
      }
    }

    // If the returned value is empty, it means the current iterator position is out of bound and
    // this iterator is exhausted.
    public Optional<IterValue> peek() {
      return this.currentValue;
    }

    public LuceneSearchBatchProducer parentBatchProducer() {
      return LuceneSearchBatchProducer.this;
    }

    // Calling this means the current document has been consumed, therefore, the lastScoreDoc
    // is updated to current document. Precondition: this.peek().isPresent must be true.
    public void acceptAndAdvance() throws IOException {
      if (this.peek().isEmpty()) {
        throw new IllegalStateException("The iterator has been exhausted.");
      }
      acceptAndAdvanceWithoutCheck();
    }

    private void updateExhausted() {
      LuceneSearchBatchProducer.this.exhausted =
          (this.documentIndex >= this.scoreDocs.length) && this.luceneExhausted;
    }

    private void acceptAndAdvanceWithoutCheck() throws IOException {
      LuceneSearchBatchProducer.this.lastIterValue = this.currentValue;
      if (this.firstValue.isEmpty()) {
        this.firstValue = this.currentValue;
      }
      this.currentValue = Optional.empty();
      this.documentIndex++;

      updateExhausted();
      maybeUpdate();
    }

    private static ImmutableSet<Integer> computeNullnessFieldIndexes(TopDocs topDocs) {
      if (!(topDocs instanceof TopFieldDocs topFieldDocs)) {
        return ImmutableSet.of();
      }
      ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
      for (int i = 0; i < topFieldDocs.fields.length; i++) {
        if (FieldName.isNullnessFieldName(topFieldDocs.fields[i].getField())) {
          builder.add(i);
        }
      }
      return builder.build();
    }

    /**
     * Strips nullness sort values from the list so that {@code $searchSortValues} keys align with
     * the non-expanded sort fields used by {@link
     * com.xgen.mongot.server.command.search.ShardedSearchPlanner#constructSortSpec}.
     */
    private Optional<List<BsonValue>> stripNullnessSortValues(
        Optional<List<BsonValue>> sortValues) {
      if (this.nullnessFieldIndexes.isEmpty() || sortValues.isEmpty()) {
        return sortValues;
      }
      List<BsonValue> all = sortValues.get();
      List<BsonValue> filtered = new ArrayList<>(all.size() - this.nullnessFieldIndexes.size());
      for (int i = 0; i < all.size(); i++) {
        if (!this.nullnessFieldIndexes.contains(i)) {
          filtered.add(all.get(i));
        }
      }
      return Optional.of(filtered);
    }
  }
}
