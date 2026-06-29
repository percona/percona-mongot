package com.xgen.mongot.index.lucene.codec.flat;

import com.google.auto.service.AutoService;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import com.xgen.mongot.index.lucene.quantization.FlatBitVectorsScorer;
import java.io.IOException;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

/**
 * A {@link KnnVectorsFormat} that supports exhaustive vector search for bit-packed vector types.
 *
 * <p>This format does <b>NOT</b> build or use HNSW graph structures. Instead, it performs
 * exhaustive brute-force search by scanning all vectors in the index to find the k-nearest
 * neighbors.
 *
 * <p>The format delegates to Lucene's {@link Lucene99FlatVectorsFormat} for the underlying storage
 * and retrieval, using {@link FlatBitVectorsScorer} for similarity scoring of bit-packed vectors.
 *
 * @see KnnVectorsFormat
 * @see Lucene99FlatVectorsFormat
 * @see FlatBitVectorsScorer
 */
@AutoService(KnnVectorsFormat.class)
public class FlatBitVectorsFormat extends KnnVectorsFormat {

  private static final String NAME = "FlatBitVectorsFormat";

  private final FlatVectorsFormat format =
      new Lucene99FlatVectorsFormat(new FlatBitVectorsScorer());

  public FlatBitVectorsFormat() {
    super(NAME);
  }

  @Override
  public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
    return new FlatVectorsWriterDecorator(this.format.fieldsWriter(state));
  }

  @Override
  public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
    return new FullScanFlatVectorsReaderDecorator(this.format.fieldsReader(state));
  }

  @Override
  public int getMaxDimensions(String fieldName) {
    // [Changed from Lucene]
    // Changed the max allowed vector dimensions.
    return VectorFieldSpecification.MAX_DIMENSIONS;
  }
}
