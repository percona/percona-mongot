package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTransformationStage;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import org.bson.BsonDocument;

public class LengthTokenFilterDefinition extends TokenFilterDefinition
    implements TokenStreamTransformationStage.OutputsSameTypeAsInput {
  static class Fields {
    static final Field.WithDefault<Integer> MIN =
        Field.builder("min").intField().mustBeNonNegative().optional().withDefault(0);

    static final Field.WithDefault<Integer> MAX =
        Field.builder("max").intField().mustBeNonNegative().optional().withDefault(255);
  }

  public final int min;
  public final int max;

  public LengthTokenFilterDefinition(int min, int max) {
    this.min = min;
    this.max = max;
  }

  /** Deserialize a LengthTokenFilterDefinition. */
  public static LengthTokenFilterDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    int min = parser.getField(Fields.MIN).unwrap();
    int max = parser.getField(Fields.MAX).unwrap();

    if (min > max) {
      // Field 'max' has a default value of 255. If min is greater than 255 and max is not set, tell
      // the user that they must manually set the 'max' value (do not tell users "min must not be
      // greater than max" when max is unspecified).
      if (parser.getField(Fields.MAX).isEmpty()) {
        parser
            .getContext()
            .handleSemanticError("max must be explicitly defined when min is greater than 255");
      }
      parser.getContext().handleSemanticError("min must not be greater than max");
    }

    return new LengthTokenFilterDefinition(min, max);
  }

  @Override
  public Type getType() {
    return Type.LENGTH;
  }

  @Override
  BsonDocument tokenFilterToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.MIN, this.min)
        .field(Fields.MAX, this.max)
        .build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof LengthTokenFilterDefinition otherDefinition)) {
      return false;
    }
    return (this.min == otherDefinition.min) && (this.max == otherDefinition.max);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.min, this.max);
  }
}
