package com.xgen.mongot.index.lucene.query.sort.mixed;

import com.xgen.mongot.index.query.sort.NullEmptySortPosition;
import java.io.IOException;
import org.bson.BsonType;
import org.bson.BsonValue;

/**
 * An implementation of this interface is responsible for reading from a single column and
 * performing (possibly optimized) same-bracket comparisons. Note that some sort brackets are
 * composed of multiple columns and therefore multiple instances of this interface.
 */
interface MixedLeafFieldComparator {

  /**
   * Returns the sort priority of BsonTypes in this field. This function is used to prune
   * unnecessary calls to compareTop/compareBottom
   *
   * <p>See {@link SortUtil#getBracketPriority(BsonType, NullEmptySortPosition)}
   */
  int getBracketPriority();

  /**
   * Returns true if a value exists for the document for the given ID.
   *
   * <p>Must be called before {@link #compareBottomToCurrent()}, {@link #compareTopToCurrent()}, and
   * {@link #getCurrentValue()}
   */
  boolean hasValue(int docId) throws IOException;

  /**
   * Determine if the current document is competitive to the current bottom.
   *
   * <p>This function is only called if bottom is set, {@link #hasValue(int)} returned true, and
   * bottom is in the same bracket as {@link #getBracketPriority()}.
   *
   * @return -1 if bottom is lower, 0 if equal, 1 if bottom is larger than current doc
   */
  int compareBottomToCurrent() throws IOException;

  /**
   * Determine if the current document is competitive to the current top.
   *
   * <p>This function is only called if top is set, {@link #hasValue(int)} returned true, and top is
   * in the same bracket as {@link #getBracketPriority()}.
   *
   * @return -1 if top is lower, 0 if equal, 1 if top is larger than current doc
   */
  int compareTopToCurrent() throws IOException;

  /**
   * Reads the current docValue and wraps it in a BsonValue.
   *
   * <p>This function is only called if {@link #hasValue(int)} returned true.
   */
  BsonValue getCurrentValue() throws IOException;

  /**
   * Called whenever we encounter a new bottom or there is an existing bottom when a new
   * LeafComparator is constructed.
   *
   * <p>This method is used to cache information that may be required to implement efficient {@link
   * #compareBottomToCurrent()}. This method is only called if the new bottom is in the same bracket
   * of this comparator.
   */
  void notifyNewBottom(BsonValue bottom) throws IOException;

  /**
   * Advances to the next document that has a value in this column. Returns {@link
   * org.apache.lucene.search.DocIdSetIterator#NO_MORE_DOCS} when exhausted.
   *
   * <p>After this returns a valid doc ID, {@link #getCurrentValue()} is available.
   *
   * <p><b>Iterator interaction:</b> both {@code nextDoc()} and {@link #hasValue(int)} advance the
   * same underlying doc-value iterator. Callers must not mix the two on the same comparator
   * instance — use {@code nextDoc()} for sequential scans and {@link #hasValue(int)} for
   * random-access lookups.
   */
  int nextDoc() throws IOException;
}
