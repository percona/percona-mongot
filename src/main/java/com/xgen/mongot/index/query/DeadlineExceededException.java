package com.xgen.mongot.index.query;

import com.xgen.mongot.util.UserFacingException;

/**
 * Thrown when a search query exceeds its wall-clock time limit (the {@code deadlineTimestampMs}
 * supplied by mongod).
 */
public class DeadlineExceededException extends RuntimeException implements UserFacingException {

  private static final String DEADLINE_EXCEEDED_MESSAGE = "operation exceeded time limit";

  public DeadlineExceededException() {
    super(DEADLINE_EXCEEDED_MESSAGE);
  }
}
