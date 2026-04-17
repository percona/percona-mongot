package com.xgen.mongot.index.lucene.explain;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.index.lucene.explain.explainers.CollectorTimingFeatureExplainer;
import com.xgen.mongot.index.lucene.explain.explainers.MetadataFeatureExplainer;
import com.xgen.mongot.index.lucene.explain.information.LuceneMetadataExplainInformation;
import com.xgen.mongot.index.lucene.explain.profiler.ProfileLeafCollector;
import com.xgen.mongot.index.lucene.explain.profiler.ProfileWeight;
import com.xgen.mongot.index.lucene.explain.profiler.QueryProfiler;
import com.xgen.mongot.index.lucene.explain.query.QueryExecutionContextNode;
import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import com.xgen.mongot.index.lucene.query.custom.WrappedKnnQuery;
import com.xgen.mongot.index.lucene.searcher.LuceneIndexSearcher;
import com.xgen.mongot.trace.Tracing;
import com.xgen.mongot.util.Check;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.CollectionTerminatedException;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.SparseFixedBitSet;

public class ProfilingIndexSearcher extends LuceneIndexSearcher {
  public final QueryProfiler queryProfiler;
  public final CollectorTimingFeatureExplainer collectorExplainer;
  private final MetadataFeatureExplainer metadataFeatureExplainer;

  @Var private boolean isLuceneKnnVectorQuery;
  @Var private int rewriteDepth;

  public ProfilingIndexSearcher(
      LuceneIndexSearcher other,
      QueryProfiler queryProfiler,
      CollectorTimingFeatureExplainer collectorExplainer,
      MetadataFeatureExplainer metadataFeatureExplainer) {
    super(other);
    this.queryProfiler = queryProfiler;
    this.collectorExplainer = collectorExplainer;
    this.metadataFeatureExplainer = metadataFeatureExplainer;
    this.isLuceneKnnVectorQuery = false;
    this.rewriteDepth = 0;
  }

  public ProfilingIndexSearcher(
      LuceneIndexSearcher other,
      QueryProfiler queryProfiler,
      CollectorTimingFeatureExplainer collectorExplainer,
      MetadataFeatureExplainer metadataFeatureExplainer,
      Executor executor) {
    super(other, executor);
    this.queryProfiler = queryProfiler;
    this.collectorExplainer = collectorExplainer;
    this.metadataFeatureExplainer = metadataFeatureExplainer;
    this.isLuceneKnnVectorQuery = false;
    this.rewriteDepth = 0;
  }

  @Override
  public Weight createWeight(Query query, ScoreMode scoreMode, float boost) throws IOException {
    // During a nested rewrite (rewriteDepth > 0), sub-queries like KNN filters may call
    // createWeight internally (e.g. AbstractKnnVectorQuery creates a filter Weight during rewrite).
    // Skip profiling for these to prevent sub-queries from accidentally becoming the explain root.
    if (this.rewriteDepth > 0) {
      return query.createWeight(this, scoreMode, boost);
    }

    Optional<? extends QueryExecutionContextNode> contextNodeIfProfiled =
        this.queryProfiler.getOrCreateNode(query);

    // If the profiler doesn't have a context node associated with this query, it was likely created
    // through a bulk scorer. ProfilingIndexSearcher tries to avoid using bulk scorers, but some
    // queries may decide to use one. Circumstances may also dictate when a bulkScorer is to be used
    // instead of a scorer - for example, some queries may opt to use a bulkScorer when a liveDocs
    // file doesn't exist (e.g. no documents have been deleted).
    if (contextNodeIfProfiled.isEmpty()) {
      return query.createWeight(this, scoreMode, boost);
    }
    QueryExecutionContextNode contextNode = contextNodeIfProfiled.get();

    Weight weight;
    ExplainTimings timings = contextNode.getTimings();
    try (var ignored = timings.split(ExplainTimings.Type.CREATE_WEIGHT)) {
      weight = query.createWeight(this, scoreMode, boost);
      Check.argNotNull(weight, "weight");
    }

    return new ProfileWeight(query, weight, timings);
  }

  @Override
  public Query rewrite(Query original) throws IOException {
    // Special explain handling for KnnFloatVectorQuery
    if (original instanceof KnnFloatVectorQuery
        || WrappedKnnQuery.asWrapped(original)
            .map(WrappedKnnQuery::isLuceneKnnVectorQuery)
            .orElse(false)) {
      this.isLuceneKnnVectorQuery = true;
    }

    return rewrite(original, this.isLuceneKnnVectorQuery);
  }

  Query rewrite(Query original, boolean maybeLuceneVectorQuery) throws IOException {
    if (maybeLuceneVectorQuery) {
      return luceneVectorQueryRewrite(original);
    }

    // Track rewrite depth so that only the top-level rewrite updates the profiler tree.
    // super.rewrite() runs an iterative loop that may trigger nested calls back into this method
    // (e.g. when a KNN query's filter is rewritten by AbstractKnnVectorQuery). Those nested
    // replaceNode calls must be suppressed to prevent sub-queries from accidentally becoming the
    // explain tree root.
    this.rewriteDepth++;
    Query rewritten;
    try {
      rewritten = super.rewrite(original);
    } finally {
      this.rewriteDepth--;
    }
    Check.argNotNull(rewritten, "rewritten query");

    // Replace the original query's subtree with a new one for the rewritten query. The profiler
    // will handle special cases (e.g. if original == rewritten, original is the root node, etc.).
    if (this.rewriteDepth == 0) {
      this.queryProfiler.replaceNode(original, rewritten);
    }

    return rewritten;
  }

