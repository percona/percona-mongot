package com.xgen.mongot.server.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to manage the commands in a single gRPC stream.
 *
 * <p>Workflow:
 *
 * <ol>
 *   <li>When receiving a command from client, {@code onCommandStart} should be called.
 *   <li>When a command is completed, {@code onCommandComplete} should be called to send server
 *       response.
 *   <li>When receiving a half-close from client, {@code onHalfClosedByClient} should be called.
 *       Server will send half-close after all pending commands are replied.
 *   <li>When the stream is cancelled, {@code onStreamCancellation} should be called. Server will
 *       send half-close after all pending commands are replied. A cleanup callback will be run
 *       after server sending half-close.
 * </ol>
 *
 * <p>Concurrency and Order:
 *
 * <ol>
 *   <li>{@code onCommandStart}, {@code onHalfClosedByClient} and {@code onStreamCancellation}
 *       should not be called concurrently.
 *   <li>{@code onCommandComplete} can be called concurrently with other methods.
 *   <li>After {@code onHalfClosedByClient} is called, only {@code onCommandComplete} can be called.
 *   <li>After {@code onStreamCancellation} is called, only {@code onCommandComplete} can be called.
 * </ol>
 */
public class CommandManager<T> {
  private static final Logger LOG = LoggerFactory.getLogger(CommandManager.class);

  private final StreamObserver<T> responseObserver;

  // Total number of pending `responseObserver.onNext` and `responseObserver.onCompleted` calls.
  // Server will send half-close after this number is reduced to 0.
  private final AtomicInteger numPendingResponseObserverCalls;

  // This callback will be executed after server sending half-close.
  // It can only be set in `onStreamCancellation`.
  private volatile Runnable cleanupCallback;

  // Set when the client cancels the stream (`onStreamCancellation`). Running commands poll this
  // via `isStreamCancelled()` to short-circuit themselves.
  private volatile boolean streamCancelled;

  // Set when the stream was terminated by a gRPC error via `onCommandFailure`. Read in
  // `sendSeverHalfCloseAndRunCleanupCallback` to decide whether to call `onCompleted` — gRPC
  // treats `onError` and `onCompleted` as mutually exclusive terminal operations, and calling
  // `onCompleted` after `onError` throws IllegalStateException from the underlying ServerCall.
  //
  // Orthogonal to `streamCancelled` above: `streamCancelled` signals running commands to abort,
  // while `terminatedWithError` signals the half-close callback not to re-complete the stream.
  // Both may be true in a race (e.g. load-shedding fires RESOURCE_EXHAUSTED concurrently with a
  // client-initiated cancel); the `!terminatedWithError` guard below is what keeps the half-close
  // path safe regardless of ordering.
  private volatile boolean terminatedWithError;

  CommandManager(StreamObserver<T> responseObserver) {
    this.responseObserver = responseObserver;
    // This is initialized to 1 because we need to call `responseObserver.onCompleted` after
    // receiving `onHalfClosedByClient` or `onStreamCancellation`.
    this.numPendingResponseObserverCalls = new AtomicInteger(1);
    this.streamCancelled = false;
    this.terminatedWithError = false;
    this.cleanupCallback =
        () -> {
          // Do nothing.
        };
  }

  void onCommandStart() {
    this.numPendingResponseObserverCalls.getAndIncrement();
  }

  // When the command fails, `replyMsg` will contain an error body.
  void onCommandComplete(T replyMsg, Runnable replySentCallback) {
    // Synchronization is required here because the `StreamObserver` is not thread-safe.
    synchronized (this) {
      try {
        this.responseObserver.onNext(replyMsg);
      } catch (StatusRuntimeException e) {
        // The RPC stream is already cancelled. Log at debug so we have a breadcrumb for
        // investigating unexpected cancellations without spamming warn on every client hangup.
        LOG.debug("onNext failed because stream was already cancelled", e);
      }
    }
    replySentCallback.run();

    if (this.numPendingResponseObserverCalls.decrementAndGet() == 0) {
      sendSeverHalfCloseAndRunCleanupCallback();
    }
  }

  /**
   * Terminates the current command by sending a gRPC error status to the client. This is used when
   * the server wants to signal envoy to retry on a different host (e.g., load shedding with
   * RESOURCE_EXHAUSTED), rather than returning a BSON error response via onNext.
   */
  void onCommandFailure(Status status) {
    synchronized (this) {
      try {
        this.responseObserver.onError(status.asRuntimeException());
        this.terminatedWithError = true;
      } catch (StatusRuntimeException e) {
        // The RPC stream is already cancelled. Mirrors the debug log in `onCommandComplete` —
        // same "client disconnected before we could respond" scenario, just on the error path.
        LOG.debug("onError failed because stream was already cancelled", e);
      }
    }

    if (this.numPendingResponseObserverCalls.decrementAndGet() == 0) {
      sendSeverHalfCloseAndRunCleanupCallback();
    }
  }

  void onHalfClosedByClient() {
    if (this.numPendingResponseObserverCalls.decrementAndGet() == 0) {
      sendSeverHalfCloseAndRunCleanupCallback();
    }
  }

  boolean isStreamCancelled() {
    return this.streamCancelled;
  }

  void onStreamCancellation(Runnable cleanupCallback) {
    this.streamCancelled = true;
    // - If there are running commands, this callback will be triggerred by `onCommandComplete` of
    //   the last completed command.
    // - Otherwise, this callback will be triggerred in the current `onStreamCancellation` call.
    this.cleanupCallback = cleanupCallback;

    // Currently mongot doesn't implement any optimizations to cancel a running command.
    // So we still wait for the response of all pending commands before sending half-close.
    if (this.numPendingResponseObserverCalls.decrementAndGet() == 0) {
      sendSeverHalfCloseAndRunCleanupCallback();
    }
  }

  // This method is triggered exactly once:
  // - When receive half-close from client,
  //   - If there are running commands, this method will be triggerred by `onCommandComplete` of the
  //     last completed command.
  //   - Otherwise, this method will be triggerred by `onHalfClosedByClient`.
  // - When the stream is cancelled,
  //   - If there are running commands, this method will be triggerred by `onCommandComplete` of the
  //     last completed command.
  //   - Otherwise, this method will be triggerred by `onStreamCancellation`.
  private void sendSeverHalfCloseAndRunCleanupCallback() {
    // Synchronization is not necessary here because there should be no concurrent calls to the
    // responseObserver.
    // Skip onCompleted if the stream was already terminated by onError — calling onCompleted
    // after onError violates the gRPC contract and throws IllegalStateException. Cleanup must
    // still run to release resources.
    if (!this.terminatedWithError) {
      try {
        this.responseObserver.onCompleted();
      } catch (StatusRuntimeException e) {
        // The RPC stream is already cancelled.
      }
    }

    this.cleanupCallback.run();
  }
}
