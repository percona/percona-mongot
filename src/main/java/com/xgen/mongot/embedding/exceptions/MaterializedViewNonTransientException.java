package com.xgen.mongot.embedding.exceptions;

public class MaterializedViewNonTransientException extends RuntimeException {
  private final Reason reason;

  public MaterializedViewNonTransientException(String message) {
    super(message);
    this.reason = Reason.UNKNOWN;
  }

  public MaterializedViewNonTransientException(Throwable throwable) {
    super(throwable);
    this.reason = Reason.UNKNOWN;
  }

  public MaterializedViewNonTransientException(String message, Reason reason) {
    super(message);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public MaterializedViewNonTransientException(Throwable throwable, Reason reason) {
    super(throwable);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public Reason getReason() {
    return this.reason;
  }

  /** Categorizes the cause of a non-transient materialized view failure. */
  public enum Reason {
    /** Lease version is invalid; cannot write to MV without a valid lease. */
    INVALID_LEASE,
    /** A document in the MV has a higher leaseVersion — this instance is a stale leader. */
    FENCING_REJECTION,
    /** Single document exceeds 16 MiB limit after embedding vectors. */
    DOCUMENT_TOO_LARGE,
    /** MongoDB bulk write returned a non-retryable error code. */
    NON_RETRYABLE_ERROR,
    /** Default for legacy constructors or unclassified non-transient failures. */
    UNKNOWN,
  }
}
