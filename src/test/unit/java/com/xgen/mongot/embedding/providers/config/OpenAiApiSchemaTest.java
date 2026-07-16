package com.xgen.mongot.embedding.providers.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.embedding.providers.configs.OpenAiApiSchema;
import com.xgen.mongot.embedding.providers.configs.OpenAiApiSchema.EmbedRequest;
import com.xgen.mongot.embedding.providers.configs.OpenAiApiSchema.EmbedResponse;
import com.xgen.mongot.util.bson.FloatVector;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.junit.Test;

public class OpenAiApiSchemaTest {

  private static EmbedResponse parse(String json) throws BsonParseException {
    return EmbedResponse.fromBson(
        BsonDocumentParser.fromRoot(BsonDocument.parse(json)).allowUnknownFields(true).build());
  }

  private static String base64Floats(float... values) {
    ByteBuffer buffer =
        ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (float value : values) {
      buffer.putFloat(value);
    }
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  @Test
  public void requestSerialization_omitsDimensionsWhenAbsent() {
    BsonDocument doc =
        new EmbedRequest(
                "bge-m3",
                List.of("one", "two"),
                OpenAiApiSchema.DEFAULT_ENCODING_FORMAT,
                Optional.empty())
            .toBson();

    assertEquals("bge-m3", doc.getString("model").getValue());
    assertEquals("base64", doc.getString("encoding_format").getValue());
    assertEquals(2, doc.getArray("input").size());
    assertFalse("dimensions must not be sent when not configured", doc.containsKey("dimensions"));
  }

  @Test
  public void requestSerialization_includesDimensionsWhenConfigured() {
    BsonDocument doc =
        new EmbedRequest(
                "text-embedding-3-small",
                List.of("one"),
                OpenAiApiSchema.DEFAULT_ENCODING_FORMAT,
                Optional.of(256))
            .toBson();

    assertTrue(doc.containsKey("dimensions"));
    assertEquals(256, doc.getInt32("dimensions").getValue());
  }

  @Test
  public void responseDeserialization_decodesBase64FloatEmbedding() throws Exception {
    String json =
        String.format(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":\"%s\","
                + "\"index\":0}],\"usage\":{\"prompt_tokens\":3,\"total_tokens\":3}}",
            base64Floats(1f, 2f, 3f));

    EmbedResponse response = parse(json);

    assertEquals(1, response.data.size());
    assertEquals(
        Vector.fromFloats(new float[] {1f, 2f, 3f}, FloatVector.OriginalType.NATIVE),
        response.data.get(0).embedding);
    assertEquals(Optional.of(3), response.usage.flatMap(u -> u.totalTokens));
  }

  @Test
  public void responseDeserialization_decodesPlainFloatArrayEmbedding() throws Exception {
    // Servers that ignore encoding_format return a JSON number array instead of base64.
    String json =
        "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\","
            + "\"embedding\":[0.5,1.5,2.5],\"index\":0}],\"usage\":{\"total_tokens\":2}}";

    EmbedResponse response = parse(json);

    assertEquals(
        Vector.fromFloats(new float[] {0.5f, 1.5f, 2.5f}, FloatVector.OriginalType.NATIVE),
        response.data.get(0).embedding);
  }

  @Test
  public void responseDeserialization_toleratesMissingUsageAndObject() throws Exception {
    // Some local engines (certain Ollama builds) omit usage and object.
    String json =
        String.format(
            "{\"data\":[{\"embedding\":\"%s\"}]}", base64Floats(1f, 1f));

    EmbedResponse response = parse(json);

    assertTrue(response.usage.isEmpty());
    assertEquals(2, response.data.get(0).embedding.numDimensions());
  }

  @Test
  public void responseDeserialization_invalidBase64Throws() {
    String json =
        "{\"object\":\"list\",\"data\":[{\"embedding\":\"not-base-64!!!\",\"index\":0}]}";
    assertThrows(BsonParseException.class, () -> parse(json));
  }

  @Test
  public void responseDeserialization_oddByteLengthThrows() {
    // 3 bytes is not a multiple of Float.BYTES (4).
    String base64 = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    String json =
        String.format("{\"data\":[{\"embedding\":\"%s\",\"index\":0}]}", base64);
    assertThrows(BsonParseException.class, () -> parse(json));
  }
}
