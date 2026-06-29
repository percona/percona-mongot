package com.xgen.mongot.embedding.providers.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.VoyageModelConfig;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.Test;

public class VoyageModelConfigTest {

  @Test
  public void fromBson_withSimilarityMap_parsesPerQuantizationDefaults() throws Exception {
    BsonDocument doc =
        new BsonDocument()
            .append("outputDimensions", new BsonInt32(1024))
            .append("quantization", new BsonString("scalar"))
            .append(
                "similarity",
                new BsonDocument()
                    .append("scalar", new BsonString("cosine"))
                    .append("float", new BsonString("dotProduct")));

    VoyageModelConfig config;
    try (var parser = BsonDocumentParser.fromRoot(doc).build()) {
      config = VoyageModelConfig.fromBson(parser);
    }

    assertTrue(config.similarityByQuantization.isPresent());
    Map<String, String> byQuantization = config.similarityByQuantization.get();
    assertEquals("cosine", byQuantization.get("scalar"));
    assertEquals("dotProduct", byQuantization.get("float"));
  }

  @Test
  public void fromBson_withoutSimilarityMap_isEmpty() throws Exception {
    BsonDocument doc =
        new BsonDocument()
            .append("outputDimensions", new BsonInt32(1024))
            .append("quantization", new BsonString("scalar"));

    VoyageModelConfig config;
    try (var parser = BsonDocumentParser.fromRoot(doc).build()) {
      config = VoyageModelConfig.fromBson(parser);
    }

    assertFalse(config.similarityByQuantization.isPresent());
  }
}
