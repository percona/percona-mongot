package com.xgen.mongot.index.lucene;

import com.xgen.mongot.index.query.DeadlineExceededException;
import java.util.Optional;
import org.apache.lucene.index.QueryTimeout;

/**
 * A {@link QueryTimeout} that exits when the wall-clock time reaches an absolute deadline. The
 * deadline is expressed as epoch milliseconds, matching the {@code deadlineTimestampMs} field sent
 * by mongod in the vector search command.
 */
public class DeadlineQueryTimeout implements QueryTimeout {

  private final long deadlineMs;

  public DeadlineQueryTimeout(long deadlineTimestampMs) {
    this.deadlineMs = deadlineTimestampMs;
  }

  @Override
  public boolean shouldExit() {
    return System.currentTimeMillis() >= this.deadlineMs;
  }

  public static Optional<QueryTimeout> fromDeadline(Optional<Long> deadlineTimestampMs) {
    return deadlineTimestampMs.map(DeadlineQueryTimeout::new);
  }

  /**
   * Throws {@link DeadlineExceededException} if the given timeout is present and its deadline has
   * already been reached. An empty timeout (no deadline configured) is a no-op.
   */
  public static void throwIfExceeded(Optional<QueryTimeout> timeout) {
    if (timeout.isPresent() && timeout.get().shouldExit()) {
      throw new DeadlineExceededException();
    }
  }
}
