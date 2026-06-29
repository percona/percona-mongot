package com.xgen.mongot.util;

import com.google.errorprone.annotations.Var;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.Nullable;

/**
 * This class provides an Immutable, thread-safe list with an O(1) prepend() method. Because the
 * list is immutable, lists with common roots will share data. For example the following code:
 *
 * <pre>{@code
 * List<String> common = empty().prepend(root).prepend(document); // common = [document, root]
 * List<String> listA = common.prepend(foo);  // fieldA = [foo, document, root]
 * List<String> listB = common.prepend(bar);  // fieldB = [bar, document, root]
 * }</pre>
 *
 * <p>Results in an efficient memory structure:
 *
 * <pre>
 *   listA = foo
 *              \
 *      common = document -> root
 *              /
 *   listB = bar
 * </pre>
 */
public final class SingleLinkedList<T> extends AbstractCollection<T> {

  private static final SingleLinkedList<?> EMPTY = new SingleLinkedList<>(null, null, 0);

  @Nullable private final T value;

  private final int size;

  @Nullable private final SingleLinkedList<T> tail;

  private SingleLinkedList(@Nullable T value, @Nullable SingleLinkedList<T> tail, int size) {
    this.value = value;
    this.tail = tail;
    this.size = size;
  }

  @SuppressWarnings("unchecked")
  public static <T> SingleLinkedList<T> empty() {
    return (SingleLinkedList<T>) EMPTY;
  }

  /**
   * Efficiently iterates over the list from left to right (reverse insertion order) and applies
   * {@code accumulator} to each successive element. For example:
   *
   * <pre>
   * {@code [3, 2, 1].foldLeft("seed:", (result, next) -> result + next )} = "seed:321"
   * </pre>
   *
   * @param seed - The initial value
   * @param accumulator - A function that combines the next list element with the result accumulated
   *     so far.
   * @return Returns the accumulation of the list elements, or {@code seed} if the list is empty
   */
  public <S> S foldLeft(S seed, BiFunction<S, T, S> accumulator) {
    @Var S result = seed;
    for (T value : this) {
      result = accumulator.apply(result, value);
    }
    return result;
  }

  /**
   * Iterates over the list from right to left (insertion order) and applies {@code accumulator} to
   * each successive element. For example:
   *
   * <pre>
   * {@code [3, 2, 1].foldLeft("seed:", (result, next) -> result + next )} = "seed:123"
   * </pre>
   *
   * <b>Note:</b> This operation takes O(n) space. Prefer {@link #foldLeft(Object, BiFunction)} if
   * you don't care about the order of the traversal.
   *
   * @param seed - The initial value
   * @param accumulator - A function that combines the next list element with the result accumulated
   *     so far.
   * @return Returns the accumulation of the list elements, or {@code seed} if the list is empty
   */
  public <S> S foldRight(S seed, BiFunction<S, T, S> accumulator) {
    if (this.tail == null) {
      return seed;
    }
    return accumulator.apply(this.tail.foldRight(seed, accumulator), this.value);
  }

  @Override
  @Deprecated
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("SingleLinkedList is immutable");
  }

  @Override
  @Deprecated
  public boolean add(Object o) {
    throw new UnsupportedOperationException("SingleLinkedList is immutable");
  }

  public int size() {
    return this.size;
  }

  public boolean isEmpty() {
    return this.size == 0;
  }

  public T head() {
    Check.stateNotNull(this.value, "Cannot call head() on an empty list");
    return this.value;
  }

  public SingleLinkedList<T> getTail() {
    Check.stateNotNull(this.tail, "Cannot call tail() on an empty list");
    return this.tail;
  }

  public SingleLinkedList<T> prepend(T value) {
    return new SingleLinkedList<>(value, this, this.size + 1);
  }

  @Override
  public Spliterator<T> spliterator() {
    return Spliterators.spliterator(
        iterator(), this.size, Spliterator.SIZED | Spliterator.SUBSIZED);
  }

  @Override
  public Stream<T> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<>() {

      @Var private SingleLinkedList<T> ptr = SingleLinkedList.this;

      @Override
      public boolean hasNext() {
        return this.ptr.value != null;
      }

      @Override
      public T next() {
        if (this.ptr == EMPTY) {
          throw new NoSuchElementException();
        }
        T next = this.ptr.head();
        this.ptr = this.ptr.getTail();
        return next;
      }
    };
  }
}
