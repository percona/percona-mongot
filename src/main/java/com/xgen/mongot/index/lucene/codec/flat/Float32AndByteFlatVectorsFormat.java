package com.xgen.mongot.index.lucene.codec.flat;

import com.google.auto.service.AutoService;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import java.io.IOException;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.DefaultFlatVectorScorer;
import org.apache.lucene.codecs.hnsw.FlatVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99FlatVectorsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

/**
 * A {@link KnnVectorsFormat} that supports exhaustive vector search for both float32 and byte
 * vector types.
 *
 * <p>This format does <b>NOT</b> build or use HNSW graph structures. Instead, it performs
 * exhaustive brute-force search by scanning all vectors in the index to find the k-nearest
 * neighbors.
 *
 * <p>The format delegates to Lucene's {@link Lucene99FlatVectorsFormat} for the underlying storage
 * and retrieval, using {@link DefaultFlatVectorScorer} for similarity scoring.
 *
 * @see KnnVectorsFormat
 * @see Lucene99FlatVectorsFormat
 */
@AutoService(KnnVectorsFormat.class)
public class Float32AndByteFlatVectorsFormat extends KnnVectorsFormat {

  private static final String NAME = "Float32AndByteFlatVectorsFormat";

  private final FlatVectorsFormat format =
      new Lucene99FlatVectorsFormat(DefaultFlatVectorScorer.INSTANCE);

  public Float32AndByteFlatVectorsFormat() {
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
