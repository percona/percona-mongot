package com.xgen.mongot.index.lucene.query.custom;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.util.FieldPath;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

/**
 * A {@code WrappedQuery} is an object tying a Lucene {@link Query}, and the query path that mongot
 * Operator originated from. This class is used to wrap all Lucene queries other than the KNN and
 * ENN queries.
 *
 * <p>The goal of WrappedQuery is to associate a Lucene query with the query path that it came from,
 * so that we can display information about an Operator with that of a lucene Query in a
 * QueryExplainInformation. It is possible for the wrapped lucene query to be rewritten and if there
 * are any sub queries inside the rewritten query, that will not be wrapped. Only the top level
 * query is wrapped again.
 */
public class WrappedQuery extends Query {

  private final Query query;
  private final Optional<FieldPath> operatorPath;

  @VisibleForTesting
  public WrappedQuery(Query query) {
    this(query, Optional.empty());
  }

  public WrappedQuery(Query query, Optional<FieldPath> operatorPath) {
    this.query = query;
    this.operatorPath = operatorPath;
  }

  /**
   * Return this query as an {@code Optional<WrappedQuery>} if it is one - otherwise return an empty
   * optional.
   */
  public static Optional<WrappedQuery> asWrapped(Query query) {
    return (query instanceof WrappedQuery) ? Optional.of((WrappedQuery) query) : Optional.empty();
  }

  @Override
  public String toString(String field) {
    return "(Wrapped (" + this.query.toString(field) + ")";
  }

  @Override
  public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
      throws IOException {
    return this.query.createWeight(searcher, scoreMode, boost);
  }

  @Override
  public Query rewrite(IndexSearcher searcher) throws IOException {
    Query rewrittenQuery = this.query.rewrite(searcher);
    if (!Objects.equals(this.query, rewrittenQuery)) {
      return new WrappedQuery(rewrittenQuery, this.operatorPath);
    }

    return this;
  }

  @Override
  public void visit(QueryVisitor visitor) {
    this.query.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    WrappedQuery other = (WrappedQuery) obj;
    return Objects.equals(this.query, other.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.query);
  }

  public Query getQuery() {
    return this.query;
  }

  public Optional<FieldPath> getOperatorPath() {
    return this.operatorPath;
  }
}
