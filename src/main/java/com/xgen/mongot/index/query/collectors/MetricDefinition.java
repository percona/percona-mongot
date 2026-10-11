package com.xgen.mongot.index.query.collectors;

import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import org.bson.BsonDocument;

/**
 * A single numeric metric requested through the {@link MetricsCollector}: an aggregation {@link
 * Type} computed over the indexed numeric values found at {@code path} across all documents matched
 * by the collector's operator.
 */
public record MetricDefinition(Type type, String path) implements DocumentEncodable {

  private static class Fields {
    private static final Field.Required<Type> TYPE =
        Field.builder("type").enumField(Type.class).asCamelCase().required();

    private static final Field.Required<String> PATH =
        Field.builder("path").stringField().required();
  }

  /** Supported aggregations. Please keep in alphabetical order. */
  public enum Type {
    AVG,
    MAX,
    MIN,
    SUM
  }

  public static MetricDefinition fromBson(DocumentParser parser) throws BsonParseException {
    return new MetricDefinition(
        parser.getField(Fields.TYPE).unwrap(), parser.getField(Fields.PATH).unwrap());
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.TYPE, this.type)
        .field(Fields.PATH, this.path)
        .build();
  }
}
