package com.xgen.mongot.util;

import org.jetbrains.annotations.Nullable;

/**
 * Parent class for checked exceptions that must have a non-null message. Used for NullAway
 * analysis.
 */
public abstract class LoggableException extends Exception {

  protected final String message;

  protected LoggableException(String message) {
    this(message, null);
  }

  protected LoggableException(String message, @Nullable Throwable cause) {
    super(message, cause);
    this.message = message;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