  private Query luceneVectorQueryRewrite(Query original) throws IOException {
    // create root Node first for vectorSearch in rewrite so filters can connect to root node upon
    // rewrite
    var contextNode = this.queryProfiler.getOrCreateNode(original);

    Query rewritten;
    if (original instanceof BooleanQuery) {
      // In a vector query, a boolean query always represents the filter, which is not visited prior
      // to its rewrite. Connect it to the root node as a child filter node
      Check.isEmpty(contextNode, "contextNode");
      rewritten = super.rewrite(original);
      this.queryProfiler.addVectorFilterNode(rewritten);
      return rewritten;
    }

    if (contextNode.isEmpty()) {
      // This occurs for a rewrite into some nested complex filters, where a node does not
      // get visited prior to its rewrite
      return rewrite(original, false);
    }
    ExplainTimings timings = contextNode.get().getTimings();
    // rewrite is timed for vector queries only - vector queries do the majority of its execution
    // during the `rewrite` phase
    try (var ignored = timings.split(ExplainTimings.Type.VECTOR_EXECUTION)) {
      rewritten = super.rewrite(original);
    }

    Check.argNotNull(rewritten, "rewritten query");
    this.queryProfiler.addVectorMustNode(original, rewritten);

    return rewritten;
  }

  @Override
  public <C extends Collector, T> T search(Query query, CollectorManager<C, T> collectorManager)
      throws IOException {
    this.queryProfiler.createQueryExecutionContext();
    recordLuceneMetadata();
    return super.search(query, collectorManager);
  }

  @Override
  @Deprecated
  public void search(Query query, Collector results) throws IOException {
    this.queryProfiler.createQueryExecutionContext();
    recordLuceneMetadata();
    super.search(query, results);
  }

  @Override
  protected void searchLeaf(
      LeafReaderContext ctx, int minDocId, int maxDocId, Weight weight, Collector collector)
      throws IOException {
    try (var guard = Tracing.simpleSpanGuard("ProfilingIndexSearcher.searchLeaf")) {
      searchLeaf(
          ctx,
          minDocId,
          maxDocId,
          weight,
          collector,
          this.collectorExplainer.getAllCollectorTimings());
    }
  }

  private void searchLeaf(
      LeafReaderContext ctx,
      int minDocId,
      int maxDocId,
      Weight weight,
      Collector collector,
      ExplainTimings explainTimings)
      throws IOException {

    try (var guard = Tracing.simpleSpanGuard("searchLeaf")) {
      LeafCollector leafCollector;
      try {
        leafCollector = new ProfileLeafCollector(collector.getLeafCollector(ctx), explainTimings);
      } catch (CollectionTerminatedException e) {
        // This LeafCollector found no matches; continue with the next leaf.
        return;
      }

      // If we can't get the BitSet for this LeafReader's LiveDocs, try to use a bulk scorer. Using
      // a
      // bulk scorer won't profile this part of the query.
      Optional<Bits> liveDocs = Optional.ofNullable(ctx.reader().getLiveDocs());
      if (liveDocs.isEmpty()) {
        Optional<BulkScorer> bulkScorer = Optional.ofNullable(weight.bulkScorer(ctx));

        if (bulkScorer.isPresent()) {
          try {
            // Second arg is "acceptDocs", which is null because liveDocs is empty.
            bulkScorer.get().score(leafCollector, null, minDocId, maxDocId);
          } catch (CollectionTerminatedException e) {
            // Collection terminated, continue with next leaf.
          }
        }
        // Note: this is called if collection ran successfully, including the above special cases of
        // CollectionTerminatedException, but no other exception.
        leafCollector.finish();
        return;
      }

      BitSet liveDocsBitSet = bitSetFromBits(liveDocs.get());
      // This Scorer instance is instrumented for profiling.
      Scorer scorer = weight.scorer(ctx);

      if (scorer != null) {
        try {
          // Find the intersection of live docs and documents to iterate over from the scorer.
          leafCollector.setScorer(scorer);
          var liveDocsIterator =
              new BitSetIterator(liveDocsBitSet, liveDocsBitSet.approximateCardinality());
          var scorerIterator = scorer.iterator();
          // TODO(CLOUDP-266184): Intersect the competitiveIterator that comes out of the
          // leafCollector here.
          var docIdSetIterator =
              ConjunctionUtils.intersectIterators(Arrays.asList(liveDocsIterator, scorerIterator));

          // These docIds are those of result documents; they were present in both the live docs
          // bitset
          // as well as the scorer iterator.
          for (int docId = docIdSetIterator.nextDoc();
              docId < DocIdSetIterator.NO_MORE_DOCS;
              docId = docIdSetIterator.nextDoc()) {
            leafCollector.collect(docId);
          }

        } catch (CollectionTerminatedException e) {
          // Collection terminated, continue with next leaf.
        }
      }
      // Note: this is called if collection ran successfully, including the above special cases of
      // CollectionTerminatedException, but no other exception.
      leafCollector.finish();
    }
  }

  private static BitSet bitSetFromBits(Bits liveDocs) {
    if (liveDocs instanceof SparseFixedBitSet || liveDocs instanceof FixedBitSet) {
      return (BitSet) liveDocs;
    }

    return FixedBitSet.copyOf(liveDocs);
  }

  private void recordLuceneMetadata() {
    int totalSegments = this.getIndexReader().leaves().size();
    int totalDocs = this.getIndexReader().maxDoc();
    this.metadataFeatureExplainer.setLucene(
        new LuceneMetadataExplainInformation(Optional.of(totalSegments), Optional.of(totalDocs)));
  }
}
