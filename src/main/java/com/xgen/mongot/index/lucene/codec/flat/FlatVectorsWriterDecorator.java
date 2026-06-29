package com.xgen.mongot.index.lucene.codec.flat;

import java.io.IOException;
import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.Sorter;

/**
 * Writer implementation for flat vector format.
 *
 * <p>This writer delegates to Lucene's {@link FlatVectorsWriter} to persist vector data <b>without
 * building any HNSW graph structure or other indexing structures</b>. Vectors are stored in a
 * simple flat format optimized for sequential scanning. Supports both float32 and byte vector
 * types.
 */
class FlatVectorsWriterDecorator extends KnnVectorsWriter {

  private final FlatVectorsWriter writer;

  FlatVectorsWriterDecorator(FlatVectorsWriter writer) {
    super();
    this.writer = writer;
  }

  @Override
  public KnnFieldVectorsWriter<?> addField(FieldInfo fieldInfo) throws IOException {
    return this.writer.addField(fieldInfo);
  }

  @Override
  public void flush(int maxDoc, Sorter.DocMap sortMap) throws IOException {
    this.writer.flush(maxDoc, sortMap);
  }

  @Override
  public void finish() throws IOException {
    this.writer.finish();
  }

  @Override
  public void close() throws IOException {
    this.writer.close();
  }

  @Override
  public long ramBytesUsed() {
    return this.writer.ramBytesUsed();
  }

  @Override
  public void mergeOneField(FieldInfo fieldInfo, MergeState mergeState) throws IOException {
    this.writer.mergeOneField(fieldInfo, mergeState);
  }
}
