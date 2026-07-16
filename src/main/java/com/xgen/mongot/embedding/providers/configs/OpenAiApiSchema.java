package com.xgen.mongot.embedding.providers.configs;

import com.xgen.mongot.util.bson.FloatVector;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseContext;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Wire schema for the OpenAI {@code /v1/embeddings} protocol (OpenAI, Ollama, vLLM, HF TEI,
 * Together, ...). Like {@link VoyageApiSchema} but only the fields common across these servers.
 *
 * <p>float only. Requests ask for {@code encoding_format: base64} (float32 little-endian); we also
 * decode a plain JSON number array, since some servers ignore {@code encoding_format}.
 */
public class OpenAiApiSchema {

  public static final String DEFAULT_ENCODING_FORMAT = "base64";

  public static class EmbedRequest implements DocumentEncodable {
    public static class Fields {
      static final Field.Required<String> MODEL_ID =
          Field.builder("model").stringField().required();
      static final Field.Required<List<String>> INPUT =
          Field.builder("input").stringField().asList().required();
      static final Field.WithDefault<String> ENCODING_FORMAT =
          Field.builder("encoding_format")
              .stringField()
              .optional()
              .withDefault(DEFAULT_ENCODING_FORMAT);
      // only sent when explicitly set; most local engines reject an unexpected dimensions field
      static final Field.Optional<Integer> DIMENSIONS =
          Field.builder("dimensions").intField().optional().noDefault();
    }

    public final String modelId;
    public final List<String> input;
    public final String encodingFormat;
    public final Optional<Integer> dimensions;

    public EmbedRequest(
        String modelId, List<String> input, String encodingFormat, Optional<Integer> dimensions) {
      this.modelId = modelId;
      this.input = input;
      this.encodingFormat = encodingFormat;
      this.dimensions = dimensions;
    }

