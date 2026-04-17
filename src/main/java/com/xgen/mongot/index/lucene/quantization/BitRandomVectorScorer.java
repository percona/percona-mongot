package com.xgen.mongot.index.lucene.quantization;

import com.xgen.mongot.index.lucene.util.VectorSearchUtil;
import java.io.IOException;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.hnsw.RandomVectorScorer;

public class BitRandomVectorScorer implements RandomVectorScorer {
  private final ByteVectorValues vectorValues;

  /** Number of bits to consider when scoring (i.e. query.length * 8 - padding) */
  private final int bitDimensions;

  /** A densely packed bit vector */
  private final byte[] query;

  /**
   * Create a VectorScorer for hnsw traversal over pre-quantized and auto-quantized bitvectors.
   *
   * @param vectorValues an iterator over densely packed bit vectors
   * @param query a bit vector densely packed into a byte[] to score against
   */
  BitRandomVectorScorer(ByteVectorValues vectorValues, byte[] query) {
    this.query = query;
    // Note: scaling bitDimensions is correct and necessary to avoid negative scores on
    // pre-quantized BitVectors. For auto-quantized bit vectors, it incorrectly scales dimensions
    // twice, but this fine because it's a rank-preserving transformation, even in float32 space.
    this.bitDimensions = vectorValues.dimension() * Byte.SIZE;
    this.vectorValues = vectorValues;
    assert query.length <= vectorValues.dimension();
  }

  @Override
  public float score(int node) throws IOException {
    return VectorSearchUtil.bitSimilarity(
        this.query, this.vectorValues.vectorValue(node), this.bitDimensions);
  }

  @Override
  public int maxOrd() {
    return this.vectorValues.size();
  }

  @Override
  public int ordToDoc(int ord) {
    return this.vectorValues.ordToDoc(ord);
  }

  @Override
  public Bits getAcceptOrds(Bits acceptDocs) {
    return this.vectorValues.getAcceptOrds(acceptDocs);
  }
}
