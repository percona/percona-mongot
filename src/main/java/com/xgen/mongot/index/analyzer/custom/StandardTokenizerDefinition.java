package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTypeAware;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.Range;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.bson.BsonDocument;

public class StandardTokenizerDefinition extends TokenizerDefinition
    implements TokenStreamTypeAware.Stream {
  static class Fields {
    static final Field.Optional<Integer> MAX_TOKEN_LENGTH =
        Field.builder("maxTokenLength")
            .intField()
            .mustBeWithinBounds(Range.of(1, StandardTokenizer.MAX_TOKEN_LENGTH_LIMIT))
            .optional()
            .noDefault();
  }

  public final Optional<Integer> maxTokenLength;

  public StandardTokenizerDefinition(Optional<Integer> maxTokenLength) {
    this.maxTokenLength = maxTokenLength;
  }

  public static StandardTokenizerDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    return new StandardTokenizerDefinition(parser.getField(Fields.MAX_TOKEN_LENGTH).unwrap());
  }

  @Override
  public Type getType() {
    return Type.STANDARD;
  }

  @Override
  BsonDocument tokenizerToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.MAX_TOKEN_LENGTH, this.maxTokenLength)
        .build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof StandardTokenizerDefinition otherDefinition)) {
      return false;
    }
    return Objects.equals(this.maxTokenLength, otherDefinition.maxTokenLength);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.maxTokenLength);
  }
}
