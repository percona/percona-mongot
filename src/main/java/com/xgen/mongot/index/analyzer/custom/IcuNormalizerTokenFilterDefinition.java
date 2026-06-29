package com.xgen.mongot.index.analyzer.custom;

import com.ibm.icu.text.Normalizer2;
import com.xgen.mongot.index.analyzer.attributes.TokenStreamTransformationStage;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Objects;
import org.bson.BsonDocument;

public class IcuNormalizerTokenFilterDefinition extends TokenFilterDefinition
    implements TokenStreamTransformationStage.OutputsSameTypeAsInput {
  static class Fields {
    static final Field.WithDefault<NormalizationForm> NORMALIZATION_FORM =
        Field.builder("normalizationForm")
            .enumField(NormalizationForm.class)
            .asCamelCase()
            .optional()
            .withDefault(NormalizationForm.NFC);
  }

  public enum NormalizationForm {
    NFC,
    NFD,
    NFKC,
    NFKD
  }

  public final NormalizationForm normalizationForm;

  public IcuNormalizerTokenFilterDefinition(NormalizationForm normalizationForm) {
    this.normalizationForm = normalizationForm;
  }

  public static IcuNormalizerTokenFilterDefinition fromBson(DocumentParser parser)
      throws BsonParseException {
    return new IcuNormalizerTokenFilterDefinition(
        parser.getField(Fields.NORMALIZATION_FORM).unwrap());
  }

  @Override
  public Type getType() {
    return Type.ICU_NORMALIZER;
  }

  @Override
  BsonDocument tokenFilterToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.NORMALIZATION_FORM, this.normalizationForm)
        .build();
  }

  /** Gets a corresponding Normalizer2 instance for the chosen normalization form. */
  public Normalizer2 getNormalizer() {
    return switch (this.normalizationForm) {
      case NFC -> Normalizer2.getNFCInstance();
      case NFD -> Normalizer2.getNFDInstance();
      case NFKC -> Normalizer2.getNFKCInstance();
      case NFKD -> Normalizer2.getNFKDInstance();
    };
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }

    if (!(other instanceof IcuNormalizerTokenFilterDefinition otherDefinition)) {
      return false;
    }

    return this.normalizationForm == otherDefinition.normalizationForm;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.normalizationForm);
  }
}
