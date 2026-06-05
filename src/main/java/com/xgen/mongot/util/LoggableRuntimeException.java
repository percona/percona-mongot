package com.xgen.mongot.util;

import org.jetbrains.annotations.Nullable;

/**
 * Parent class for unchecked exceptions that must have a non-null message. Used for NullAway
 * analysis.
 */
public abstract class LoggableRuntimeException extends RuntimeException {

  protected final String message;

  protected LoggableRuntimeException(String message) {
    this(message, null);
  }

  protected LoggableRuntimeException(String message, @Nullable Throwable cause) {
    super(message, cause);
    this.message = message;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
