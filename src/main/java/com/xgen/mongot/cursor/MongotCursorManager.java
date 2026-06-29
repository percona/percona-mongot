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
import com.xgen.mongot.index.version.GenerationId;
import com.xgen.mongot.searchenvoy.grpc.SearchEnvoyMetadata;
import com.xgen.mongot.util.Bytes;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface MongotCursorManager {

  /**
   * Registers a new cursor for the given query over the supplied index and returns the cursor's id
   * along with its MetaResults.
   */
  default SearchCursorInfo newCursor(
      String databaseName,
      String collectionName,
      UUID collectionUuid,
      Optional<String> viewName,
      Query query,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags,
      Optional<SearchEnvoyMetadata> searchEnvoyMetadata)
      throws IOException,
          InvalidQueryException,
          IndexUnavailableException,
          InterruptedException,
          ReaderClosedException {
    return newCursor(
        databaseName,
        collectionName,
        collectionUuid,
        viewName,
        new CursorQuery.Search(query),
        queryCursorOptions,
        queryOptimizationFlags,
        searchEnvoyMetadata);
  }

  SearchCursorInfo newCursor(
      String databaseName,
      String collectionName,
      UUID collectionUuid,
      Optional<String> viewName,
      CursorQuery cursorQuery,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags,
      Optional<SearchEnvoyMetadata> searchEnvoyMetadata)
      throws IOException,
          InvalidQueryException,
          IndexUnavailableException,
          InterruptedException,
          ReaderClosedException;

  IntermediateSearchCursorInfo newIntermediateCursors(
      String databaseName,
      String collectionName,
      UUID collectionUuid,
      Optional<String> viewName,
      SearchQuery query,
      int intermediateVersion,
      QueryCursorOptions queryCursorOptions,
      QueryOptimizationFlags queryOptimizationFlags,
      Optional<SearchEnvoyMetadata> searchEnvoyMetadata)
      throws IOException, InvalidQueryException, IndexUnavailableException, InterruptedException;

  /** Returns the next batch from a cursor given its id. */
  MongotCursorResultInfo getNextBatch(
      long cursorId, Bytes resultsSizeLimit, BatchCursorOptions queryCursorOptions)
      throws MongotCursorNotFoundException, IOException, IndexUnavailableException;

  QueryBatchTimerRecorder getIndexQueryBatchTimerRecorder(long cursorId)
      throws MongotCursorNotFoundException;

  /** Gets the `ExplainQueryState` stored in the cursor. */
  Optional<ExplainQueryState> getExplainQueryState(long cursorId)
      throws MongotCursorNotFoundException;

  /** Removes and cleans up the cursor with the given id. */
  void killCursor(long cursorId);

  /**
   * Drops all of the cursors from the index with the given id.
   *
   * <p>It is up to the caller to ensure no more cursors can be created prior to calling this
   * method.
   */
  void killIndexCursors(GenerationId indexGenerationId);

  /**
   * Whether an index with the given id has open cursors.
   *
   * <p>It is up to the caller to ensure no more cursors can be created prior to calling this
   * method.
   */
  boolean hasOpenCursors(GenerationId indexGenerationId);

  void close();
}
