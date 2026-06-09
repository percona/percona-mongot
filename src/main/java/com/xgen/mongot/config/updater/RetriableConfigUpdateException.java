package com.xgen.mongot.config.updater;

import com.xgen.mongot.util.LoggableException;

/**
 * Thrown by {@link ConfigUpdater#update()} when an update fails due to a transient condition that
 * callers should retry with backoff rather than treat as fatal.
 */
public class RetriableConfigUpdateException extends LoggableException {

  public RetriableConfigUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}
