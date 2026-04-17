package com.xgen.mongot.index.lucene.query.weights;

import java.io.IOException;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EmptyWeight} provides a {@link Weight#explain(LeafReaderContext, int)} implementation
 * placeholder for queries that do not have {@link Query#createWeight(IndexSearcher, ScoreMode,
 * float)} implemented.
 */
class EmptyWeight extends Weight {

  EmptyWeight(Query query) {
    super(query);
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    String description =
        String.format(
            "query %s may or may not have matched this document, but an explanation could not be"
                + " generated over it",
            this.parentQuery.toString());
    return Explanation.match(Float.NaN, description);
  }

  @Override
  @Nullable
  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
    return null;
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    return false;
  }
}
