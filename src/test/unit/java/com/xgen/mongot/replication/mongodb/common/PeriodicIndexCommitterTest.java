package com.xgen.mongot.replication.mongodb.common;

import static com.xgen.mongot.index.status.IndexStatus.StatusCode.INITIAL_SYNC;
import static com.xgen.mongot.index.status.IndexStatus.StatusCode.STEADY;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

import com.xgen.mongot.index.Index;
import com.xgen.mongot.index.IndexClosedException;
import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.util.concurrent.Executors;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@RunWith(Theories.class)
public class PeriodicIndexCommitterTest {

  private static final Set<IndexStatus.StatusCode> ACTIONABLE_STATUSES =
      EnumSet.of(INITIAL_SYNC, STEADY);

  @DataPoints("actionableStatuses")
  public static Set<IndexStatus> actionableStatuses() {
    return Stream.of(IndexStatus.StatusCode.values())
        .filter(ACTIONABLE_STATUSES::contains)
        .map(IndexStatus::new)
        .collect(Collectors.toSet());
  }

  @DataPoints("nonActionableStatuses")
  public static Set<IndexStatus> nonActionableStatuses() {
    return Stream.of(IndexStatus.StatusCode.values())
        .filter(status -> !ACTIONABLE_STATUSES.contains(status))
        .map(IndexStatus::new)
        .collect(Collectors.toSet());
  }

  @Theory
  public void testPeriodicCommitsAreHappeningWhenIndexStatusIsActionable(
      @FromDataPoints("actionableStatuses") IndexStatus status)
      throws InterruptedException, IOException {
    var indexer = Mockito.mock(DocumentIndexer.class);
    var index = Mockito.mock(Index.class);
    when(index.getStatus()).thenReturn(status);
    var latch = new CountDownLatch(3);
    doAnswer(countDown(latch)).when(indexer).commit();

    new PeriodicIndexCommitter(
        index,
        indexer,
        Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()),
        Duration.ofMillis(50));

    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    Mockito.verify(indexer, atLeast(3)).commit();
  }

  @Theory
  public void testPeriodicCommitsAreNotHappeningWhenIndexStatusIsNotActionable(
      @FromDataPoints("nonActionableStatuses") IndexStatus status)
      throws IOException, InterruptedException {
    var indexer = Mockito.mock(DocumentIndexer.class);
    var index = Mockito.mock(Index.class);
    when(index.getStatus()).thenReturn(status);
    var latch = new CountDownLatch(1);
    doAnswer(countDown(latch)).when(indexer).commit();

    new PeriodicIndexCommitter(
        index,
        indexer,
        Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()),
        Duration.ofMillis(50));

    Assert.assertFalse(latch.await(200, TimeUnit.MILLISECONDS));
    Mockito.verify(indexer, never()).commit();
  }

  @Theory
  public void testPeriodicCommitsLogErrorIfIndexWriterIsClosed()
      throws IOException, InterruptedException {
    var index = Mockito.mock(Index.class);
    when(index.getStatus()).thenReturn(new IndexStatus(ACTIONABLE_STATUSES.iterator().next()));
    when(index.isClosed()).thenReturn(false);
    var indexer = Mockito.mock(DocumentIndexer.class);
    doThrow(new IndexClosedException("index already closed")).when(indexer).commit();

    var latch = new CountDownLatch(1);

    spy(
        new PeriodicIndexCommitter(
            index,
            indexer,
            Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()),
            Duration.ofMillis(50)) {
          @Override
          protected void crashWithException(Exception e) {
            latch.countDown();
          }
        });

    // We call indexer.commit, so the IndexerClosedException is thrown
    // we should give some time for the executor to schedule the commit call async
    Mockito.verify(indexer, timeout(500).atLeast(1)).commit();
    // The countdown latch never goes to 0, so crashWithException is never called
    Assert.assertFalse(latch.await(200, TimeUnit.MILLISECONDS));
  }

  @Theory
  public void testPeriodicCommitterCrashesIfUnexpectedExceptionThrowsDuringCommit()
      throws IOException, InterruptedException {
    var index = Mockito.mock(Index.class);
    when(index.getStatus()).thenReturn(new IndexStatus(ACTIONABLE_STATUSES.iterator().next()));
    when(index.isClosed()).thenReturn(false);
    var indexer = Mockito.mock(DocumentIndexer.class);
    doThrow(new IOException("Unexpected exception")).when(indexer).commit();
    var latch = new CountDownLatch(1);

    spy(
        new PeriodicIndexCommitter(
            index,
            indexer,
            Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()),
            Duration.ofMillis(50)) {
          @Override
          protected void crashWithException(Exception e) {
            latch.countDown();
          }
        });

    // If the latch.await returns true, crashWithException has been called
    Assert.assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
    Mockito.verify(indexer, atLeast(1)).commit();
  }

  @Theory
  public void testPeriodicCommitFuturesAreRemovedAfterStop(
      @FromDataPoints("actionableStatuses") IndexStatus status)
      throws InterruptedException, IOException {
    var indexer = Mockito.mock(DocumentIndexer.class);
    var index = Mockito.mock(Index.class);
    when(index.getStatus()).thenReturn(status);
    var latch = new CountDownLatch(3);
    doAnswer(countDown(latch)).when(indexer).commit();

    var executor = new ScheduledThreadPoolExecutor(1);
    PeriodicIndexCommitter periodicIndexCommitter =
        new PeriodicIndexCommitter(index, indexer, executor, Duration.ofMillis(50));

    assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
    Mockito.verify(indexer, atLeast(3)).commit();
    periodicIndexCommitter.close();
    Thread.sleep(500);
    assertTrue(executor.getQueue().isEmpty());
  }

  @Test
  public void testPeriodicCommitsStopAfterClose() throws Exception {
    var indexer = Mockito.mock(DocumentIndexer.class);
    var index = Mockito.mock(Index.class);
    var barrier = new CyclicBarrier(2);
    when(index.getStatus()).thenReturn(new IndexStatus(ACTIONABLE_STATUSES.iterator().next()));
    doAnswer(awaitBarrier(barrier)).when(indexer).commit();

    var periodicCommitter =
        new PeriodicIndexCommitter(
            index,
            indexer,
            Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()),
            Duration.ofMillis(100));

    barrier.await(500, TimeUnit.MILLISECONDS);

    periodicCommitter.close();
    barrier.reset();

    Assert.assertThrows(TimeoutException.class, () -> barrier.await(500, TimeUnit.MILLISECONDS));
  }

  private Answer<Void> countDown(CountDownLatch latch) {
    return invocation -> {
      latch.countDown();
      return null;
    };
  }

  private Answer<Void> awaitBarrier(CyclicBarrier barrier) {
    return invocation -> {
      barrier.await();
      return null;
    };
  }
}
