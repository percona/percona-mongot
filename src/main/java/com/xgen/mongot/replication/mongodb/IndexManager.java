package com.xgen.mongot.replication.mongodb;

import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/** Common interface for index replication managers. */
public interface IndexManager {

  /**
   * The lifecycle state of an {@link IndexManager}.
   *
   * <p>See {@link ReplicationIndexManager} for the full state-machine description.
   */
  enum State {
    INITIALIZING,
    INITIAL_SYNC,
    INITIAL_SYNC_BACKOFF,
    STEADY_STATE,
    STEADY_STATE_SHUT_DOWN,
    FAILED,
    /**
     * Similar to FAILED only due to a user exceeding a usage limit. Unlike FAILED, this state is
     * persisted and recoverable after restart.
     */
    FAILED_EXCEEDED,
    SHUT_DOWN,
  }

  CompletableFuture<Void> getInitFuture();

  @Nullable
  State getState();

  CompletableFuture<Void> shutdown();

  CompletableFuture<Void> drop();
}
