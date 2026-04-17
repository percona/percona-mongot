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

// NOTE(corecursion): This file copied from Lucene's: ScalarQuantizer.java

package com.xgen.mongot.index.lucene.quantization;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import com.google.errorprone.annotations.Var;
import java.io.IOException;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues.DocIndexIterator;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.util.quantization.ScalarQuantizer;

public class BinaryQuantizer extends ScalarQuantizer {
  public BinaryQuantizer(float minQuantile, float maxQuantile) {
    // CLOUDP-289670: work around Lucene ScalarQuantizer division-by-zero bug
    // This (?:) logic can be removed from mongot when we upgrade to Lucene 11+.
    super(
        minQuantile == maxQuantile ? minQuantile - 1 : minQuantile,
        minQuantile == maxQuantile ? maxQuantile + 1 : maxQuantile,
        (byte) 1);
    // Starting in Lucene 11 this constructor can be changed to this:
    //    super(minQuantile, maxQuantile, (byte) 1);
  }

  @Override
  public float quantize(float[] src, byte[] dest, VectorSimilarityFunction similarityFunction) {
    assert src.length == dest.length;
    if (dest != null) {
      for (int i = 0; i < src.length; i++) {
        dest[i] = (src[i] > 0.0 ? (byte) 1 : (byte) 0);
      }
    }
    return 0f;
  }

  @Override
  public float recalculateCorrectiveOffset(
      byte[] quantizedVector,
      ScalarQuantizer oldQuantizer,
      VectorSimilarityFunction similarityFunction) {
    // Corrective offsets aren't necessary with single bit quantization, and they are not used for
    // scoring.
    return 0f;
  }

  static BinaryQuantizer fromVectors(FloatVectorValues floatVectorValues, int totalVectorCount)
      throws IOException {
    if (totalVectorCount == 0) {
      return new BinaryQuantizer(0f, 0f);
    }
    // TODO(corecursion): can we just provide dummy (0, 1?) values for these min and max quantiles
    @Var float min = Float.POSITIVE_INFINITY;
    @Var float max = Float.NEGATIVE_INFINITY;
    DocIndexIterator iterator = floatVectorValues.iterator();
    while (iterator.nextDoc() != NO_MORE_DOCS) {
      for (float v : floatVectorValues.vectorValue(iterator.index())) {
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }
    return new BinaryQuantizer(min, max);
  }
}
