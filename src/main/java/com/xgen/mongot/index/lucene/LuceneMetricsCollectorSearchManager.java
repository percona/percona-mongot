package com.xgen.mongot.index.lucene;

import com.xgen.mongot.index.lucene.searcher.LuceneIndexSearcher;
import com.xgen.mongot.index.query.sort.SequenceToken;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.search.MultiCollectorManager;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;

/**
 * The LuceneMetricsCollectorSearchManager manages the execution of a single lucene search for a
 * {@link com.xgen.mongot.index.query.collectors.MetricsCollector} query. Like {@link
 * LuceneFacetCollectorSearchManager} it collects the top docs of the first batch alongside the
 * metric accumulators, with an exact total hit count.
 */
class LuceneMetricsCollectorSearchManager
    extends AbstractLuceneSearchManager<
        LuceneMetricsCollectorSearchManager.MetricsCollectorQueryInfo> {

  public static class MetricsCollectorQueryInfo extends QueryInfo {
    /** Keyed by Lucene field name. */
    public final Map<String, NumericMetricAccumulator> accumulatorsByLuceneField;

    public MetricsCollectorQueryInfo(
        TopDocs topDocs,
        Map<String, NumericMetricAccumulator> accumulatorsByLuceneField,
        boolean luceneExhausted) {
      super(topDocs, luceneExhausted);
      this.accumulatorsByLuceneField = accumulatorsByLuceneField;
    }
  }

  private final Collection<String> luceneFields;

  public LuceneMetricsCollectorSearchManager(
      Query luceneQuery,
      Optional<Sort> luceneSort,
      Optional<SequenceToken> searchAfter,
      Collection<String> luceneFields) {
    super(luceneQuery, luceneSort, searchAfter);
    this.luceneFields = luceneFields;
  }

  @Override
  public MetricsCollectorQueryInfo initialSearch(
      LuceneIndexSearcherReference searcherReference, int batchSize) throws IOException {

    LuceneIndexSearcher searcher = searcherReference.getIndexSearcher();

    var readerLimit = Math.max(1, searcher.getIndexReader().maxDoc());
    var searchLimit = Math.min(readerLimit, batchSize);

    // Integer.MAX_VALUE hit threshold so totalHits is exact, as the count result requires.
    var searchCollectorManager = createCollectorManager(searchLimit, Integer.MAX_VALUE);

    var results =
        searcher.search(
            this.getLuceneQuery(),
            new MultiCollectorManager(
                new LuceneMetricsCollectorManager(this.luceneFields), searchCollectorManager));

    // MultiCollectorManager reduces to Object[]; the generic Map type cannot be verified at
    // runtime.
    @SuppressWarnings("unchecked")
    var accumulators = (Map<String, NumericMetricAccumulator>) results[0];
    var topDocs = (TopDocs) results[1];

    maybePopulateScores(searcher, topDocs.scoreDocs);
    return new MetricsCollectorQueryInfo(
        topDocs, accumulators, topDocs.scoreDocs.length < batchSize);
  }
}
