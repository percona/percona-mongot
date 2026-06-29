package com.xgen.mongot.index.query;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.xgen.mongot.util.UserFacingException;
import com.xgen.mongot.util.functionalinterfaces.CheckedSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * InvalidQueryException is thrown when the user's query cannot be processed due to parsing or
 * logical errors. Note that the exception's message might be shown to the user.
 */
public class InvalidQueryException extends Exception implements UserFacingException {

  public enum Type {
    /** Default, represents an error which should be exposed to the user. */
    STRICT,
    /**
     * Represents an error which should not be shown to users. Used for backward compatibility
     * purposes, where we want to return empty result instead of throwing an error.
     */
    LENIENT
  }

  private final Type type;

  public InvalidQueryException(@Nullable String message) {
    this(message, Type.STRICT);
  }

  public InvalidQueryException(@Nullable String message, Type type) {
    super(message);
    this.type = type;
  }

  /** Throws a InvalidQueryException if the condition does not hold true. */
  public static void validate(boolean condition, @CompileTimeConstant String message)
      throws InvalidQueryException {
    if (!condition) {
      throw new InvalidQueryException(message);
    }
  }

  /**
   * Same as {@link #validate(boolean, String)}, but lazily formats the message string with
   * arbitrary args.
   */
  @FormatMethod
  public static void validate(boolean condition, @FormatString String format, Object... formatArgs)
      throws InvalidQueryException {
    if (!condition) {
      String message = String.format(format, formatArgs);
      throw new InvalidQueryException(message);
    }
  }

  /**
   * Throw InvalidQueryException instead of a checked exception thrown by the given supplier.
   *
   * @param exceptionClass Checked exception thrown by runnable
   * @return The return value of runnable
   * @throws InvalidQueryException In case runnable threw a exceptionClass
   */
  public static <V, E extends Exception> V wrapIfThrows(
      CheckedSupplier<V, E> runnable, Class<E> exceptionClass) throws InvalidQueryException {

    try {
      return runnable.get();
    } catch (Exception e) {
      if (exceptionClass.isInstance(e)) {
        throw new InvalidQueryException(e.getMessage());
      } else {
        throw (RuntimeException) e;
      }
    }
  }

  /** Throw InvalidQueryException if argument is not between min and max inclusive. */
  public static void argIsBetween(int argument, int min, int max, String argumentName)
      throws InvalidQueryException {
    InvalidQueryException.validate(
        argument >= min && argument <= max,
        "%s must be between %s and %s (is %s)",
        argumentName,
        min,
        max,
        argument);
  }

  public Type getType() {
    return this.type;
  }
}
