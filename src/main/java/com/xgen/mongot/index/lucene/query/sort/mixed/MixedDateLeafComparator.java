package com.xgen.mongot.index.lucene.query.sort.mixed;

import com.xgen.mongot.index.query.sort.NullEmptySortPosition;
import java.io.IOException;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.bson.BsonDateTime;
import org.bson.BsonType;
import org.bson.BsonValue;

/**
 * A specialized alternative to {@link MixedNumericLeafComparator} that can perform top/bottom
 * comparisons without object allocations.
 */
class MixedDateLeafComparator implements MixedLeafFieldComparator {
  private static final int DATE_BRACKET_PRIORITY =
      SortUtil.getBracketPriority(BsonType.DATE_TIME, NullEmptySortPosition.LOWEST);

  private final NumericDocValues dv;
  private final long top;
  private long bottom;
  private long curr;

  public MixedDateLeafComparator(CompositeComparator parent, NumericDocValues dv) {
    this.dv = dv;
    // top value is irrelevant if top is not in same bracket.
    this.top =
        parent.getTopBracket() == DATE_BRACKET_PRIORITY
            ? parent.getTop().asDateTime().getValue()
            : 0;
  }

  @Override
  public int getBracketPriority() {
    return DATE_BRACKET_PRIORITY;
  }

  @Override
  public boolean hasValue(int docId) throws IOException {
    if (this.dv.advanceExact(docId)) {
      this.curr = this.dv.longValue();
      return true;
    }
    return false;
  }

  @Override
  public int compareBottomToCurrent() {
    return Long.compare(this.bottom, this.curr);
  }

  @Override
  public int compareTopToCurrent() {
    return Long.compare(this.top, this.curr);
  }

  @Override
  public BsonValue getCurrentValue() {
    return new BsonDateTime(this.curr);
  }

  @Override
  public void notifyNewBottom(BsonValue bottom) {
    this.bottom = bottom.asDateTime().getValue();
  }

  @Override
  public int nextDoc() throws IOException {
    int doc = this.dv.nextDoc();
    if (doc != DocIdSetIterator.NO_MORE_DOCS) {
      this.curr = this.dv.longValue();
    }
    return doc;
  }
}
