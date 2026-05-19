package com.xgen.mongot.replication.mongodb.common;

import com.mongodb.MongoNamespace;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import org.bson.BsonDocument;

/** ChangeStreamResumeInfo holds information regarding how to resume a change stream. */
public class ChangeStreamResumeInfo implements DocumentEncodable {

  private static class Fields {
    private static final Field.Required<String> NAMESPACE =
        Field.builder("namespace").stringField().required();
    private static final Field.Required<BsonDocument> RESUME_TOKEN =
        Field.builder("resumeToken").documentField().required();
  }

  private final MongoNamespace namespace;
  private final BsonDocument resumeToken;

  private ChangeStreamResumeInfo(MongoNamespace namespace, BsonDocument resumeToken) {
    this.namespace = namespace;
    this.resumeToken = resumeToken;
  }

  public MongoNamespace getNamespace() {
    return this.namespace;
  }

  public BsonDocument getResumeToken() {
    return this.resumeToken;
  }

  /** Creates a new ChangeStreamResumeInfo with the supplied namespace and resumeToken. */
  public static ChangeStreamResumeInfo create(MongoNamespace namespace, BsonDocument resumeToken) {
    return new ChangeStreamResumeInfo(namespace, resumeToken);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (!(obj instanceof ChangeStreamResumeInfo other)) {
      return false;
    }
    return other.namespace.equals(this.namespace) && other.resumeToken.equals(this.resumeToken);
  }

  @Override
  public int hashCode() {
    return this.namespace.hashCode() + this.resumeToken.hashCode();
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.NAMESPACE, this.namespace.getFullName())
        .field(Fields.RESUME_TOKEN, this.resumeToken)
        .build();
  }

  public static ChangeStreamResumeInfo fromBson(DocumentParser parser) throws BsonParseException {
    return new ChangeStreamResumeInfo(
        new MongoNamespace(parser.getField(Fields.NAMESPACE).unwrap()),
        parser.getField(Fields.RESUME_TOKEN).unwrap());
  }
}
