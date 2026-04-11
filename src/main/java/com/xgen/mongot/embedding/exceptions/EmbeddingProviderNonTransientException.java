package com.xgen.mongot.embedding.exceptions;

public class EmbeddingProviderNonTransientException extends RuntimeException {
  private final Reason reason;

  public EmbeddingProviderNonTransientException(String message) {
    super(message);
    this.reason = Reason.UNKNOWN;
  }

  public EmbeddingProviderNonTransientException(String message, Reason reason) {
    super(message);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
  }

  public Reason getReason() {
    return this.reason;
  }

  /** Categorizes the cause of a non-transient embedding provider failure. */
  public enum Reason {
    /** Requested embedding model is not registered in the catalog or service registry. */
    MODEL_NOT_REGISTERED,
    /** Error during embedding batch preparation in EmbeddingServiceManager. */
    BATCHING_ERROR,
    /** Provider returned HTTP 400 due to input exceeding token limit. */
    INPUT_TOO_LARGE,
    /** Materialized view metadata missing when embedding batch starts. */
    MV_METADATA_NOT_PRESENT,
    /** Default for legacy constructors or unclassified non-transient failures. */
    UNKNOWN,
  }
}
