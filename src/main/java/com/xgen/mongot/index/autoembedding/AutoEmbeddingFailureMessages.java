package com.xgen.mongot.index.autoembedding;

/**
 * Shared prefix prepended to auto-embedding index failure status messages so they can be filtered
 * in logs and matched by the MMS alert. This literal is part of a cross-service contract: changing
 * it requires updating the corresponding MMS alert that searches for it.
 */
public final class AutoEmbeddingFailureMessages {
  public static final String FAILURE_PREFIX = "Automated Embedding Index Failed: ";

  private AutoEmbeddingFailureMessages() {}

  public static String withFailurePrefix(String message) {
    return FAILURE_PREFIX + message;
  }
}
