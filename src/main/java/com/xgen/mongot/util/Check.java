package com.xgen.mongot.util;

import com.google.common.base.Strings;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Class for basic checks. */
public class Check {

  @Language("RegExp")
  private static final String JAVA_IDENTIFIER = "[a-zA-Z0-9_]+((\\.)?([a-zA-Z0-9_]*\\(\\))?)*";

  private static final Logger LOGGER = LoggerFactory.getLogger(Check.class);

  /** Throws an IllegalArgumentException if the provided assertion fails. */
  @FormatMethod
  @Contract("false, _ -> fail")
  public static void checkArg(boolean assertion, @FormatString String message) {
    if (!assertion) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Throws an IllegalArgumentException if the provided assertion fails. Lazily format error
   * message.
   */
  @FormatMethod
  @Contract("false, _, _ -> fail")
  public static void checkArg(
      boolean assertion, @FormatString String format, @Nullable Object... formatArgs) {
    if (!assertion) {
      String message = Strings.lenientFormat(format, formatArgs);
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Intended to be used to check arguments. Throws an IllegalArgumentException if argument is null.
   */
  @Contract("null, _ -> fail")
  public static <T> T argNotNull(
      @Nullable T obj, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String fieldName) {
    if (obj == null) {
      throw new IllegalArgumentException(String.format("%s cannot be null", fieldName));
    }
    return obj;
  }

  /**
   * Intended to be used to check arguments. Throws an IllegalArgumentException if argument is not
   * null.
   */
  @Contract("!null, _ -> fail")
  public static void argIsNull(@Nullable Object obj, @CompileTimeConstant String fieldName) {
    if (obj != null) {
      throw new IllegalArgumentException(String.format("%s should be null", fieldName));
    }
  }

  /**
   * Intended to be used to check arguments. Throws an IllegalArgumentException if argument is
   * empty. Does not check for nulls.
   */
  public static void argNotEmpty(Collection<?> obj, @CompileTimeConstant String fieldName) {
    if (obj.isEmpty()) {
      throw new IllegalArgumentException(String.format("%s cannot be empty", fieldName));
    }
  }

  /**
   * Intended to be used to check arguments. Throws an IllegalArgumentException if argument is
   * empty. Does not check for nulls.
   */
  public static void argNotEmpty(Map<?, ?> obj, @CompileTimeConstant String fieldName) {
    if (obj.isEmpty()) {
      throw new IllegalArgumentException(String.format("%s cannot be empty", fieldName));
    }
  }

  /**
   * Intended to be used to check String arguments. Throws an IllegalArgumentException if argument
   * is empty. Does not check for nulls.
   */
  public static void argNotEmpty(
      String str, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String fieldName) {
    if (str.isEmpty()) {
      throw new IllegalArgumentException(String.format("%s cannot be empty", fieldName));
    }
  }

  /**
   * Ensures that for the supplied collection, all of the given attributes returned by the mapper
   * are unique.
   */
  public static <E, A> void elementAttributesAreUnique(
      Collection<E> collection,
      Function<E, A> mapper,
      @CompileTimeConstant String fieldName,
      String attributeName) {
    Set<A> seen = new HashSet<>();
    for (E elem : collection) {
      A attribute = mapper.apply(elem);
      if (seen.contains(attribute)) {
        throw new IllegalArgumentException(
            String.format("%s elements must contain unique %s", fieldName, attributeName));
      }

      seen.add(attribute);
    }
  }

  public static <E> E hasSingleElement(Collection<E> collection, String fieldName) {

    if (collection.size() == 1) {
      return collection.iterator().next();
    }

    throw new IllegalArgumentException(
        fieldName + " must have exactly one element, but got " + collection.size());
  }

  /**
   * Intended to be used to check object state. Throws an IllegalStateException if object reference
   * is null.
   */
  @FormatMethod
  @Contract("null, _ -> fail")
  public static <T> T stateNotNull(@Nullable T obj, @FormatString String msg) {
    if (obj == null) {
      throw new IllegalStateException(msg);
    }
    return obj;
  }

  @FormatMethod
  @Contract("null, _, _ -> fail")
  public static <T> T stateNotNull(
      @Nullable T obj, @FormatString String fmt, @Nullable Object... args) {
    if (obj == null) {
      String message = Strings.lenientFormat(fmt, args);
      throw new IllegalStateException(message);
    }
    return obj;
  }

  /**
   * Throws an IllegalStateException if the provided assertion fails. Optimized variant of {@link
   * #checkState(boolean, String, Object...)} for constant string messages.
   */
  @Contract("false, _ -> fail")
  public static void checkState(boolean assertion, @CompileTimeConstant String msg) {
    if (!assertion) {
      throw new IllegalStateException(msg);
    }
  }

  /**
   * Throws an IllegalStateException if the provided assertion fails. Lazily format error message.
   */
  @FormatMethod
  @Contract("false, _, _ -> fail")
  public static void checkState(
      boolean assertion, @FormatString String format, @Nullable Object... formatArgs) {
    if (!assertion) {
      String message = Strings.lenientFormat(format, formatArgs);
      throw new IllegalStateException(message);
    }
  }

  /** Throws an IllegalArgumentException if argument < 0. */
  public static void argNotNegative(int number, @CompileTimeConstant String fieldName) {
    if (number < 0) {
      throw new IllegalArgumentException(
          String.format("%s cannot be negative but was %d", fieldName, number));
    }
  }

  /** Throws an IllegalArgumentException if argument < 0. */
  public static void argNotNegative(long number, @CompileTimeConstant String fieldName) {
    if (number < 0) {
      throw new IllegalArgumentException(
          String.format("%s cannot be negative but was %d", fieldName, number));
    }
  }

  /** Throws an IllegalArgumentException if argument < 0. */
  public static void argNotNegative(double number, @CompileTimeConstant String fieldName) {
    if (number < 0) {
      throw new IllegalArgumentException(
          String.format("%s cannot be negative but was %f", fieldName, number));
    }
  }

  /** Throws an IllegalArgumentException if argument <= 0. */
  public static void argIsPositive(int number, @CompileTimeConstant String fieldName) {
    if (number <= 0) {
      throw new IllegalArgumentException(
          String.format("%s must be positive but was %d", fieldName, number));
    }
  }

  /** Throws an IllegalArgumentException if argument <= 0. */
  public static void argIsPositive(double number, @CompileTimeConstant String fieldName) {
    if (number <= 0D) {
      throw new IllegalArgumentException(
          String.format("%s must be positive but was %f", fieldName, number));
    }
  }

  /** Throws an IllegalArgumentException if argument <= 0. */
  public static void argIsPositive(Duration duration, @CompileTimeConstant String fieldName) {
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException(
          String.format("%s must be positive but was %s", fieldName, duration));
    }
  }

  /** Throws an IllegalArgumentException if argument < min or argument > max. */
  public static void argInInclusiveRange(
      double number, double min, double max, @CompileTimeConstant String fieldName) {
    if (number < min || number > max) {
      throw new IllegalArgumentException(
          String.format("%s must be within %s to %s but was %f", fieldName, min, max, number));
    }
  }

  /** Throws an IllegalArgumentException if argument is NaN. */
  public static void argNotNaN(float number, @CompileTimeConstant String fieldName) {
    if (Float.isNaN(number)) {
      throw new IllegalArgumentException(String.format("%s cannot be NaN", fieldName));
    }
  }

  /**
   * Don't leave in production code if possible, but you can use it during implementation for
   * methods that you haven't implemented yet.
   */
  public static void throwNotImplemented(String message) {
    throw new NotImplementedException(message);
  }

  /**
   * Intended to be used to check arguments. Throws an IllegalArgumentException if argument is null.
   */
  public static void validPortNumber(int port) {
    if (port < 0 || 65535 < port) {
      throw new IllegalArgumentException(String.format("port: %d outside of valid range", port));
    }
  }

  /**
   * Throws an AssertionError if the supplied Optional is not present.
   *
   * <p>For more control over the error message, see {@link Optionals#orElseThrow(Optional, String)}
   *
   * @param variableName - The name of the optional variable which will be used to construct an
   *     error message.
   * @return the value contained in the Optional
   */
  public static <T> T isPresent(
      Optional<T> optional, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String variableName) {
    return optional.orElseThrow(
        () -> new AssertionError(String.format("%s must be present", variableName)));
  }

  /** Throws an AssertionError if the supplied Optional is present. */
  public static void isEmpty(
      Optional<?> optional, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String variableName) {
    if (optional.isPresent()) {
      throw new AssertionError(String.format("%s must be empty", variableName));
    }
  }

  /** Throws an AssertionError if the supplied value is null. */
  @Contract("null, _-> fail")
  public static <T> T isNotNull(
      @Nullable T value, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String variableName) {
    if (value == null) {
      throw new AssertionError(String.format("%s must not be null", variableName));
    }
    return value;
  }

  /** Throws an AssertionError if the supplied value is not null. */
  @Contract("!null, _-> fail")
  public static void isNull(
      @Nullable Object value, @CompileTimeConstant @Pattern(JAVA_IDENTIFIER) String variableName) {
    if (value != null) {
      throw new AssertionError(String.format("%s must be null", variableName));
    }
  }

  public static void exactlyOneOf(@CompileTimeConstant String message, Optional<?>... optionals) {
    long count = Arrays.stream(optionals).filter(Optional::isPresent).count();
    checkState(count == 1, message);
  }

  public static void atMostOneOf(@CompileTimeConstant String message, Optional<?>... optionals) {
    long count = Arrays.stream(optionals).filter(Optional::isPresent).count();
    checkState(count <= 1, message);
  }

  /**
   * Throws an AssertionError if the method is called.
   *
   * <p>Returns whatever type the compiler expects it to so it can be used when to satisfy the
   * compiler that we always return a value even when a line is unreachable.
   *
   * <p>A contrived example: <code>
   *   Boolean foo(Object o) {
   *     if (o != null) {
   *       return true;
   *     }
   *
   *     // Always throws exception.
   *     handleNullValue(o);
   *
   *     // Compiler doesn't know handleNullValue always throws an exception,
   *     // so it still wants you to return a value.
   *     return Check.unreachable();
   *   }
   * </code>
   */
  @Contract("-> fail")
  @Deprecated
  public static <T> T unreachable() {
    return unreachable("unreachable");
  }

  @Contract("_->fail")
  public static <T> T unreachable(@CompileTimeConstant String reason) {
    // increment global counter if initialized (handled internally)
    GlobalMetricFactory.incrementUnreachable(reason);

    // log stacktrace for debugging
    LOGGER.error("Unreachable code reached: {}", reason, new Throwable("stacktrace"));
    throw new AssertionError("Unreachable code reached: " + reason);
  }

  /**
   * Report an occurrence of unreachable code and logs the error, but does not throw. Useful for
   * cases where we want visibility without failing (e.g. replication logic).
   *
   * <p>This function should be used at discretion as it should result in a high severity alert.
   */
  public static void reportUnreachable(@CompileTimeConstant String reason) {
    // increment global counter if initialized (handled internally)
    GlobalMetricFactory.incrementUnreachable(reason);

    // log at error level but continue execution
    LOGGER.error("Unexpected code path (alert only): {}", reason, new Throwable("stacktrace"));
  }

  /** Returns error object used in unreachable checks */
  public static AssertionError unreachableError() {
    return new AssertionError("unreachable");
  }

  /** Check that two enums are equal. */
  public static <E extends Enum<E>> void expectedType(E expected, E actual) {
    if (expected != actual) {
      throw new UnsupportedOperationException(
          String.format("Expected to be of type %s but found: %s", expected, actual));
    }
  }

  /** Check that an enum is one of multiple potentially valid values. */
  public static <E extends Enum<E>> void expectedType(EnumSet<E> expected, E actual) {
    if (!expected.contains(actual)) {
      throw new UnsupportedOperationException(
          String.format(
              "Expected to be one of types [%s] but found: %s",
              expected.stream().map(Objects::toString).collect(Collectors.joining(",")), actual));
    }
  }

  public static <T> T instanceOf(Object actual, Class<T> expected) {
    if (!expected.isInstance(actual)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected %s, but got instance of %s",
              expected.getName(), actual.getClass().getName()));
    }
    return expected.cast(actual);
  }
}
