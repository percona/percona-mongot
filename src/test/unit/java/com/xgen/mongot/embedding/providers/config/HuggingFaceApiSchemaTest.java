package com.xgen.mongot.embedding.providers.config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.embedding.providers.configs.HuggingFaceApiSchema;
import com.xgen.mongot.embedding.providers.configs.HuggingFaceApiSchema.MalformedResponseException;
import com.xgen.mongot.util.bson.Vector;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.junit.Test;

public class HuggingFaceApiSchemaTest {

  @Test
  public void embedRequest_serializesInputsAndOptionalFlags() {
    BsonDocument withFlags =
        new HuggingFaceApiSchema.EmbedRequest(
                List.of("a", "b"), Optional.of(false), Optional.of(true))
            .toBson();
    assertEquals(
        BsonDocument.parse("{inputs: ['a', 'b'], normalize: false, truncate: true}"), withFlags);

    BsonDocument withoutFlags =
        new HuggingFaceApiSchema.EmbedRequest(List.of("a"), Optional.empty(), Optional.empty())
            .toBson();
    assertEquals(BsonDocument.parse("{inputs: ['a']}"), withoutFlags);
  }

  @Test
  public void decodeEmbeddings_parsesNestedFloatArrays() throws Exception {
    List<Vector> vectors = HuggingFaceApiSchema.decodeEmbeddings("[[0.5, -1, 2.25], [0, 1e-3, 3]]");
    assertEquals(2, vectors.size());
    assertArrayEquals(
        new float[] {0.5f, -1f, 2.25f}, vectors.get(0).asFloatVector().getFloatVector(), 0f);
    assertArrayEquals(
        new float[] {0f, 0.001f, 3f}, vectors.get(1).asFloatVector().getFloatVector(), 1e-7f);
  }

  @Test
  public void decodeEmbeddings_emptyOuterArray_returnsEmpty() throws Exception {
    assertTrue(HuggingFaceApiSchema.decodeEmbeddings("[]").isEmpty());
  }

  @Test
  public void decodeEmbeddings_tokenLevelOutput_flaggedAsPermanent() {
    MalformedResponseException e =
        assertThrows(
            MalformedResponseException.class,
            () -> HuggingFaceApiSchema.decodeEmbeddings("[[[0.1, 0.2], [0.3, 0.4]]]"));
    assertTrue(e.isTokenLevelOutput());
  }

  @Test
  public void decodeEmbeddings_malformedBodies_notPermanent() {
    for (String body :
        new String[] {
          "<html>bad gateway</html>",
          "{\"error\": \"Model is loading\"}",
          "[1, 2, 3]",
          "[[]]",
          "[[1, \"x\"]]",
        }) {
      MalformedResponseException e =
          assertThrows(
              "expected failure for " + body,
              MalformedResponseException.class,
              () -> HuggingFaceApiSchema.decodeEmbeddings(body));
      assertFalse(e.isTokenLevelOutput());
    }
  }
}
