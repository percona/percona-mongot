package com.xgen.mongot.util;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Objects;

/**
 * Bytes represents a byte-based amount of data, and provides utilities to convert between different
 * multiples of bytes.
 *
 * <p>Bytes exclusively uses IEC prefixes (https://en.wikipedia.org/wiki/Binary_prefix#IEC_prefixes)
 * to avoid any confusion between the number of bytes denoted by a prefix, and the normal SI prefix
 * meanings.
 */
public class Bytes implements Comparable<Bytes> {
  private static class BinaryFactor {

    private final double factor;

    BinaryFactor(int exponent) {
      this.factor = Math.pow(2, exponent);
    }

    long toBytes(long convertedBytes) {
      return (long) (convertedBytes * this.factor);
    }

    long fromBytes(long bytes) {
      return (long) (bytes / this.factor);
    }
  }

  private static final BinaryFactor KIBI_FACTOR = new BinaryFactor(10);
  private static final BinaryFactor MEBI_FACTOR = new BinaryFactor(20);
  private static final BinaryFactor GIBI_FACTOR = new BinaryFactor(30);

  private final long bytes;

  private Bytes(long bytes) {
    Check.argNotNegative(bytes, "bytes");
    this.bytes = bytes;
  }

  private Bytes(long convertedBytes, BinaryFactor factor) {
    this(factor.toBytes(convertedBytes));
  }

  public static Bytes ofBytes(long bytes) {
    return new Bytes(bytes);
  }

  public static Bytes ofKibi(long kibibytes) {
    return new Bytes(kibibytes, KIBI_FACTOR);
  }

  public static Bytes ofMebi(long mebibytes) {
    return new Bytes(mebibytes, MEBI_FACTOR);
  }

  public static Bytes ofGibi(long gibibytes) {
    return new Bytes(gibibytes, GIBI_FACTOR);
  }

  /** Returns a new Bytes object, which bytes size is the summation of current and given Bytes. */
  @CheckReturnValue
  public Bytes add(Bytes addend) {
    return new Bytes(this.bytes + addend.bytes);
  }

  /** Returns a new Bytes object, which bytes size is the subtraction of current and given Bytes. */
  @CheckReturnValue
  public Bytes subtract(Bytes subtrahend) {
    return new Bytes(this.bytes - subtrahend.bytes);
  }

  /** Gets the number of bytes in this Bytes. */
  public long toBytes() {
    return this.bytes;
  }

  /**
   * Gets the number of kibibytes in this Bytes.
   *
   * <p>Rounds down to the closest kibibyte.
   */
  public long toKibi() {
    return toFactor(KIBI_FACTOR);
  }

  /**
   * Gets the number of mebibytes in this Bytes.
   *
   * <p>Rounds down to the closest mebibyte.
   */
  public long toMebi() {
    return toFactor(MEBI_FACTOR);
  }

  /**
   * Gets the number of gibibytes in this Bytes.
   *
   * <p>Rounds down to the closest gibibyte.
   */
  public long toGibi() {
    return toFactor(GIBI_FACTOR);
  }

  @Override
  public String toString() {
    // Adopted from https://stackoverflow.com/a/3758880
    return this.bytes < 1024L
        ? this.bytes + " B"
        : this.bytes <= 0xfffccccccccccccL >> 40
            ? String.format("%.1f KiB", this.bytes / 0x1p10)
            : this.bytes <= 0xfffccccccccccccL >> 30
                ? String.format("%.1f MiB", this.bytes / 0x1p20)
                : this.bytes <= 0xfffccccccccccccL >> 20
                    ? String.format("%.1f GiB", this.bytes / 0x1p30)
                    : this.bytes <= 0xfffccccccccccccL >> 10
                        ? String.format("%.1f TiB", this.bytes / 0x1p40)
                        : this.bytes <= 0xfffccccccccccccL
                            ? String.format("%.1f PiB", (this.bytes >> 10) / 0x1p40)
                            : String.format("%.1f EiB", (this.bytes >> 20) / 0x1p40);
  }

  @Override
  public int compareTo(Bytes other) {
    return Long.compare(this.bytes, other.bytes);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof Bytes other)) {
      return false;
    }
    return this.bytes == other.bytes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.bytes);
  }

  private long toFactor(BinaryFactor factor) {
    return factor.fromBytes(this.bytes);
  }
}
