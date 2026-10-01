package com.xgen.mongot.embedding.providers.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.embedding.providers.configs.EmbeddingConfigFactory;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.EmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.EmbeddingProvider;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.HuggingFaceEmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.HuggingFaceModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.ModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.OpenAiEmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.OpenAiModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.ServiceTier;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.VoyageEmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.VoyageModelConfig;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Test;

public class EmbeddingConfigFactoryTest {

  private static BsonDocument tagged(String provider, String json) {
    BsonDocument doc = BsonDocument.parse(json);
    doc.put("_provider", new BsonString(provider));
    return doc;
  }

  private static EmbeddingCredentials credentials(BsonDocument doc) throws BsonParseException {
    return EmbeddingConfigFactory.getCredentials(BsonDocumentParser.fromRoot(doc).build());
  }

  private static ModelConfig modelConfig(BsonDocument doc) throws BsonParseException {
    return EmbeddingConfigFactory.getModelConfig(BsonDocumentParser.fromRoot(doc).build());
  }

  @Test
  public void getCredentials_voyage() throws Exception {
    EmbeddingCredentials creds =
        credentials(tagged("VOYAGE", "{apiToken: 'v-token', expirationDate: ''}"));
    assertEquals(new VoyageEmbeddingCredentials("v-token"), creds);
  }

  @Test
  public void getCredentials_openAiCompatible() throws Exception {
    EmbeddingCredentials creds = credentials(tagged("OPENAI_COMPATIBLE", "{apiKey: 'sk-1'}"));
    assertEquals(new OpenAiEmbeddingCredentials(Optional.of("sk-1")), creds);
  }

  @Test
  public void getCredentials_huggingFace() throws Exception {
    EmbeddingCredentials creds = credentials(tagged("HUGGINGFACE_INFERENCE", "{apiToken: 'hf_x'}"));
    assertEquals(new HuggingFaceEmbeddingCredentials(Optional.of("hf_x")), creds);
    assertEquals(EmbeddingProvider.HUGGINGFACE_INFERENCE, creds.getCredentialProvider());
  }

  @Test
  public void getCredentials_huggingFaceWithoutToken_parsesAsEmpty() throws Exception {
    HuggingFaceEmbeddingCredentials creds =
        (HuggingFaceEmbeddingCredentials) credentials(tagged("HUGGINGFACE_INFERENCE", "{}"));
    assertEquals(Optional.empty(), creds.apiToken);
    assertFalse(creds.hasToken());
  }

  @Test
  public void getCredentials_unsupportedProviders_throw() {
    for (String provider : new String[] {"AWS_BEDROCK", "COHERE"}) {
      assertThrows(IllegalStateException.class, () -> credentials(tagged(provider, "{}")));
    }
  }

  @Test
  public void getModelConfig_voyage() throws Exception {
    ModelConfig config = modelConfig(tagged("VOYAGE", "{outputDimensions: 1024}"));
    assertTrue(config instanceof VoyageModelConfig);
    assertEquals(1024, config.getOutputDimensions());
  }

  @Test
  public void getModelConfig_openAiCompatible() throws Exception {
    ModelConfig config = modelConfig(tagged("OPENAI_COMPATIBLE", "{outputDimensions: 768}"));
    assertTrue(config instanceof OpenAiModelConfig);
    assertEquals(768, config.getOutputDimensions());
  }

  @Test
  public void getModelConfig_huggingFace_allFields() throws Exception {
    ModelConfig config =
        modelConfig(
            tagged(
                "HUGGINGFACE_INFERENCE",
                "{modelId: 'BAAI/bge-small-en-v1.5', outputDimensions: 384, batchSize: 16,"
                    + " batchTokenLimit: 5000, quantization: 'float', normalize: false,"
                    + " truncate: false, queryPrefix: 'q: ', documentPrefix: 'd: '}"));
    assertEquals(
        new HuggingFaceModelConfig(
            Optional.of("BAAI/bge-small-en-v1.5"),
            Optional.of(384),
            Optional.of(16),
            Optional.of(5000),
            Optional.of(VectorAutoEmbedQuantization.FLOAT),
            Optional.of(false),
            Optional.of(false),
            Optional.of("q: "),
            Optional.of("d: ")),
        config);
    assertEquals(EmbeddingProvider.HUGGINGFACE_INFERENCE, config.getModelProvider());
  }

  @Test
  public void getModelConfig_huggingFace_defaults() throws Exception {
    HuggingFaceModelConfig config =
        (HuggingFaceModelConfig) modelConfig(tagged("HUGGINGFACE_INFERENCE", "{}"));
    assertEquals(32, config.getBatchSize());
    assertEquals(120_000, config.getBatchTokenLimit());
    assertEquals(1024, config.getOutputDimensions());
    assertEquals(Optional.empty(), config.getConfiguredOutputDimensions());
    assertEquals(Optional.empty(), config.getConfiguredQuantization());
    assertEquals(Optional.empty(), config.getConfiguredSimilarityByQuantization());
    assertTrue(config.shouldTruncate());
    assertEquals("my-model", config.modelIdOrDefault("my-model"));
    assertEquals("", config.inputPrefixForTier(ServiceTier.QUERY));
    assertEquals("", config.inputPrefixForTier(ServiceTier.COLLECTION_SCAN));
  }

