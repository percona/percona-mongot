package com.xgen.mongot.index.lucene.query.custom;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnByteVectorQuery;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

/**
 * This class wraps a KNN or an ENN Lucene query, so we can properly construct the
 * QueryExplainInformation for the KNN and ENN queries.
 */
public class WrappedKnnQuery extends Query {
  private final Query query;

  public WrappedKnnQuery(Query query) {
    this.query = query;
  }

  public Query getQuery() {
    return this.query;
  }

  @Override
  public Query rewrite(IndexSearcher indexSearcher) throws IOException {
    Query rewrittenQuery = this.query.rewrite(indexSearcher);
    if (!Objects.equals(this.query, rewrittenQuery)) {
      return new WrappedKnnQuery(rewrittenQuery);
    }

    return this;
  }

  public boolean isLuceneKnnVectorQuery() {
    return this.query instanceof KnnFloatVectorQuery || this.query instanceof KnnByteVectorQuery;
  }

  @Override
  public void visit(QueryVisitor visitor) {
    this.query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
  }

  /**
   * Return this query as an {@code Optional<WrappedKnnQuery>} if it is one - otherwise return an
   * empty optional.
   */
  public static Optional<WrappedKnnQuery> asWrapped(Query query) {
    return (query instanceof WrappedKnnQuery)
        ? Optional.of((WrappedKnnQuery) query)
        : Optional.empty();
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return this.query.createWeight(searcher, scoreMode, boost);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    WrappedKnnQuery other = (WrappedKnnQuery) obj;
    return Objects.equals(this.query, other.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.query);
  }

  @Override
  public String toString(String field) {
    return "(WrappedKnn (" + this.query.toString(field) + ")";
  }
}
