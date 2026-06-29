/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xgen.mongot.index.lucene.facet;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PrimitiveIterator;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.TopOrdAndIntQueue;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.util.BytesRef;

/**
 * Base class for faceting implementations.
 *
 * <p>NOTE(guanlinzhou): This file copied from Lucene (version 9.11): {@link
 * org.apache.lucene.facet.sortedset.AbstractSortedSetDocValueFacetCounts}, modified only at line 89
 * and 108 to be able to use `token` fields for faceting. Lucene's version expects strings to appear
 * in a specific format and slices the string to perform faceting. This class makes no such
 * assumption, and uses the entire string value for faceting.
 */
abstract class AbstractSortedSetDocValuesFacetCounts extends Facets {
  final SortedSetDocValuesReaderState state;
  final SortedSetDocValues dv;
  final String field;

  AbstractSortedSetDocValuesFacetCounts(SortedSetDocValuesReaderState state) throws IOException {
    this.field = state.getField();
    this.dv = state.getDocValues();
    this.state = state;
  }

  @Override
  public FacetResult getTopChildren(@Var int topN, String dim, String... path) throws IOException {
    validateTopN(topN);
    validateDimAndPathForGetChildren(dim);

    topN = Math.min(topN, (int) this.dv.getValueCount());
    @Var TopOrdAndIntQueue q = null;
    @Var TopOrdAndIntQueue.OrdAndInt reuse = null;
    @Var int bottomCount = 0;
    @Var int bottomOrd = Integer.MAX_VALUE;
    @Var int childCount = 0; // total number of labels with non-zero count
    @Var int totalDocCount = 0;
    PrimitiveIterator.OfInt ordIterator = getOrdIterator();
    while (ordIterator.hasNext()) {
      int ord = ordIterator.nextInt();
      int count = getCount(ord);
      totalDocCount += count;
      if (count != 0) {
        childCount++;
        if (count > bottomCount || (count == bottomCount && ord < bottomOrd)) {
          if (q == null) {
            // Lazy init for sparse case:
            q = new TopOrdAndIntQueue(topN);
          }
          if (reuse == null) {
            reuse = new TopOrdAndIntQueue.OrdAndInt();
          }
          reuse.ord = ord;
          reuse.value = count;
          reuse = (TopOrdAndIntQueue.OrdAndInt) q.insertWithOverflow(reuse);
          if (q.size() == topN) {
            bottomCount = ((TopOrdAndIntQueue.OrdAndInt) q.top()).value;
            bottomOrd = q.top().ord;
          }
        }
      }
    }

    int resultCount = q == null ? 0 : q.size();
    LabelAndValue[] labelValues = new LabelAndValue[resultCount];
    if (q != null) {
      for (int i = labelValues.length - 1; i >= 0; i--) {
        TopOrdAndIntQueue.OrdAndInt ordAndInt = (TopOrdAndIntQueue.OrdAndInt) q.pop();
        BytesRef term = this.dv.lookupOrd(ordAndInt.ord);
        labelValues[i] = new LabelAndValue(term.utf8ToString(), ordAndInt.value);
      }
    }

    return new FacetResult(this.field, new String[0], totalDocCount, labelValues, childCount);
  }

  @Override
  public FacetResult getAllChildren(String dim, String... path) throws IOException {
    validateDimAndPathForGetChildren(dim);

    List<LabelAndValue> labelValues = new ArrayList<>();
    @Var int totalDocCount = 0;
    PrimitiveIterator.OfInt ordIterator = getOrdIterator();

    while (ordIterator.hasNext()) {
      int ord = ordIterator.nextInt();
      int count = getCount(ord);
      totalDocCount += count;
      if (count != 0) {
        BytesRef term = this.dv.lookupOrd(ord);
        labelValues.add(new LabelAndValue(term.utf8ToString(), count));
      }
    }

    return new FacetResult(
        this.field,
        new String[0],
        totalDocCount,
        labelValues.toArray(new LabelAndValue[0]),
        labelValues.size());
  }

  @Override
  public Number getSpecificValue(String dim, String... path) throws IOException {
    if (path.length != 1) {
      throw new IllegalArgumentException("path must be length=1");
    }
    int ord = (int) this.dv.lookupTerm(new BytesRef(path[0]));
    if (ord < 0) {
      return -1;
    }

    return !hasCounts() ? 0 : getCount(ord);
  }

  @Override
  public List<FacetResult> getAllDims(int topN) throws IOException {
    validateTopN(topN);
    return Collections.singletonList(getTopChildren(topN, this.field));
  }

  /** Were any counts actually computed? (They may not be if there are no hits, etc.) */
  abstract boolean hasCounts();

  /** Retrieve the count for a specified ordinal. */
  abstract int getCount(int ord);

  abstract PrimitiveIterator.OfInt getOrdIterator();

  private void validateDimAndPathForGetChildren(String dim) {
    if (!dim.equals(this.field)) {
      throw new IllegalArgumentException(
          "invalid dim \"" + dim + "\"; should be \"" + this.field + "\"");
    }
  }
}