  @Test
  public void getModelConfig_huggingFace_invalidQuantization_throws() {
    assertThrows(
        BsonParseException.class,
        () -> modelConfig(tagged("HUGGINGFACE_INFERENCE", "{quantization: 'bogus'}")));
  }

  @Test
  public void getModelConfig_unsupportedProviders_throw() {
    for (String provider : new String[] {"AWS_BEDROCK", "COHERE"}) {
      assertThrows(IllegalStateException.class, () -> modelConfig(tagged(provider, "{}")));
    }
  }

  @Test
  public void huggingFaceModelConfig_bsonRoundTrip() throws Exception {
    HuggingFaceModelConfig original =
        new HuggingFaceModelConfig(
            Optional.of("BAAI/bge-small-en-v1.5"),
            Optional.of(384),
            Optional.of(32),
            Optional.of(120_000),
            Optional.of(VectorAutoEmbedQuantization.FLOAT),
            Optional.of(true),
            Optional.of(true),
            Optional.of("query: "),
            Optional.of("passage: "));
    BsonDocument encoded = original.toBson();
    encoded.put("_provider", new BsonString("HUGGINGFACE_INFERENCE"));
    assertEquals(original, modelConfig(encoded));
    assertEquals(original.hashCode(), modelConfig(encoded).hashCode());
  }

  @Test
  public void huggingFaceModelConfig_prefixesAndModelIdHelpers() {
    HuggingFaceModelConfig config =
        new HuggingFaceModelConfig(
            Optional.of("  "),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(false),
            Optional.of("query: "),
            Optional.of("passage: "));
    // blank modelId falls back to the catalog name
    assertEquals("catalog-name", config.modelIdOrDefault("catalog-name"));
    assertFalse(config.shouldTruncate());
    assertEquals("query: ", config.inputPrefixForTier(ServiceTier.QUERY));
    assertEquals("passage: ", config.inputPrefixForTier(ServiceTier.CHANGE_STREAM));
    assertEquals("passage: ", config.inputPrefixForTier(ServiceTier.COLLECTION_SCAN));
  }

  @Test
  public void huggingFaceCredentials_roundTripSanitizeAndUuid() throws Exception {
    HuggingFaceEmbeddingCredentials creds =
        new HuggingFaceEmbeddingCredentials(Optional.of("hf_secret"));
    BsonDocument encoded = creds.toBson();
    encoded.put("_provider", new BsonString("HUGGINGFACE_INFERENCE"));
    assertEquals(creds, credentials(encoded));
    assertEquals(creds.hashCode(), credentials(encoded).hashCode());
    assertTrue(creds.hasToken());
    assertFalse(new HuggingFaceEmbeddingCredentials(Optional.of(" ")).hasToken());

    HuggingFaceEmbeddingCredentials sanitized =
        (HuggingFaceEmbeddingCredentials) creds.copySanitized("xxx");
    assertEquals(Optional.of("xxx"), sanitized.apiToken);
    // keyless stays keyless when sanitized
    assertEquals(
        Optional.empty(),
        ((HuggingFaceEmbeddingCredentials)
                new HuggingFaceEmbeddingCredentials(Optional.empty()).copySanitized("xxx"))
            .apiToken);

    // stable per token, different across tokens, defined when keyless
    assertEquals(
        creds.getCredentialsUuID(),
        new HuggingFaceEmbeddingCredentials(Optional.of("hf_secret")).getCredentialsUuID());
    assertFalse(
        creds
            .getCredentialsUuID()
            .equals(
                new HuggingFaceEmbeddingCredentials(Optional.of("hf_other"))
                    .getCredentialsUuID()));
    assertFalse(
        new HuggingFaceEmbeddingCredentials(Optional.empty()).getCredentialsUuID().isEmpty());
  }

  @Test
  public void fromBson_huggingFaceServiceConfig_lowercasesModelNameButKeepsModelId()
      throws Exception {
    BsonDocument doc =
        BsonDocument.parse(
            "{embeddingProvider: 'HUGGINGFACE_INFERENCE', modelName: 'BGE-Small-EN-v1.5',"
                + " config: {"
                + "  modelConfig: {_provider: 'HUGGINGFACE_INFERENCE',"
                + "    modelId: 'BAAI/bge-small-en-v1.5', outputDimensions: 384},"
                + "  errorHandlingConfig: {maxRetries: 3, initialRetryWaitMs: 10,"
                + "    maxRetryWaitMs: 10, jitter: 0.1},"
                + "  credentials: {_provider: 'HUGGINGFACE_INFERENCE', apiToken: 'hf_x'}}}");
    EmbeddingServiceConfig config =
        EmbeddingConfigFactory.fromBson(BsonDocumentParser.fromRoot(doc).build());
    assertEquals(EmbeddingProvider.HUGGINGFACE_INFERENCE, config.embeddingProvider);
    assertEquals("bge-small-en-v1.5", config.modelName);
    HuggingFaceModelConfig modelConfig =
        (HuggingFaceModelConfig) config.embeddingConfig.modelConfigBase;
    assertEquals(Optional.of("BAAI/bge-small-en-v1.5"), modelConfig.modelId);
  }
}
