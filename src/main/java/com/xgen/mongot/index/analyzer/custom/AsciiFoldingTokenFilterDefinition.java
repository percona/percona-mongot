package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTransformationStage;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import org.bson.BsonDocument;

public class AsciiFoldingTokenFilterDefinition extends TokenFilterDefinition
    implements TokenStreamTransformationStage.OutputsSameTypeAsInput {
  static class Fields {
    private static final Field.WithDefault<OriginalTokens> ORIGINAL_TOKENS =
        Field.builder("originalTokens")
            .enumField(OriginalTokens.class)
            .asCamelCase()
            .optional()
            .withDefault(OriginalTokens.OMIT);
  }

  public final OriginalTokens outputOption;

  public AsciiFoldingTokenFilterDefinition(OriginalTokens outputOption) {
    this.outputOption = outputOption;
  }

  /** Deserialize an AsciiFoldingTokenFilterDefinition. */
  public static AsciiFoldingTokenFilterDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    return new AsciiFoldingTokenFilterDefinition(parser.getField(Fields.ORIGINAL_TOKENS).unwrap());
  }

  @Override
  public Type getType() {
    return Type.ASCII_FOLDING;
  }

  @Override
  BsonDocument tokenFilterToBson() {
    return BsonDocumentBuilder.builder().field(Fields.ORIGINAL_TOKENS, this.outputOption).build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof AsciiFoldingTokenFilterDefinition otherDefinition)) {
      return false;
    }

    return (this.outputOption == otherDefinition.outputOption);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.outputOption);
  }
}
