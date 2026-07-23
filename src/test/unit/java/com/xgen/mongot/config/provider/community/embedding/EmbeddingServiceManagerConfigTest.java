package com.xgen.mongot.config.provider.community.embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.EmbeddingProvider;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.OpenAiEmbeddingCredentials;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.OpenAiModelConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.VoyageEmbeddingCredentials;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class EmbeddingServiceManagerConfigTest {

  private static final ch.qos.logback.classic.Logger CONFIG_LOGGER =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EmbeddingServiceManagerConfig.class);

  private static EmbeddingServiceManagerConfig loadWithTestCredentials() {
    EmbeddingServiceManagerConfig.VoyageCredentials credentials =
        new EmbeddingServiceManagerConfig.VoyageCredentials(
            new VoyageEmbeddingCredentials("test-query-key"),
            new VoyageEmbeddingCredentials("test-indexing-key"));

    Optional<EmbeddingServiceManagerConfig> result =
        EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(Optional.of(credentials));

    assertTrue("Expected non-empty Optional", result.isPresent());
    return result.get();
  }

  @Test
  public void loadEmbeddingServiceConfig_withValidCredentials_returnsConfig() {
    EmbeddingServiceManagerConfig config = loadWithTestCredentials();

    // Verify that configs were loaded from the YAML file
    assertFalse("Expected at least one config", config.configs().isEmpty());

    // Verify expected models are present (from embedding-service-configs.yml)
    Map<String, EmbeddingProvider> expectedModels =
        Map.of(
            "voyage-4-large", EmbeddingProvider.VOYAGE,
            "voyage-4", EmbeddingProvider.VOYAGE,
            "voyage-4-lite", EmbeddingProvider.VOYAGE,
            "voyage-code-3", EmbeddingProvider.VOYAGE,
            "bge-m3", EmbeddingProvider.OPENAI_COMPATIBLE,
            "nomic-embed-text", EmbeddingProvider.OPENAI_COMPATIBLE);
    Map<String, EmbeddingProvider> actualModels =
        config.configs().stream()
            .collect(
                Collectors.toMap(
                    serviceConfig -> serviceConfig.modelName,
                    serviceConfig -> serviceConfig.embeddingProvider));

    assertEquals(
        "Expected all models from YAML to be loaded with their providers",
        expectedModels,
        actualModels);

    // Verify credentials were injected/parsed for every config
    for (EmbeddingServiceConfig serviceConfig : config.configs()) {
      assertNotNull(
          "Expected credentials to be present for model: " + serviceConfig.modelName,
          serviceConfig.embeddingConfig.credentialsBase);
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_openAiCompatModels_parseWithOpenAiTypes() {
    EmbeddingServiceManagerConfig config = loadWithTestCredentials();

    EmbeddingServiceConfig bgeM3 =
        config.configs().stream()
            .filter(c -> c.modelName.equals("bge-m3"))
            .findFirst()
            .orElseThrow();

    assertEquals(EmbeddingProvider.OPENAI_COMPATIBLE, bgeM3.embeddingProvider);
    assertTrue(
        "Expected OpenAiModelConfig for OPENAI_COMPATIBLE model",
        bgeM3.embeddingConfig.modelConfigBase instanceof OpenAiModelConfig);
    assertTrue(
        "Expected OpenAiEmbeddingCredentials for OPENAI_COMPATIBLE model",
        bgeM3.embeddingConfig.credentialsBase instanceof OpenAiEmbeddingCredentials);
    // Keyless local engine: no API key injected.
    assertTrue(
        "Expected no API key for keyless local engine",
        ((OpenAiEmbeddingCredentials) bgeM3.embeddingConfig.credentialsBase).apiKey.isEmpty());
    assertEquals(
        Optional.of("http://localhost:11434/v1/embeddings"),
        bgeM3.embeddingConfig.providerEndpoint);
  }

  @Test
  public void loadEmbeddingServiceConfig_noCredentials_dropsVoyageKeepsOpenAiCompat() {
    Optional<EmbeddingServiceManagerConfig> result =
        EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(Optional.empty());

    assertTrue(
        "Expected config to load even without Voyage credentials (keyless local deployments)",
        result.isPresent());

    Map<String, EmbeddingProvider> actualModels =
        result.get().configs().stream()
            .collect(
                Collectors.toMap(
                    serviceConfig -> serviceConfig.modelName,
                    serviceConfig -> serviceConfig.embeddingProvider));

    // Voyage models require an API key and are dropped; OPENAI_COMPATIBLE models (optional key)
    // remain.
    Map<String, EmbeddingProvider> expectedModels =
        Map.of(
            "bge-m3", EmbeddingProvider.OPENAI_COMPATIBLE,
            "nomic-embed-text", EmbeddingProvider.OPENAI_COMPATIBLE);
    assertEquals(
        "Expected only OPENAI_COMPATIBLE models to remain without Voyage credentials",
        expectedModels,
        actualModels);
  }

  @Test
  public void loadEmbeddingServiceConfig_onDiskOverride_loadsFromFile() throws Exception {
    // An on-disk catalog fully replaces the bundled one: only the model defined in the file should
    // be present. This backs the "edit file + restart" workflow for adding/overriding models.
    String catalog =
        """
        configs:
          - modelName: custom-on-disk-model
            embeddingProvider: OPENAI_COMPATIBLE
            config:
              providerEndpoint: http://localhost:9999/v1/embeddings
              modelConfig:
                batchSize: 8
                batchTokenLimit: 1000
              errorHandlingConfig:
                maxRetries: 3
                initialRetryWaitMs: 100
                maxRetryWaitMs: 1000
                jitter: 0.1
              credentials: {}
        """;
    Path catalogFile = Files.createTempFile("embedding-service-configs", ".yml");
    try {
      Files.writeString(catalogFile, catalog, StandardCharsets.UTF_8);

      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(
              Optional.empty(), Optional.of(catalogFile));

      assertTrue("Expected config to load from the on-disk catalog", result.isPresent());
      Map<String, EmbeddingProvider> actualModels =
          result.get().configs().stream()
              .collect(
                  Collectors.toMap(
                      serviceConfig -> serviceConfig.modelName,
                      serviceConfig -> serviceConfig.embeddingProvider));
      assertEquals(
          "Expected only the model defined in the on-disk catalog",
          Map.of("custom-on-disk-model", EmbeddingProvider.OPENAI_COMPATIBLE),
          actualModels);
    } finally {
      Files.deleteIfExists(catalogFile);
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_missingOverrideFile_failsClosed() {
    // An explicit embedding.modelConfigFile must not silently fall back to the bundled catalog
    // (which can point at localhost endpoints under the same model names). Fail closed so
    // auto-embedding stays inactive instead of routing to the wrong place.
    Path missingPath = Path.of("/nonexistent/embedding-service-configs.yml");
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    CONFIG_LOGGER.addAppender(listAppender);
    try {
      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(
              Optional.empty(), Optional.of(missingPath));

      assertTrue(
          "Expected empty result when explicit modelConfigFile is missing", result.isEmpty());

      ILoggingEvent error =
          listAppender.list.stream()
              .filter(event -> event.getLevel() == Level.ERROR)
              .filter(
                  event ->
                      event.getFormattedMessage().contains("Explicit embedding.modelConfigFile"))
              .filter(
                  event ->
                      event
                          .getFormattedMessage()
                          .contains("not falling back to the bundled catalog"))
              .filter(
                  event -> event.getFormattedMessage().contains("auto-embedding will be inactive"))
              .filter(event -> event.getFormattedMessage().contains(missingPath.toString()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "expected ERROR naming the missing modelConfigFile and fail-closed"
                              + " behavior"));
      assertNotNull(error);
    } finally {
      CONFIG_LOGGER.detachAppender(listAppender);
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_workloadOverrides_getProviderTagged() throws Exception {
    // Operators may override per-tier modelConfig/credentials under query / collectionScan /
    // changeStream without knowing about the internal _provider discriminator. Injection must
    // tag those nested docs the same way as the base fields, or parsing fails.
    String catalog =
        """
        configs:
          - modelName: workload-override-model
            embeddingProvider: OPENAI_COMPATIBLE
            config:
              providerEndpoint: http://localhost:9999/v1/embeddings
              modelConfig:
                batchSize: 8
                batchTokenLimit: 1000
              errorHandlingConfig:
                maxRetries: 3
                initialRetryWaitMs: 100
                maxRetryWaitMs: 1000
                jitter: 0.1
              credentials: {}
              query:
                credentials:
                  apiKey: query-only-key
              collectionScan:
                modelConfig:
                  batchSize: 16
                  batchTokenLimit: 2000
                  outputDimensions: 768
              changeStream:
                credentials:
                  apiKey: change-stream-key
                  authHeaderName: api-key
        """;
    Path catalogFile = Files.createTempFile("embedding-service-configs-workload", ".yml");
    try {
      Files.writeString(catalogFile, catalog, StandardCharsets.UTF_8);

      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(
              Optional.empty(), Optional.of(catalogFile));

      assertTrue("Expected workload-override catalog to parse", result.isPresent());
      assertEquals(1, result.get().configs().size());

      EmbeddingServiceConfig serviceConfig = result.get().configs().get(0);
      assertEquals("workload-override-model", serviceConfig.modelName);

      assertTrue(serviceConfig.embeddingConfig.queryParams.isPresent());
      assertTrue(serviceConfig.embeddingConfig.queryParams.get().credentialsOverride.isPresent());
      OpenAiEmbeddingCredentials queryCreds =
          (OpenAiEmbeddingCredentials)
              serviceConfig.embeddingConfig.queryParams.get().credentialsOverride.get();
      assertEquals(Optional.of("query-only-key"), queryCreds.apiKey);

      assertTrue(serviceConfig.embeddingConfig.collectionScanParams.isPresent());
      assertTrue(
          serviceConfig.embeddingConfig.collectionScanParams.get().modelConfigOverride.isPresent());
      assertTrue(
          serviceConfig.embeddingConfig.collectionScanParams.get().modelConfigOverride.get()
              instanceof OpenAiModelConfig);

      assertTrue(serviceConfig.embeddingConfig.changeStreamParams.isPresent());
      OpenAiEmbeddingCredentials changeStreamCreds =
          (OpenAiEmbeddingCredentials)
              serviceConfig.embeddingConfig.changeStreamParams.get().credentialsOverride.get();
      assertEquals(Optional.of("change-stream-key"), changeStreamCreds.apiKey);
      assertEquals(Optional.of("api-key"), changeStreamCreds.authHeaderName);
    } finally {
      Files.deleteIfExists(catalogFile);
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_malformedOverrideFile_failsClosed() throws Exception {
    // An explicit embedding.modelConfigFile with bad YAML must not crash startup, but also must
    // not silently fall back to the bundled catalog (wrong endpoints under familiar model names).
    Path catalogFile = Files.createTempFile("embedding-service-configs-malformed", ".yml");
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    CONFIG_LOGGER.addAppender(listAppender);
    try {
      Files.writeString(
          catalogFile, "configs: [unterminated flow sequence\n", StandardCharsets.UTF_8);

      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(
              Optional.empty(), Optional.of(catalogFile));

      assertTrue(
          "Expected empty result when explicit modelConfigFile is malformed", result.isEmpty());

      ILoggingEvent error =
          listAppender.list.stream()
              .filter(event -> event.getLevel() == Level.ERROR)
              .filter(
                  event ->
                      event
                          .getFormattedMessage()
                          .contains(
                              "Failed to load embedding configuration from explicit"
                                  + " modelConfigFile"))
              .filter(
                  event ->
                      event
                          .getFormattedMessage()
                          .contains("not falling back to the bundled catalog"))
              .filter(
                  event -> event.getFormattedMessage().contains("auto-embedding will be inactive"))
              .filter(event -> event.getFormattedMessage().contains(catalogFile.toString()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "expected ERROR naming the malformed modelConfigFile and fail-closed"
                              + " behavior"));
      assertNotNull(error);
    } finally {
      CONFIG_LOGGER.detachAppender(listAppender);
      Files.deleteIfExists(catalogFile);
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_missingSystemPropertyFile_fallsBackToBundledCatalog() {
    // The launcher always sets -Dmongot.embeddingModelConfigFile to the shipped file next to the
    // JAR. If that path is missing (unusual packaging), soft-fallback to the bundled resource is
    // fine — unlike an operator-chosen embedding.modelConfigFile.
    String previous = System.getProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY);
    try {
      System.setProperty(
          EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY,
          "/nonexistent/embedding-service-configs.yml");

      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(Optional.empty());

      assertTrue("Expected fallback to the bundled catalog", result.isPresent());
      assertFalse("Expected bundled models to load", result.get().configs().isEmpty());
    } finally {
      if (previous == null) {
        System.clearProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY);
      } else {
        System.setProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY, previous);
      }
    }
  }

  @Test
  public void loadEmbeddingServiceConfig_malformedSystemPropertyFile_fallsBackToBundledCatalog()
      throws Exception {
    String previous = System.getProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY);
    Path catalogFile = Files.createTempFile("embedding-service-configs-sysprop-malformed", ".yml");
    try {
      Files.writeString(
          catalogFile, "configs: [unterminated flow sequence\n", StandardCharsets.UTF_8);
      System.setProperty(
          EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY, catalogFile.toString());

      Optional<EmbeddingServiceManagerConfig> result =
          EmbeddingServiceManagerConfig.loadEmbeddingServiceConfig(Optional.empty());

      assertTrue("Expected fallback to the bundled catalog on malformed YAML", result.isPresent());
      assertTrue(
          "Expected bundled OPENAI_COMPATIBLE model after fallback",
          result.get().configs().stream().anyMatch(c -> c.modelName.equals("bge-m3")));
    } finally {
      Files.deleteIfExists(catalogFile);
      if (previous == null) {
        System.clearProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY);
      } else {
        System.setProperty(EmbeddingServiceManagerConfig.MODEL_CONFIG_FILE_PROPERTY, previous);
      }
    }
  }
}
