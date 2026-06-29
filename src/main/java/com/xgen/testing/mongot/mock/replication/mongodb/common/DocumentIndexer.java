package com.xgen.testing.mongot.mock.replication.mongodb.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.xgen.mongot.index.FieldExceededLimitsException;
import com.xgen.mongot.index.definition.IndexDefinition;
import java.util.Optional;
import org.mockito.Mockito;

public class DocumentIndexer {

  /** create a mock. */
  public static com.xgen.mongot.replication.mongodb.common.DocumentIndexer mockDocumentIndexer() {
    var mock = mock();
    doReturn(Optional.empty()).when(mock).exceededLimits();
    return mock;
  }

  /** Creates an indexer that handles auto embedding. */
  public static com.xgen.mongot.replication.mongodb.common.DocumentIndexer
      mockDocumentRequiresAutoEmbedding(IndexDefinition indexDefinition) {
    var mocked = mock();
    doReturn(indexDefinition).when(mocked).getIndexDefinition();
    return mocked;
  }

  /** Creates an indexer that exceeded indexing limits. */
  public static com.xgen.mongot.replication.mongodb.common.DocumentIndexer
      mockFieldLimitsExceeded() {
    var mocked = mock();
    mockFieldLimitsExceeded(mocked);
    return mocked;
  }

  /** Patch a mock DocumentIndexer to exceed field limit. */
  public static void mockFieldLimitsExceeded(
      com.xgen.mongot.replication.mongodb.common.DocumentIndexer mocked) {
    doReturn(Optional.of(new FieldExceededLimitsException("exceeded")))
        .when(mocked)
        .exceededLimits();
  }

  /** Creates an indexer that exceeded indexing field limit. */
  public static com.xgen.mongot.replication.mongodb.common.DocumentIndexer
      mockDocumentExceedsFieldLimit(String message) {
    var mocked = mock();
    mockFieldLimitsExceeded(mocked);
    mockDocumentExceedsFieldLimit(mocked, message);
    return mocked;
  }

  private static void mockDocumentExceedsFieldLimit(
      com.xgen.mongot.replication.mongodb.common.DocumentIndexer mocked, String message) {
    try {
      doThrow(new FieldExceededLimitsException(message)).when(mocked).indexDocumentEvent(any());
    } catch (FieldExceededLimitsException e) {
      throw new AssertionError("mocking failed");
    }
  }

  /** Creates an indexer that exceeded indexing limits. */
  public static com.xgen.mongot.replication.mongodb.common.DocumentIndexer
      mockDocumentExceedsLimitButNotIndex(String message) {
    var mocked = mockDocumentIndexer();
    mockDocumentExceedsFieldLimit(mocked, message);
    return mocked;
  }

  private static com.xgen.mongot.replication.mongodb.common.DocumentIndexer mock() {
    return Mockito.mock(com.xgen.mongot.replication.mongodb.common.DocumentIndexer.class);
  }
}
