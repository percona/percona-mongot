package com.xgen.mongot.cursor;

import com.xgen.mongot.cursor.batch.BatchCursorOptions;
import com.xgen.mongot.cursor.batch.QueryCursorOptions;
import com.xgen.mongot.index.IndexUnavailableException;
import com.xgen.mongot.index.ReaderClosedException;
import com.xgen.mongot.index.lucene.explain.tracing.ExplainQueryState;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.Query;
import com.xgen.mongot.index.query.QueryOptimizationFlags;
import com.xgen.mongot.index.query.SearchQuery;
import com.xgen.mongot.util.Bytes;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Manages the cursors for one index. */
interface IndexCursorManager {

  default SearchCursorInfo createCursor(
      String namespace,
      Query query,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags)
      throws IOException,
          InvalidQueryException,
          IndexUnavailableException,
          InterruptedException,
          ReaderClosedException {
    return createCursor(
        namespace, new CursorQuery.Search(query), queryCursorOptions, queryOptimizationFlags);
  }

  SearchCursorInfo createCursor(
      String namespace,
      CursorQuery cursorQuery,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags)
      throws IOException,
          InvalidQueryException,
          IndexUnavailableException,
          InterruptedException,
          ReaderClosedException;

  IntermediateSearchCursorInfo createIntermediateCursors(
      String namespace,
      SearchQuery query,
      int intermediateVersion,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags)
      throws IOException, InvalidQueryException, IndexUnavailableException, InterruptedException;

  MongotCursorResultInfo getNextBatch(
      long cursorId, Bytes resultsSizeLimit, BatchCursorOptions queryCursorOptions)
      throws MongotCursorNotFoundException, IOException;

  void killCursor(long cursorId);

  Collection<Long> killAll();

  boolean hasOpenCursors();

  void reportLongLivedCursors(Duration reportDuration, int reportedLongLivedCursorsSize);

  List<Long> killIdleCursorsSince(Instant idleSince);

  /**
   * Returns a QueryBatchTimerRecorder that will record the given Timer under this index's batch
   * processing metrics.
   */
  QueryBatchTimerRecorder getQueryBatchTimerRecorder();

  /** Gets the `ExplainQueryState` stored in the cursor. */
  Optional<ExplainQueryState> getExplainQueryState(long cursorId)
      throws MongotCursorNotFoundException;
}
