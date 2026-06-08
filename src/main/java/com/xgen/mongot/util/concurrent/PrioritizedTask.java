package com.xgen.mongot.util.concurrent;

import java.util.Objects;

/**
 * A {@link Runnable} that carries priority metadata for routing by {@link DualBlockingQueue}.
 *
 * @param runnable the work to execute
 * @param isHighPriority true if the scheduler has determined this task should be routed to the
 *     high-priority queue
 */
public record PrioritizedTask(Runnable runnable, boolean isHighPriority) implements Runnable {

  public PrioritizedTask {
    Objects.requireNonNull(runnable);
  }

  @Override
  public void run() {
    this.runnable.run();
  }
}
