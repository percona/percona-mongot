package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTypeAware;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bson.BsonDocument;

public class RegexSplitTokenizerDefinition extends TokenizerDefinition
    implements TokenStreamTypeAware.Stream {
  static class Fields {
    static final Field.Required<String> PATTERN = Field.builder("pattern").stringField().required();
  }

  public final Pattern pattern;

  public RegexSplitTokenizerDefinition(Pattern pattern) {
    this.pattern = pattern;
  }

  /** Deserialize a RegexSplitTokenizerDefinition. */
  public static RegexSplitTokenizerDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    String strPattern = parser.getField(Fields.PATTERN).unwrap();

    Pattern pattern;

    try {
      // check that the pattern is valid
      pattern = Pattern.compile(strPattern);
    } catch (PatternSyntaxException ex) {
      return parser.getContext().handleSemanticError(ex.getMessage());
    }

    return new RegexSplitTokenizerDefinition(pattern);
  }

  @Override
  public Type getType() {
    return Type.REGEX_SPLIT;
  }

  @Override
  BsonDocument tokenizerToBson() {
    return BsonDocumentBuilder.builder().field(Fields.PATTERN, this.pattern.pattern()).build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof RegexSplitTokenizerDefinition otherDefinition)) {
      return false;
    }

    // Pattern::equals() currently only tests identity - compare underlying string instead
    return Objects.equals(this.pattern.pattern(), otherDefinition.pattern.pattern());
  }

  @Override
  public int hashCode() {
    // Pattern::hashCode() currently only tests identity - compare underlying string instead
    return Objects.hash(this.pattern.pattern());
  }
}
