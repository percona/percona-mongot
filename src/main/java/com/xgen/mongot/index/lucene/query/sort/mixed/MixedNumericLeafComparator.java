package com.xgen.mongot.index.lucene.query.sort.mixed;

import java.io.IOException;
import org.apache.lucene.index.NumericDocValues;
import org.bson.BsonValue;

/**
 * A LeafComparator that reads from {@link NumericDocValues}. Works for any fixed sized type but is
 * not necessarily the most efficient implementation since it relies on allocating BsonValues for
 * comparison.
 */
abstract class MixedNumericLeafComparator implements MixedLeafFieldComparator {

  private final int priority;
  protected final NumericDocValues dv;

  protected final BsonValue top;

  protected BsonValue bottom;

  MixedNumericLeafComparator(CompositeComparator parent, NumericDocValues dv, int bracket) {
    this.dv = dv;
    this.priority = bracket;
    this.top = parent.getTop();
  }

  @Override
  public int getBracketPriority() {
    return this.priority;
  }

  @Override
  public boolean hasValue(int doc) throws IOException {
    return this.dv.advanceExact(doc);
  }

  @Override
  public void notifyNewBottom(BsonValue bottom) {
    this.bottom = bottom;
  }

  @Override
  public int compareBottomToCurrent() throws IOException {
    // Called after hasValue(doc) returns true
    return compareWithinBracket(this.bottom);
  }

  @Override
  public int compareTopToCurrent() throws IOException {
    // Called after hasValue returns true
    return compareWithinBracket(this.top);
  }

  private int compareWithinBracket(BsonValue left) throws IOException {
    // TODO(CLOUDP-210522): We could avoid allocations here if BsonNumberUtils handled NaNs properly
    return SortUtil.compareWithinBracketUnsafe(left, this.getCurrentValue());
  }

  @Override
  public int nextDoc() throws IOException {
    return this.dv.nextDoc();
  }
}
