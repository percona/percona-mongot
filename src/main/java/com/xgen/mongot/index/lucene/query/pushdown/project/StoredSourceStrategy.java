package com.xgen.mongot.index.lucene.query.pushdown.project;

import com.google.common.collect.ImmutableSet;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.util.BsonUtils;
import com.xgen.mongot.util.bson.ByteUtils;
import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.util.BytesRef;
import org.bson.RawBsonDocument;
import org.jetbrains.annotations.Nullable;

class StoredSourceStrategy implements ProjectStrategy<RawBsonDocument> {

  private static final RawBsonDocument EMPTY = BsonUtils.emptyDocument();

  private static final String STORED_SOURCE_FIELD =
      FieldName.StaticField.STORED_SOURCE.getLuceneFieldName();

  private static final ImmutableSet<String> STORED_FIELD = ImmutableSet.of(STORED_SOURCE_FIELD);

  private final StoredFields reader;

  StoredSourceStrategy(IndexReader reader) throws IOException {
    this.reader = reader.storedFields();
  }

  @Override
  public RawBsonDocument project(int docId) throws IOException {
    var document = this.reader.document(docId, STORED_FIELD);
    @Nullable BytesRef bytes = document.getBinaryValue(STORED_SOURCE_FIELD);
    if (bytes == null) {
      // we fall back to an empty document if the stored source is not present
      return EMPTY;
    }
    return ByteUtils.fromBytesRef(bytes);
  }
}
