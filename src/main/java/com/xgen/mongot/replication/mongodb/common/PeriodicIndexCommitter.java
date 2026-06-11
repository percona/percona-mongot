package com.xgen.mongot.replication.mongodb.common;

import static com.xgen.mongot.index.status.IndexStatus.StatusCode.INITIAL_SYNC;
import static com.xgen.mongot.index.status.IndexStatus.StatusCode.STEADY;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.xgen.mongot.index.Index;
import com.xgen.mongot.index.IndexClosedException;
import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.util.Crash;
import com.xgen.mongot.util.VerboseRunnable;
import java.io.Closeable;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeriodicIndexCommitter implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(PeriodicIndexCommitter.class);

  private static final Set<IndexStatus.StatusCode> ACTIONABLE_STATUSES =
      EnumSet.of(INITIAL_SYNC, STEADY);

  private final Index index;
  private final DocumentIndexer indexer;

  @GuardedBy("this")
  private boolean shutdown = false;

  @GuardedBy("this")
  private Optional<ScheduledFuture<?>> commitFuture = Optional.empty();

  /** Creates a committer and immediately activates periodic commits on the given executor. */
  public static PeriodicIndexCommitter create(
      Index index, DocumentIndexer indexer, ScheduledExecutorService executor, Duration interval) {
    var committer = new PeriodicIndexCommitter(index, indexer);
    committer.start(executor, interval);
    return committer;
  }

  /**
   * Creates a committer with the given index and indexer. No periodic commits will occur unless
   * {@link #start} is called.
   */
  public static PeriodicIndexCommitter createInactive(Index index, DocumentIndexer indexer) {
    return new PeriodicIndexCommitter(index, indexer);
  }

  PeriodicIndexCommitter(Index index, DocumentIndexer indexer) {
    this.index = index;
    this.indexer = indexer;
  }

  /** Activates periodic commits on the given executor. Must be called at most once. */
  public synchronized void start(ScheduledExecutorService executor, Duration interval) {
    if (this.shutdown) {
      throw new IllegalStateException("cannot start a closed committer");
    }
    if (this.commitFuture.isPresent()) {
      throw new IllegalStateException("committer already started");
    }
    this.commitFuture =
        Optional.of(
            executor.scheduleWithFixedDelay(
                new VerboseRunnable() {
                  @Override
                  public void verboseRun() {
                    commitIfNeeded();
                  }

                  @Override
                  public Logger getLogger() {
                    return LOG;
                  }
                },
                0,
                interval.toMillis(),
                TimeUnit.MILLISECONDS));
  }

  @Override
  public synchronized void close() {
    this.shutdown = true;
    this.commitFuture.ifPresent(f -> f.cancel(false));
  }

  private synchronized void commitIfNeeded() {
    if (this.shutdown) {
      return;
    }
    if (!ACTIONABLE_STATUSES.contains(this.index.getStatus().getStatusCode())
        || this.index.isClosed()) {
      return;
    }

    try {
      this.indexer.commit();
    } catch (IndexClosedException e) {
      // Despite the checks preventing it, we may rarely try to commit after the index is closed
      LOG.atError()
          .addKeyValue("indexId", this.index.getDefinition().getIndexId())
          .setCause(e)
          .log("Failed to commit: Index is already closed");
    } catch (Exception e) {
      this.crashWithException(e);
    }
  }

  @VisibleForTesting
  protected void crashWithException(Exception e) {
    Crash.because("Unexpected Exception: Failed to commit index").withThrowable(e).now();
  }
}
