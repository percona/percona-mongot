package com.xgen.mongot.cursor;

import com.xgen.mongot.index.query.MaterializedVectorSearchQuery;
import com.xgen.mongot.index.query.Query;
import com.xgen.mongot.index.query.QueryExecutionContext;

/**
 * A sealed wrapper that carries a query through the cursor creation pipeline, abstracting over the
 * difference between a search {@link Query} and a {@link MaterializedVectorSearchQuery}. This
 * avoids duplicating cursor creation methods for each query type.
 */
public sealed interface CursorQuery {

  /** Returns the underlying {@link Query} for use in stats tracking, index routing, etc. */
  Query getQuery();

  record Search(Query query) implements CursorQuery {
    @Override
    public Query getQuery() {
      return this.query;
    }
  }

  record Vector(MaterializedVectorSearchQuery query, QueryExecutionContext context)
      implements CursorQuery {
    @Override
    public Query getQuery() {
      return this.query.vectorSearchQuery();
    }
  }
}
