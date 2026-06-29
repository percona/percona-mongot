package com.xgen.mongot.index.lucene.query.sort.comparator;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.index.query.sort.NullEmptySortPosition;
import java.io.IOException;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.LeafFieldComparator;
import org.apache.lucene.search.Pruning;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSelector;
import org.apache.lucene.search.comparators.LongComparator;
import org.apache.lucene.util.NumericUtils;
import org.jetbrains.annotations.Nullable;

/**
 * MqlLongComparator is able to sort Long values in a way that's compliant with the MQL sort order.
 * The position of missing values depends on the {@link NullEmptySortPosition} passed in.
 *
 * <p>This is mostly a copy of {@link LongComparator}, but with missing values stored explicitly as
 * null.
 */
public class MqlLongComparator extends MongotNumericComparator<Long> {
  private final Long[] values;
  protected @Nullable Long topValue;
  protected @Nullable Long bottom;
  private final SortField.Type sortType;
  private final SortedNumericSelector.Type selector;
  private final NullEmptySortPosition nullEmptySortPosition;
  private final long missingValueAsLong;

  /**
   * Records the initial pruning configuration set by Lucene. The pruning configuration may be
   * modified depending on the bottom value and the type of sort requested, as described below in
   * {@link MqlLongComparator.LongLeafComparator#updatePruningConfiguration(boolean)}.
   */
  private final Pruning originalPruningConfig;

  public MqlLongComparator(
      int numHits,
      String field,
      boolean reverse,
      Pruning pruning,
      SortField.Type sortType,
      SortedNumericSelector.Type selector,
      NullEmptySortPosition nullEmptySortPosition) {
    super(field, null, reverse, pruning, Long.BYTES);
    this.values = new Long[numHits];
    this.sortType = sortType;
    this.selector = selector;

    // record initial pruning configuration set by Lucene
    this.originalPruningConfig = pruning;
    this.nullEmptySortPosition = nullEmptySortPosition;
    this.missingValueAsLong =
        (this.nullEmptySortPosition == NullEmptySortPosition.LOWEST)
            ? Long.MIN_VALUE
            : Long.MAX_VALUE;
  }

  @Override
  public int compare(int slot1, int slot2) {
    return compareValues(this.values[slot1], this.values[slot2]);
  }

  @Override
  public int compareValues(Long first, Long second) {
    return mqlLongCompare(first, second, this.nullEmptySortPosition);
  }

  @Override
  public void setTopValue(Long value) {
    super.setTopValue(value);
    this.topValue = value;
  }

  @Override
  protected long missingValueAsComparableLong() {
    return this.missingValueAsLong;
  }

  @Override
  protected long sortableBytesToLong(byte[] bytes) {
    return NumericUtils.sortableBytesToLong(bytes, 0);
  }

  @Override
  public Long value(int slot) {
    return this.values[slot];
  }

