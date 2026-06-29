package com.xgen.mongot.index.lucene.query.sort.mixed;

import com.xgen.mongot.index.lucene.field.FieldValue;
import com.xgen.mongot.index.query.sort.NullEmptySortPosition;
import java.io.IOException;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;
import org.bson.BsonBoolean;
import org.bson.BsonType;
import org.bson.BsonValue;

class MixedBooleanLeafComparator implements MixedLeafFieldComparator {

  private static final int BOOLEAN_BRACKET_PRIORITY =
      SortUtil.getBracketPriority(BsonType.BOOLEAN, NullEmptySortPosition.LOWEST);

  private final int trueOrd;

  private final int topOrd;
  private final SortedDocValues dv;
  private int bottomOrd;

  MixedBooleanLeafComparator(CompositeComparator parent, SortedDocValues dv) throws IOException {
    // Do ord lookup once per segment, rather than per comparison

    // Column is either always true, always false, or contains true & false
    // If always false or true & false, use ords = {'F': 0, 'T': 1}
    // If always true, use ords = {'F': -1, 'T': 0}
    // This creates invariant: ord(false) = ord(true) - 1, which makes comparison below fast
    this.trueOrd =
        dv.lookupTerm(new BytesRef(FieldValue.BOOLEAN_TRUE_FIELD_VALUE)) == 0
            // column is always true {'T': 0}
            ? 0
            // column is always false, but we'll pretend true = 1, or column has true & false i.e.
            // {'F': 0, 'T': 1}
            : 1;
    this.topOrd =
        parent.getTopBracket() == BOOLEAN_BRACKET_PRIORITY
            ? parent.getTop().asBoolean().getValue() ? this.trueOrd : this.trueOrd - 1
            : -1;
    this.dv = dv;
  }

  @Override
  public int getBracketPriority() {
    return BOOLEAN_BRACKET_PRIORITY;
  }

  @Override
  public boolean hasValue(int doc) throws IOException {
    return this.dv.advanceExact(doc);
  }

  @Override
  public int compareBottomToCurrent() throws IOException {
    // Called after hasValue returns true
    return Integer.compare(this.bottomOrd, this.dv.ordValue());
  }

  @Override
  public int compareTopToCurrent() throws IOException {
    // Called after hasValue returns true
    return Integer.compare(this.topOrd, this.dv.ordValue());
  }

  @Override
  public BsonValue getCurrentValue() throws IOException {
    // called after hasValue returns true
    return BsonBoolean.valueOf(this.dv.ordValue() == this.trueOrd);
  }

  @Override
  public void notifyNewBottom(BsonValue bottom) throws IOException {
    // Invariant: ord(false) = ord(true) - 1
    this.bottomOrd = bottom.asBoolean().getValue() ? this.trueOrd : this.trueOrd - 1;
  }

  @Override
  public int nextDoc() throws IOException {
    return this.dv.nextDoc();
  }
}
