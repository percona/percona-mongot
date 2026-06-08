package com.xgen.mongot.replication.mongodb.steadystate.changestream;

import static com.google.common.truth.Truth.assertThat;

import com.xgen.mongot.util.concurrent.DualBlockingQueue;
import com.xgen.mongot.util.concurrent.PrioritizedTask;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

public class DualBlockingQueueTest {

  private static PrioritizedTask task(boolean isHighPriority) {
    return new PrioritizedTask(() -> {}, isHighPriority);
  }

  @Test
  public void testTake_returnsTaskFromHighPriorityQueue() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask task = task(true);
    queue.offer(task);

    assertThat(queue.take()).isEqualTo(task);
  }

  @Test
  public void testTake_returnsTaskFromLowPriorityQueue() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask task = task(false);
    queue.offer(task);

    assertThat(queue.take()).isEqualTo(task);
  }

  @Test
  public void testPoll_returnsNullWhenEmpty() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());

    assertThat(queue.poll()).isNull();
  }

  @Test
  public void testPoll_returnsTaskWhenAvailable() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask task = task(true);
    queue.offer(task);

    assertThat(queue.poll()).isEqualTo(task);
  }

  @Test
  public void testPollWithTimeout_returnsNullAfterTimeout() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());

    assertThat(queue.poll(50, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  public void testRatio_servesHighPriorityTasksBeforeLowPriority() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(2, new SimpleMeterRegistry());
    PrioritizedTask high1 = task(true);
    PrioritizedTask high2 = task(true);
    PrioritizedTask low1 = task(false);
    queue.offer(high1);
    queue.offer(high2);
    queue.offer(low1);

    assertThat(queue.take()).isEqualTo(high1);
    assertThat(queue.take()).isEqualTo(high2);
    assertThat(queue.take()).isEqualTo(low1);
  }

  @Test
  public void testFallback_servesLowPriorityWhenHighPriorityQueueEmpty()
      throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask low1 = task(false);
    queue.offer(low1);

    assertThat(queue.take()).isEqualTo(low1);
  }

  @Test
  public void testFallback_servesHighPriorityWhenLowPriorityQueueEmpty()
      throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(1, new SimpleMeterRegistry());
    PrioritizedTask high1 = task(true);
    PrioritizedTask high2 = task(true);
    queue.offer(high1);
    queue.offer(high2);

    assertThat(queue.take()).isEqualTo(high1);
    assertThat(queue.take()).isEqualTo(high2);
  }

  @Test
  public void testSize_reflectsBothQueues() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    queue.offer(task(true));
    queue.offer(task(false));
    queue.offer(task(true));

    assertThat(queue.size()).isEqualTo(3);
  }

  @Test
  public void testDrainTo_drainsAllTasks() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    queue.offer(task(true));
    queue.offer(task(false));
    queue.offer(task(true));

    List<Runnable> drained = new ArrayList<>();
    int count = queue.drainTo(drained);

    assertThat(count).isEqualTo(3);
    assertThat(drained).hasSize(3);
    assertThat(queue.size()).isEqualTo(0);
  }

  @Test
  public void testDrainTo_respectsMaxElements() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    queue.offer(task(true));
    queue.offer(task(true));
    queue.offer(task(true));

    List<Runnable> drained = new ArrayList<>();
    int count = queue.drainTo(drained, 2);

    assertThat(count).isEqualTo(2);
    assertThat(drained).hasSize(2);
    assertThat(queue.size()).isEqualTo(1);
  }

  @Test
  public void testPrioritizedTask_runDelegatesToRunnable() {
    AtomicReference<Boolean> ran = new AtomicReference<>(false);
    PrioritizedTask task = new PrioritizedTask(() -> ran.set(true), true);
    task.run();

    assertThat(ran.get()).isTrue();
  }

  @Test
  public void testConstructor_rejectsNonPositiveRatio() {
    Assert.assertThrows(
        IllegalArgumentException.class, () -> new DualBlockingQueue(0, new SimpleMeterRegistry()));
    Assert.assertThrows(
        IllegalArgumentException.class, () -> new DualBlockingQueue(-1, new SimpleMeterRegistry()));
  }

  @Test
  public void testConstructor_rejectsNullMeterRegistry() {
    Assert.assertThrows(NullPointerException.class, () -> new DualBlockingQueue(4, null));
  }

  @Test
  public void testOffer_rejectsNull() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());

    Assert.assertThrows(NullPointerException.class, () -> queue.offer(null));
  }

  @Test
  public void testOffer_rejectsNonPrioritizedTask() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());

    Assert.assertThrows(IllegalArgumentException.class, () -> queue.offer(() -> {}));
  }

  @Test
  public void testTake_blocksUntilTaskAvailable() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask task = task(true);
    CountDownLatch takeStarted = new CountDownLatch(1);
    AtomicReference<Runnable> taken = new AtomicReference<>();

    Thread consumer =
        new Thread(
            () -> {
              try {
                takeStarted.countDown();
                taken.set(queue.take());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    consumer.start();

    // Wait until the consumer thread has started, then confirm it is blocked (no task yet)
    takeStarted.await();
    Thread.sleep(50);
    assertThat(taken.get()).isNull();

    // Offering a task should unblock take()
    queue.offer(task);
    consumer.join(1000);
    assertThat(taken.get()).isEqualTo(task);
  }

  @Test
  public void testPollWithTimeout_wakesWhenTaskOfferedBeforeTimeout() throws InterruptedException {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask task = task(true);
    CountDownLatch pollStarted = new CountDownLatch(1);
    AtomicReference<Runnable> polled = new AtomicReference<>();

    Thread consumer =
        new Thread(
            () -> {
              try {
                pollStarted.countDown();
                polled.set(queue.poll(5, TimeUnit.SECONDS));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    consumer.start();

    pollStarted.await();
    queue.offer(task);
    consumer.join(2000);
    assertThat(polled.get()).isEqualTo(task);
  }

  @Test
  public void testRemove_removesFromBothQueues() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask high = task(true);
    PrioritizedTask low = task(false);
    queue.offer(high);
    queue.offer(low);

    assertThat(queue.remove(high)).isTrue();
    assertThat(queue.remove(low)).isTrue();
    assertThat(queue.size()).isEqualTo(0);
    // Removing something not present returns false
    assertThat(queue.remove(task(true))).isFalse();
  }

  @Test
  public void testPeek_returnsNextTaskWithoutRemoving() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());
    PrioritizedTask high = task(true);
    PrioritizedTask low = task(false);
    queue.offer(high);
    queue.offer(low);

    // peek() returns the next preferred task and does not remove it
    assertThat(queue.peek()).isEqualTo(high);
    assertThat(queue.size()).isEqualTo(2);
    assertThat(queue.peek()).isEqualTo(high);
  }

  @Test
  public void testPeek_returnsNullWhenEmpty() {
    DualBlockingQueue queue = new DualBlockingQueue(4, new SimpleMeterRegistry());

    assertThat(queue.peek()).isNull();
  }
}
