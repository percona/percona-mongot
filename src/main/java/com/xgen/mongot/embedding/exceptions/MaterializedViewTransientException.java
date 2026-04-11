package com.xgen.mongot.embedding.exceptions;

public class MaterializedViewTransientException extends RuntimeException {
  private final Reason reason;

  public MaterializedViewTransientException(String message) {
    super(message);
    this.reason = Reason.UNKNOWN;
  }

  public MaterializedViewTransientException(String message, Throwable cause) {
    super(message, cause);
    this.reason = Reason.UNKNOWN;
  }

  public MaterializedViewTransientException(Throwable throwable) {
    super(throwable);
    this.reason = Reason.UNKNOWN;
  }

  public MaterializedViewTransientException(String message, Reason reason) {
    super(message);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public MaterializedViewTransientException(Throwable throwable, Reason reason) {
    super(throwable);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public MaterializedViewTransientException(String message, Throwable cause, Reason reason) {
    super(message, cause);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public Reason getReason() {
    return this.reason;
  }

  /** Categorizes the cause of a transient materialized view failure. */
  public enum Reason {
    /** Sync source is missing; MongoClient cannot be obtained. */
    MONGO_CLIENT_NOT_AVAILABLE,
    /** Global auto-embedding memory budget exceeded when batch starts. */
    MEMORY_BUDGET_EXCEEDED,
    /** Lease acquisition or renewal failed (write operation). */
    LEASE_OPERATION_FAILED,
    /** Failed to read lease commit info (getCommitInfo / getCommitUserData). */
    READ_LEASE_FAILED,
    /** MongoDB bulk write returned a retryable error or max sub-retry attempts exceeded. */
    BULK_WRITE_ERROR,
    /** Failed to resolve the materialized view collection (missing metadata or UUID mismatch). */
    COLLECTION_RESOLUTION_FAILED,
    /** Mongod rejected write due to disk usage exceeding its limit (error code 14031). */
    EXCEEDED_DISK_LIMIT,
    /** Mongod blocked user writes (e.g. during Atlas maintenance or scaling). */
    USER_WRITES_BLOCKED,
    /** Mongod reported system overload (TemporarilyUnavailable, ExceededTimeLimit, etc.). */
    SYSTEM_OVERLOADED,
    /** Default for legacy constructors or unclassified transient failures. */
    UNKNOWN,
  }
}
