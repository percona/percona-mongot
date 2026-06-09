package com.xgen.mongot.index.query;

import java.util.Optional;

/**
 * Per-invocation execution context for a query. This context is supplied fresh on each reader
 * invocation and is intentionally never persisted in a cursor
 */
public record QueryExecutionContext(Optional<Long> deadlineTimestampMs) {

  private static final QueryExecutionContext EMPTY = new QueryExecutionContext(Optional.empty());

  public static QueryExecutionContext withDeadline(Optional<Long> deadlineTimestampMs) {
    return deadlineTimestampMs.isEmpty() ? EMPTY : new QueryExecutionContext(deadlineTimestampMs);
  }

  public static QueryExecutionContext empty() {
    return EMPTY;
  }
}
