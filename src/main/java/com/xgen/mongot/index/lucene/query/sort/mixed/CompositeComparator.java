package com.xgen.mongot.index.lucene.query.sort.mixed;

import com.xgen.mongot.index.query.sort.SortOrder;
import com.xgen.mongot.index.query.sort.SortSelector;
import com.xgen.mongot.index.query.sort.UserFieldSortOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.LeafFieldComparator;
import org.bson.BsonValue;
import org.jetbrains.annotations.Nullable;

/**
 * This {@link FieldComparator} is used when doing a sort on heterogeneous fields and heterogeneous
 * arrays.
 */
class CompositeComparator extends FieldComparator<BsonValue> {

  /** Sentinel value to indicate that top/bottom bracket is uninitialized. */
  static final int BRACKET_NOT_SET = -1;

  /** Per-type comparators sorted based on their priority. */
  private final MixedFieldComparator[] comparators;

  /**
   * Monotonically non-null elements. Null checking on elements is unnecessary due to the contract
   * of {@link FieldComparator} methods.
   */
  final BsonValue[] values;

  final UserFieldSortOptions options;

  /** Value is 1 if selector is MIN, -1 if selector is MAX. */
  final int selectMultiplier;

  /** Value is only set if searchAfter is provided. */
  private int topBracket = BRACKET_NOT_SET;

  /** Value is only set if searchAfter is provided. */
  @Nullable private BsonValue top = null;

  /** The array index of the current bottom value, or -1 if the queue is not full yet. */
  private int bottomSlot = BRACKET_NOT_SET;

  private boolean singleSort;
  boolean hitsThresholdReached;
  private final SortOrder sortOrder;

  /**
   * Create a comparator that is able to sort a field with multiple types or documents with
   * heterogeneous arrays.
   *
   * @param comparators factories for creating per-type LeafComparators
   * @param options configures sort direction and element selection for arrays
   * @param numHits the maximize number of hits to return (i.e. the size of the heap)
   */
  public static CompositeComparator create(
      MixedFieldComparator[] comparators, UserFieldSortOptions options, int numHits) {
    BsonValue[] values = new BsonValue[numHits];
    int selectMultiplier = options.selector() == SortSelector.MIN ? 1 : -1;

    Arrays.sort(
        comparators,
        Comparator.comparingInt(
            c ->
                selectMultiplier
                    * SortUtil.getBracketPriority(
                        c.getBsonType(), options.nullEmptySortPosition())));

    return new CompositeComparator(options, values, selectMultiplier, comparators);
  }

  public CompositeComparator(
      UserFieldSortOptions options,
      BsonValue[] values,
      int selectMultiplier,
      MixedFieldComparator[] comparators) {
    this.options = options;
    this.sortOrder = options.order();
    this.values = values;
    this.selectMultiplier = selectMultiplier;
    this.comparators = comparators;
  }

  @Override
  public int compare(int slot1, int slot2) {
    BsonValue left = this.values[slot1];
    BsonValue right = this.values[slot2];
    return SortUtil.mqlMixedCompare(left, right, this.options.nullEmptySortPosition());
  }

  @Override
  public void setTopValue(BsonValue value) {
    // Called once if using searchAfter or GetMore
    this.topBracket =
        SortUtil.getBracketPriority(value.getBsonType(), this.options.nullEmptySortPosition());
    this.top = value;
  }

  @Override
  public BsonValue value(int slot) {
    return this.values[slot];
  }

  @Override
  public int compareValues(BsonValue first, BsonValue second) {
    // Used for concurrent segment search
    return SortUtil.mqlMixedCompare(first, second, this.options.nullEmptySortPosition());
  }

  @Override
  public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
    LeafReader reader = context.reader();
    var nullPosition = this.options.nullEmptySortPosition();
    List<MixedLeafFieldComparator> children = new ArrayList<>(this.comparators.length);
    for (MixedFieldComparator comparator : this.comparators) {
      Optional<MixedLeafFieldComparator> leaf =
          comparator.getLeaf(
              this, reader, SortUtil.getBracketPriority(comparator.getBsonType(), nullPosition));
      leaf.ifPresent(children::add);
    }
    if (children.isEmpty()) {
      children.add(new MixedEmptyLeafComparator(nullPosition));
    } else if (children.getLast().getBracketPriority() == nullPosition.nullMissingPriority) {
      // No need to read docValues to distinguish between null and missing
      children.set(children.size() - 1, new MixedEmptyLeafComparator(nullPosition));
    }

    MixedLeafFieldComparator[] presentChildren = children.toArray(MixedLeafFieldComparator[]::new);
    CompositeLeafComparator leafComparator =
        new CompositeLeafComparator(this, presentChildren, reader);
    if (this.bottomSlot != BRACKET_NOT_SET) {
      // Bottom was set during previous segment, inform comparator
      leafComparator.informBottom(this.values[this.bottomSlot]);
    } else if (this.topBracket != BRACKET_NOT_SET) {
      leafComparator.updateCompetitiveIterator();
    }
    return leafComparator;
  }

  @Override
  public void setSingleSort() {
    this.singleSort = true;
  }

  boolean isSingleSort() {
    return this.singleSort;
  }

  /**
   * Called by a leaf comparator when Lucene informs a leaf of a new weakest element. We need to
   * store this value here to use across segments.
   */
  void cacheBottomSlot(int slot) {
    this.bottomSlot = slot;
  }

  /**
   * The sorting bracket of {@code getTop().getType()}, or {@link #BRACKET_NOT_SET} if top is not
   * present.
   */
  int getTopBracket() {
    return this.topBracket;
  }

  /**
   * This value is present iff using sort with searchAfter or GetMore. The top is supplied by Lucene
   * after construction. The value is effectively constant once set. If a method relying on top,
   * such as {@link LeafFieldComparator#compareTop(int)}, is called, then top is guaranteed to be
   * set.
   */
  @Nullable
  BsonValue getTop() {
    return this.top;
  }

  public SortOrder getOrder() {
    return this.sortOrder;
  }
}