  @Override
  public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
    return new LongLeafComparator(context);
  }

  public class LongLeafComparator extends NumericLeafComparator {

    public LongLeafComparator(LeafReaderContext context) throws IOException {
      super(context);
    }

    @Nullable
    public Long getValueForDoc(int doc) throws IOException {
      if (LongLeafComparator.this.docValues.advanceExact(doc)) {
        return LongLeafComparator.this.docValues.longValue();
      } else {
        // Explicitly return null if the value is missing
        return null;
      }
    }

    @Override
    public void setBottom(int slot) throws IOException {
      MqlLongComparator.this.bottom = MqlLongComparator.this.values[slot];
      super.setBottom(slot);
    }

    @Override
    public int compareBottom(int doc) throws IOException {
      return mqlLongCompare(
          MqlLongComparator.this.bottom,
          getValueForDoc(doc),
          MqlLongComparator.this.nullEmptySortPosition);
    }

    @Override
    public int compareTop(int doc) throws IOException {
      return mqlLongCompare(
          MqlLongComparator.this.topValue,
          getValueForDoc(doc),
          MqlLongComparator.this.nullEmptySortPosition);
    }

    @Override
    public void copy(int slot, int doc) throws IOException {
      MqlLongComparator.this.values[slot] = getValueForDoc(doc);
      super.copy(slot, doc);
    }

    @Override
    protected long bottomAsComparableLong(boolean isComparing) {
      updatePruningConfiguration(isComparing);
      return MqlLongComparator.this.bottom == null
          ? MqlLongComparator.this.missingValueAsLong
          : MqlLongComparator.this.bottom;
    }

    @Override
    protected long topAsComparableLong() {
      return MqlLongComparator.this.topValue == null
          ? MqlLongComparator.this.missingValueAsLong
          : MqlLongComparator.this.topValue;
    }

    @Override
    protected NumericDocValues getNumericDocValues(LeafReaderContext context, String field)
        throws IOException {
      return SortedNumericSelector.wrap(
          DocValues.getSortedNumeric(context.reader(), field),
          MqlLongComparator.this.selector,
          MqlLongComparator.this.sortType);
    }
  }

  @VisibleForTesting
  static int mqlLongCompare(
      @Nullable Long first, @Nullable Long second, NullEmptySortPosition nullEmptySortPosition) {
    if (first == null && second == null) {
      return 0;
    }

    if (first == null) {
      return nullEmptySortPosition == NullEmptySortPosition.HIGHEST ? 1 : -1;
    }

    if (second == null) {
      return nullEmptySortPosition == NullEmptySortPosition.HIGHEST ? -1 : 1;
    }

    return Long.compare(first, second);
  }

  private void updatePruningConfiguration(boolean isComparing) {
    if (isComparing) {
      // When bottom is a real value equal to missingValueAsLong, Long.compare in
      // isMissingValueCompetitive returns 0, incorrectly marking missing docs as
      // non-competitive. Shifting the value ensures the comparison reflects that
      // null is strictly less than (LOWEST) or greater than (HIGHEST) any real value.
      if (MqlLongComparator.this.bottom != null
          && MqlLongComparator.this.bottom == MqlLongComparator.this.missingValueAsLong
          && ((!MqlLongComparator.this.reverse
                  && MqlLongComparator.this.nullEmptySortPosition == NullEmptySortPosition.LOWEST)
              || (MqlLongComparator.this.reverse
                  && MqlLongComparator.this.nullEmptySortPosition
                      == NullEmptySortPosition.HIGHEST))) {
        MqlLongComparator.super.pruning = Pruning.GREATER_THAN;
      } else {
        MqlLongComparator.super.pruning = MqlLongComparator.this.originalPruningConfig;
      }
    } else if (MqlLongComparator.this.reverse
        && MqlLongComparator.this.nullEmptySortPosition != NullEmptySortPosition.HIGHEST
        && (MqlLongComparator.this.bottom == null)) {
      // For a descending sort with nulls lowest, we set the pruning enum to Pruning.GREATER_THAN
      // to ensure that documents containing actual Long.MIN_VALUE-s are not discarded when
      // bottom == null.
      MqlLongComparator.super.pruning = Pruning.GREATER_THAN;
    } else if (!MqlLongComparator.this.reverse
        && MqlLongComparator.this.nullEmptySortPosition == NullEmptySortPosition.HIGHEST
        && (MqlLongComparator.this.bottom == null)) {
      // For an ascending sort with nulls highest, we set the pruning enum to Pruning.GREATER_THAN
      // to ensure that documents containing actual Long.MAX_VALUE-s are not discarded when
      // bottom == null.
      MqlLongComparator.super.pruning = Pruning.GREATER_THAN;
    } else {
      // In all other sort scenarios, mongot can prune according to its original pruning
      // configuration defined at this class's instantiation
      MqlLongComparator.super.pruning = MqlLongComparator.this.originalPruningConfig;
    }
  }
}
