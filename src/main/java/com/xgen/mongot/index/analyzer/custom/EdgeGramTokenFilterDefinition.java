package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTransformationStage;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import org.bson.BsonDocument;

public class EdgeGramTokenFilterDefinition extends TokenFilterDefinition
    implements TokenStreamTransformationStage.AnyToGraph {
  static class Fields {
    static final Field.Required<Integer> MIN_GRAM =
        Field.builder("minGram").intField().mustBeNonNegative().required();

    static final Field.Required<Integer> MAX_GRAM =
        Field.builder("maxGram").intField().mustBeNonNegative().required();

    static final Field.WithDefault<TermNotInBounds> TERM_NOT_IN_BOUNDS =
        Field.builder("termNotInBounds")
            .enumField(TermNotInBounds.class)
            .asCamelCase()
            .optional()
            .withDefault(TermNotInBounds.OMIT);
  }

  public enum TermNotInBounds {
    INCLUDE,
    OMIT
  }

  public final int minGram;
  public final int maxGram;
  public final TermNotInBounds termNotInBounds;

  public EdgeGramTokenFilterDefinition(int minGram, int maxGram, TermNotInBounds termNotInBounds) {
    this.minGram = minGram;
    this.maxGram = maxGram;
    this.termNotInBounds = termNotInBounds;
  }

  /** Deserialize an EdgeGramTokenFilterDefinition. */
  public static EdgeGramTokenFilterDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    int minGram = parser.getField(Fields.MIN_GRAM).unwrap();
    int maxGram = parser.getField(Fields.MAX_GRAM).unwrap();
    TermNotInBounds termNotInBounds = parser.getField(Fields.TERM_NOT_IN_BOUNDS).unwrap();

    if (minGram > maxGram) {
      parser.getContext().handleSemanticError("minGram must not be greater than maxGram");
    }

    return new EdgeGramTokenFilterDefinition(minGram, maxGram, termNotInBounds);
  }

  @Override
  public Type getType() {
    return Type.EDGE_GRAM;
  }

  @Override
  BsonDocument tokenFilterToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.MIN_GRAM, this.minGram)
        .field(Fields.MAX_GRAM, this.maxGram)
        .field(Fields.TERM_NOT_IN_BOUNDS, this.termNotInBounds)
        .build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof EdgeGramTokenFilterDefinition otherDefinition)) {
      return false;
    }
    return (this.minGram == otherDefinition.minGram)
        && (this.maxGram == otherDefinition.maxGram)
        && (this.termNotInBounds == otherDefinition.termNotInBounds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.minGram, this.maxGram, this.termNotInBounds);
  }
}
