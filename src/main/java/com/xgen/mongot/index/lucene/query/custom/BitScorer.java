package com.xgen.mongot.index.lucene.query.custom;

import com.xgen.mongot.index.lucene.util.VectorSearchUtil;
import com.xgen.mongot.util.bson.BitVector;
import java.io.IOException;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.KnnVectorValues.DocIndexIterator;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.VectorScorer;

class BitScorer implements VectorScorer {

  private final ByteVectorValues values;
  private final DocIndexIterator itr;
  private final byte[] target;
  private final int numDimensions;

  BitScorer(ByteVectorValues values, BitVector target) {
    this.values = values;
    this.itr = values.iterator();
    this.target = target.getBitVector();
    this.numDimensions = target.numDimensions();
  }

  @Override
  public float score() throws IOException {
    byte[] bytes = this.values.vectorValue(this.itr.docID());
    return VectorSearchUtil.bitSimilarity(this.target, bytes, this.numDimensions);
  }

  @Override
  public DocIdSetIterator iterator() {
    return this.itr;
  }
}
