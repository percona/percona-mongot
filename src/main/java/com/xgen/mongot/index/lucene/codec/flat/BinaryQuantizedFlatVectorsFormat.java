package com.xgen.mongot.index.lucene.codec.flat;

import com.google.auto.service.AutoService;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import com.xgen.mongot.index.lucene.quantization.Mongot01042BinaryQuantizedFlatVectorsFormat;
import java.io.IOException;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

/**
 * A {@link KnnVectorsFormat} that supports exhaustive vector search with binary quantization.
 *
 * <p>This format does <b>NOT</b> build or use HNSW graph structures. Instead, it performs
 * exhaustive brute-force search by scanning all vectors in the index to find the k-nearest
 * neighbors.
 *
 * <p>The format delegates to {@link Mongot01042BinaryQuantizedFlatVectorsFormat} for the underlying
 * storage and retrieval, which applies binary quantization to reduce memory footprint
 *
 * @see KnnVectorsFormat
 * @see Mongot01042BinaryQuantizedFlatVectorsFormat
 */
@AutoService(KnnVectorsFormat.class)
public class BinaryQuantizedFlatVectorsFormat extends KnnVectorsFormat {

  private static final String NAME = "BinaryQuantizedFlatVectorsFormat";

  private final FlatVectorsFormat format = new Mongot01042BinaryQuantizedFlatVectorsFormat();

  public BinaryQuantizedFlatVectorsFormat() {
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
