package com.xgen.mongot.index.lucene.query.sort.mixed;

import java.io.IOException;
import org.apache.lucene.index.SortedDocValues;
import org.bson.BsonValue;

class MixedNullLeafComparator implements MixedLeafFieldComparator {

  private final SortedDocValues dv;
  private final int bracketPriority;
  private final BsonValue nullMissingSortValue;

  MixedNullLeafComparator(SortedDocValues dv, int bracketPriority, BsonValue nullMissingSortValue) {
    // Can avoid ord lookup because the column contains only nulls, so there is only one ordinal, 0
    this.dv = dv;
    this.bracketPriority = bracketPriority;
    this.nullMissingSortValue = nullMissingSortValue;
  }

  @Override
  public int getBracketPriority() {
    return this.bracketPriority;
  }

  @Override
  public boolean hasValue(int doc) throws IOException {
    return this.dv.advanceExact(doc);
  }

  @Override
  public int compareBottomToCurrent() {
    // Called after hasValue returns true

    // Column contains only nulls, so there is only one ordinal, 0
    // Hence bottom and current ords are always equal
    return 0;
  }

  @Override
  public int compareTopToCurrent() {
    // Called after hasValue returns true and after top is verified to be in same bracket
    return 0;
  }

  @Override
  public BsonValue getCurrentValue() throws IOException {
    // Called after hasValue returns true
    return this.nullMissingSortValue;
  }

  @Override
  public void notifyNewBottom(BsonValue bottom) {
    // No need to keep track of a bottom ord since there is only one ordinal in the column, 0
  }

  @Override
  public int nextDoc() throws IOException {
    return this.dv.nextDoc();
  }
}