    public static EmbedRequest fromBson(DocumentParser parser) throws BsonParseException {
      return new EmbedRequest(
          parser.getField(Fields.MODEL_ID).unwrap(),
          parser.getField(Fields.INPUT).unwrap(),
          parser.getField(Fields.ENCODING_FORMAT).unwrap(),
          parser.getField(Fields.DIMENSIONS).unwrap());
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.MODEL_ID, this.modelId)
          .field(Fields.INPUT, this.input)
          .field(Fields.ENCODING_FORMAT, this.encodingFormat)
          .field(Fields.DIMENSIONS, this.dimensions)
          .build();
    }
  }

  public static class EmbedResponse implements DocumentEncodable {
    public static class Fields {
      // OpenAI returns "list"; some servers omit it, so keep it optional.
      static final Field.Optional<String> OBJECT_TYPE =
          Field.builder("object").stringField().optional().noDefault();
      static final Field.Required<List<EmbedVector>> DATA =
          Field.builder("data")
              .classField(EmbedVector::fromBson)
              .allowUnknownFields()
              .asList()
              .required();
      // Some OpenAI-compatible servers (e.g. certain Ollama builds) omit usage.
      static final Field.Optional<EmbedUsage> EMBED_USAGE =
          Field.builder("usage").classField(EmbedUsage::fromBson).allowUnknownFields().optional()
              .noDefault();
    }

    public static EmbedResponse fromBson(DocumentParser parser) throws BsonParseException {
      return new EmbedResponse(
          parser.getField(Fields.OBJECT_TYPE).unwrap(),
          parser.getField(Fields.DATA).unwrap(),
          parser.getField(Fields.EMBED_USAGE).unwrap());
    }

    public final Optional<String> objectType;
    public final List<EmbedVector> data;
    public final Optional<EmbedUsage> usage;

    public EmbedResponse(
        Optional<String> objectType, List<EmbedVector> data, Optional<EmbedUsage> usage) {
      this.objectType = objectType;
      this.data = data;
      this.usage = usage;
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.OBJECT_TYPE, this.objectType)
          .field(Fields.DATA, this.data)
          .field(Fields.EMBED_USAGE, this.usage)
          .build();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EmbedResponse that = (EmbedResponse) o;
      return Objects.equals(this.objectType.orElse(null), that.objectType.orElse(null))
          && this.data.equals(that.data)
          && Objects.equals(this.usage.orElse(null), that.usage.orElse(null));
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.objectType.orElse(null), this.data, this.usage.orElse(null));
    }
  }

  public static class EmbedVector implements DocumentEncodable {
    public static class Fields {
      static final Field.Optional<String> OBJECT_TYPE =
          Field.builder("object").stringField().optional().noDefault();
      static final Field.Required<Vector> EMBEDDING =
          Field.builder("embedding").classField(EmbedVector::decodeVectorFromBsonValue).required();
      static final Field.WithDefault<Integer> INDEX =
          Field.builder("index").intField().optional().withDefault(0);
    }

    public EmbedVector(Optional<String> objectType, Vector embedding, int index) {
      this.objectType = objectType;
      this.embedding = embedding;
      this.index = index;
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.OBJECT_TYPE, this.objectType)
          .field(Fields.EMBEDDING, this.embedding)
          .field(Fields.INDEX, this.index)
          .build();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EmbedVector that = (EmbedVector) o;
      return Objects.equals(this.objectType.orElse(null), that.objectType.orElse(null))
          && this.index == that.index
          && this.embedding.equals(that.embedding);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.objectType.orElse(null), this.embedding, this.index);
    }

    public final Optional<String> objectType;
    public final Vector embedding;
    public final int index;

    public static EmbedVector fromBson(DocumentParser parser) throws BsonParseException {
      return new EmbedVector(
          parser.getField(Fields.OBJECT_TYPE).unwrap(),
          parser.getField(Fields.EMBEDDING).unwrap(),
          parser.getField(Fields.INDEX).unwrap());
    }

    /** Decode the embedding from base64 float32 (what we request) or a plain JSON number array. */
    public static Vector decodeVectorFromBsonValue(BsonParseContext context, BsonValue value)
        throws BsonParseException {
      if (value.isString()) {
        String base64Vector = value.asString().getValue();
        try {
          byte[] decoded = Base64.getDecoder().decode(base64Vector);
          ByteBuffer byteBuffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
          if (byteBuffer.remaining() % Float.BYTES != 0) {
            return context.handleSemanticError(
                "float embedding byte length is not a multiple of 4");
          }
          FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
          float[] vector = new float[floatBuffer.remaining()];
          floatBuffer.get(vector);
          return Vector.fromFloats(vector, FloatVector.OriginalType.NATIVE);
        } catch (IllegalArgumentException e) {
          return context.handleSemanticError("Invalid base64 for embedding");
        }
      }
      // Plain JSON array of numbers (or BSON binary vector) is handled by Vector.fromBson.
      return Vector.fromBson(context, value);
    }
  }

  public static class EmbedUsage implements DocumentEncodable {
    public static class Fields {
      static final Field.Optional<Integer> PROMPT_TOKENS =
          Field.builder("prompt_tokens").intField().optional().noDefault();
      static final Field.Optional<Integer> TOTAL_TOKENS =
          Field.builder("total_tokens").intField().optional().noDefault();
    }

    public static EmbedUsage fromBson(DocumentParser parser) throws BsonParseException {
      return new EmbedUsage(
          parser.getField(Fields.PROMPT_TOKENS).unwrap(),
          parser.getField(Fields.TOTAL_TOKENS).unwrap());
    }

    public EmbedUsage(Optional<Integer> promptTokens, Optional<Integer> totalTokens) {
      this.promptTokens = promptTokens;
      this.totalTokens = totalTokens;
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.PROMPT_TOKENS, this.promptTokens)
          .field(Fields.TOTAL_TOKENS, this.totalTokens)
          .build();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EmbedUsage that = (EmbedUsage) o;
      return Objects.equals(this.promptTokens.orElse(null), that.promptTokens.orElse(null))
          && Objects.equals(this.totalTokens.orElse(null), that.totalTokens.orElse(null));
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.promptTokens.orElse(null), this.totalTokens.orElse(null));
    }

    public final Optional<Integer> promptTokens;
    public final Optional<Integer> totalTokens;
  }
}
