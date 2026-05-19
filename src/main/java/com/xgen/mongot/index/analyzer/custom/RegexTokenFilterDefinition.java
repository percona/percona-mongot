package com.xgen.mongot.index.analyzer.custom;

import com.xgen.mongot.index.analyzer.attributes.TokenStreamTransformationStage;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bson.BsonDocument;

public class RegexTokenFilterDefinition extends TokenFilterDefinition
    implements TokenStreamTransformationStage.OutputsSameTypeAsInput {
  static class Fields {
    static final Field.Required<String> PATTERN = Field.builder("pattern").stringField().required();

    static final Field.Required<String> REPLACEMENT =
        Field.builder("replacement").stringField().required();

    static final Field.Required<Matches> MATCHES =
        Field.builder("matches").enumField(Matches.class).asCamelCase().required();
  }

  public enum Matches {
    ALL,
    FIRST
  }

  public final Pattern pattern;
  public final String replacement;
  public final Matches matches;

  public RegexTokenFilterDefinition(Pattern pattern, String replacement, Matches matches) {
    this.pattern = pattern;
    this.replacement = replacement;
    this.matches = matches;
  }

  /** Deserialize a RegexTokenFilterDefinition. */
  public static RegexTokenFilterDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    String strPattern = parser.getField(Fields.PATTERN).unwrap();
    Pattern pattern;

    try {
      // ensure the pattern is valid
      pattern = Pattern.compile(strPattern);
    } catch (PatternSyntaxException ex) {
      return parser.getContext().handleSemanticError(ex.getMessage());
    }

    String replacement = parser.getField(Fields.REPLACEMENT).unwrap();
    Matches matches = parser.getField(Fields.MATCHES).unwrap();

    return new RegexTokenFilterDefinition(pattern, replacement, matches);
  }

  @Override
  public Type getType() {
    return Type.REGEX;
  }

  @Override
  BsonDocument tokenFilterToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.PATTERN, this.pattern.pattern())
        .field(Fields.REPLACEMENT, this.replacement)
        .field(Fields.MATCHES, this.matches)
        .build();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof RegexTokenFilterDefinition otherDefinition)) {
      return false;
    }

    // Pattern::equals() currently only tests identity - compare underlying string instead
    return Objects.equals(this.pattern.pattern(), otherDefinition.pattern.pattern())
        && Objects.equals(this.replacement, otherDefinition.replacement)
        && this.matches == otherDefinition.matches;
  }

  @Override
  public int hashCode() {
    // Pattern::hashCode() currently only tests identity - compare underlying string instead
    return Objects.hash(this.pattern.pattern(), this.replacement, this.matches);
  }
}
