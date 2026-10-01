package com.xgen.mongot.embedding.providers.configs;

import com.xgen.mongot.util.bson.FloatVector;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.BSONException;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;

/**
 * Wire schema for the Hugging Face feature-extraction API: the hf-inference pipeline behind the
 * Inference Providers router ({@code POST /hf-inference/models/{id}/pipeline/feature-extraction})
 * and TEI's native {@code POST /embed}, which share the same shape.
 *
 * <p>Request: {@code {"inputs": [...], "normalize": bool, "truncate": bool}}. Response: a bare JSON
 * array with one float array per input, in request order (no index, no usage).
 */
public class HuggingFaceApiSchema {

  public static class EmbedRequest implements DocumentEncodable {
    public static class Fields {
      static final Field.Required<List<String>> INPUTS =
          Field.builder("inputs").stringField().asList().required();
      static final Field.Optional<Boolean> NORMALIZE =
          Field.builder("normalize").booleanField().optional().noDefault();
      static final Field.Optional<Boolean> TRUNCATE =
          Field.builder("truncate").booleanField().optional().noDefault();
    }

    public final List<String> inputs;
    public final Optional<Boolean> normalize;
    public final Optional<Boolean> truncate;

    public EmbedRequest(
        List<String> inputs, Optional<Boolean> normalize, Optional<Boolean> truncate) {
      this.inputs = inputs;
      this.normalize = normalize;
      this.truncate = truncate;
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.INPUTS, this.inputs)
          .field(Fields.NORMALIZE, this.normalize)
          .field(Fields.TRUNCATE, this.truncate)
          .build();
    }
  }

  /** The response body doesn't have the expected {@code [[float, ...], ...]} shape. */
  public static class MalformedResponseException extends Exception {
    private final boolean tokenLevelOutput;

    MalformedResponseException(String message, boolean tokenLevelOutput) {
      super(message);
      this.tokenLevelOutput = tokenLevelOutput;
    }

    /**
     * True when the server returned per-token vectors ({@code [[[...]]]}): the model has no pooling
     * layer, so every retry gets the same unusable result.
     */
    public boolean isTokenLevelOutput() {
      return this.tokenLevelOutput;
    }
  }

  /** Decode a feature-extraction response body into one float vector per input. */
  public static List<Vector> decodeEmbeddings(String body) throws MalformedResponseException {
    BsonArray outer;
    try {
      outer = BsonArray.parse(body);
    } catch (JsonParseException | BSONException e) {
      // JsonParseException: not JSON at all (e.g. an HTML error page from a proxy);
      // BSONException (BsonInvalidOperationException): valid JSON but an object root.
      throw new MalformedResponseException(
          "Embedding response is not a JSON array: " + e.getMessage(), false);
    }
    List<Vector> vectors = new ArrayList<>(outer.size());
    for (int i = 0; i < outer.size(); i++) {
      BsonValue item = outer.get(i);
      if (!item.isArray()) {
        throw new MalformedResponseException(
            "Embedding response element " + i + " is not an array", false);
      }
      BsonArray values = item.asArray();
      if (values.isEmpty()) {
        throw new MalformedResponseException(
            "Embedding response element " + i + " is empty", false);
      }
      if (values.get(0).isArray()) {
        throw new MalformedResponseException(
            "Model returned token-level embeddings (one vector per token) instead of one pooled"
                + " vector per input; use a sentence-embedding model with a pooling layer",
            true);
      }
      float[] floats = new float[values.size()];
      for (int j = 0; j < values.size(); j++) {
        BsonValue value = values.get(j);
        if (!value.isNumber()) {
          throw new MalformedResponseException(
              "Embedding response element " + i + " has a non-numeric value at " + j, false);
        }
        floats[j] = (float) value.asNumber().doubleValue();
      }
      vectors.add(Vector.fromFloats(floats, FloatVector.OriginalType.NATIVE));
    }
    return vectors;
  }
}
