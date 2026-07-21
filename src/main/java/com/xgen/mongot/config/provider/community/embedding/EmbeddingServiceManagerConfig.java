package com.xgen.mongot.config.provider.community.embedding;

import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig;
import com.xgen.mongot.embedding.providers.configs.EmbeddingServiceConfig.VoyageEmbeddingCredentials;
import com.xgen.mongot.util.bson.YamlCodec;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration for embedding service manager in community edition. */
public record EmbeddingServiceManagerConfig(List<EmbeddingServiceConfig> configs)
    implements DocumentEncodable {

  private static final Logger LOG = LoggerFactory.getLogger(EmbeddingServiceManagerConfig.class);
  private static final String CONFIG_RESOURCE = "config/community/embedding-service-configs.yml";

  // path to the on-disk catalog; the launcher points this at the file shipped next to the JAR,
  // which operators can edit and restart to pick up
  static final String MODEL_CONFIG_FILE_PROPERTY = "mongot.embeddingModelConfigFile";

  /** Simple holder for Voyage API credentials. */
  public record VoyageCredentials(
      VoyageEmbeddingCredentials queryCredentials,
      VoyageEmbeddingCredentials indexingCredentials) {}

  private static class Fields {
    public static final Field.Required<List<BsonDocument>> CONFIGS =
        Field.builder("configs").documentField().asList().required();
  }

  /**
   * Loads embedding configuration from the bundled classpath resource (no external override).
   *
   * @param credentials the optional Voyage API credentials (query and indexing keys)
   * @return optional embedding service manager configuration, empty only when the catalog is
   *     missing or fails to parse
   * @see #loadEmbeddingServiceConfig(Optional, Optional)
   */
  public static Optional<EmbeddingServiceManagerConfig> loadEmbeddingServiceConfig(
      Optional<VoyageCredentials> credentials) {
    return loadEmbeddingServiceConfig(credentials, Optional.empty());
  }

  /**
   * Load the embedding config, resolving the catalog in order:
   *
   * <ol>
   *   <li>{@code modelConfigFileOverride} ({@code embedding.modelConfigFile}), if present;
   *   <li>the {@value #MODEL_CONFIG_FILE_PROPERTY} system property (file shipped next to the JAR);
   *   <li>the catalog bundled in the JAR.
   * </ol>
   *
   * <p>A configured-but-missing/unreadable path falls back to the bundled resource rather than
   * disabling auto-embedding. Without Voyage credentials, {@code VOYAGE} entries are dropped while
   * keyless providers ({@code OPENAI_COMPATIBLE}) still load.
   *
   * @param credentials optional Voyage API credentials (query and indexing keys)
   * @param modelConfigFileOverride explicit on-disk catalog path from the community config
   * @return empty only when the catalog is missing or fails to parse
   */
  public static Optional<EmbeddingServiceManagerConfig> loadEmbeddingServiceConfig(
      Optional<VoyageCredentials> credentials, Optional<Path> modelConfigFileOverride) {

    Optional<Path> externalPath = resolveExternalCatalogPath(modelConfigFileOverride);
    if (externalPath.isPresent()) {
      return loadFromFile(externalPath.get(), credentials);
    }
    return loadFromResource(credentials);
  }

  /**
   * On-disk catalog path from the override or system property; skips a path that isn't a readable
   * file.
   */
  private static Optional<Path> resolveExternalCatalogPath(
      Optional<Path> modelConfigFileOverride) {
    Optional<Path> configured =
        modelConfigFileOverride.or(EmbeddingServiceManagerConfig::catalogPathFromSystemProperty);

    if (configured.isEmpty()) {
      return Optional.empty();
    }

    Path path = configured.get();
    if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
      LOG.warn(
          "Embedding model catalog '{}' is missing or not readable; falling back to the bundled "
              + "catalog.",
          path);
      return Optional.empty();
    }
    return configured;
  }

  private static Optional<Path> catalogPathFromSystemProperty() {
    String property = System.getProperty(MODEL_CONFIG_FILE_PROPERTY);
    if (property == null || property.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(Path.of(property.trim()));
  }

  private static Optional<EmbeddingServiceManagerConfig> loadFromFile(
      Path path, Optional<VoyageCredentials> credentials) {
    try {
      LOG.atInfo()
          .addKeyValue("modelConfigFile", path.toString())
          .log("Reading embedding configuration from on-disk catalog");
      String yaml = Files.readString(path, StandardCharsets.UTF_8);
      return Optional.of(fromBson(YamlCodec.fromYaml(yaml), credentials));
    } catch (Exception e) {
      // catch broadly: the file is operator-editable, and SnakeYAML throws unchecked
      // YAMLException on bad YAML on top of the declared IOException/BsonParseException. a typo
      // mustn't crash startup — fall back to the bundled catalog.
      LOG.error(
          "Failed to load embedding configuration from on-disk catalog {}; falling back to the "
              + "bundled catalog.",
          path,
          e);
      return loadFromResource(credentials);
    }
  }

  private static Optional<EmbeddingServiceManagerConfig> loadFromResource(
      Optional<VoyageCredentials> credentials) {
    try (InputStream resourceStream =
        EmbeddingServiceManagerConfig.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {

      // Shouldn't ever happen since resources are built with this resource
      if (resourceStream == null) {
        LOG.info(
            "No embedding configuration found at {}. Auto-embedding functionality will be disabled",
            CONFIG_RESOURCE);
        return Optional.empty();
      }

      LOG.atInfo()
          .addKeyValue("resourcePath", CONFIG_RESOURCE)
          .log("Reading embedding configuration from internal resource");

      // YAML → BSON
      BsonDocument bson =
          YamlCodec.fromYaml(new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8));
      // BSON → EmbeddingServiceManagerConfig with injected credentials
      return Optional.of(fromBson(bson, credentials));
    } catch (IOException e) {
      LOG.error("Failed to read embedding configuration resource", e);
      return Optional.empty();
    } catch (BsonParseException e) {
      LOG.error("Failed to parse embedding configuration", e);
      return Optional.empty();
    }
  }

  /**
   * Helper method to parse BSON document (follows CommunityConfig pattern).
   *
   * @param document the BSON document
   * @param credentials the optional Voyage credentials to inject
   * @return the embedding service manager configuration
   * @throws BsonParseException if parsing fails
   */
  private static EmbeddingServiceManagerConfig fromBson(
      BsonDocument document, Optional<VoyageCredentials> credentials) throws BsonParseException {
    try (var parser = BsonDocumentParser.fromRoot(document).build()) {
      return fromBson(parser, credentials);
    }
  }

  /**
   * Parses embedding configuration from BSON and injects provided credentials.
   *
   * <p>No validation is performed since the internal YAML file is MongoDB-controlled and trusted.
   * {@code VOYAGE} entries are skipped when no Voyage credentials are available, since the Voyage
   * client cannot authenticate without an API key.
   *
   * @param parser the document parser
   * @param credentials the optional Voyage credentials to inject
   * @return the embedding service manager configuration
   * @throws BsonParseException if parsing fails
   */
  private static EmbeddingServiceManagerConfig fromBson(
      DocumentParser parser, Optional<VoyageCredentials> credentials) throws BsonParseException {

    List<BsonDocument> configDocs = parser.getField(Fields.CONFIGS).unwrap();

    List<EmbeddingServiceConfig> serviceConfigs = new ArrayList<>();
    for (BsonDocument configDoc : configDocs) {
      String provider = providerOf(configDoc);
      if ("VOYAGE".equals(provider) && credentials.isEmpty()) {
        LOG.warn(
            "Skipping Voyage embedding model '{}': no Voyage API credentials configured "
                + "(set queryKeyFile and indexingKeyFile to enable Voyage models).",
            modelNameOf(configDoc));
        continue;
      }

      // Inject credentials into each config
      injectCredentials(configDoc, credentials);

      // Parse the modified document
      try (DocumentParser configParser = BsonDocumentParser.fromRoot(configDoc).build()) {
        serviceConfigs.add(EmbeddingServiceConfig.fromBson(configParser));
      }
    }

    return new EmbeddingServiceManagerConfig(serviceConfigs);
  }

  private static String providerOf(BsonDocument configDoc) {
    return configDoc.containsKey("embeddingProvider")
            && configDoc.get("embeddingProvider").isString()
        ? configDoc.getString("embeddingProvider").getValue()
        : "VOYAGE";
  }

  private static String modelNameOf(BsonDocument configDoc) {
    return configDoc.containsKey("modelName") && configDoc.get("modelName").isString()
        ? configDoc.getString("modelName").getValue()
        : "<unknown>";
  }

  /**
   * Inject the {@code _provider} discriminator and community fields into the config doc. VOYAGE
   * entries get the loaded keys (so the bundled YAML stays credential-free); other providers keep
   * their own YAML credentials. {@code isDedicatedCluster: true} is set for all.
   *
   * <p>Also tags nested {@code modelConfig} / {@code credentials} / {@code tenantCredentials}
   * under per-workload overrides ({@code query}, {@code collectionScan}, {@code changeStream}) so
   * operators can write those blocks in an on-disk catalog without knowing about the internal
   * discriminator.
   */
  private static void injectCredentials(
      BsonDocument configDoc, Optional<VoyageCredentials> credentials) {
    if (!configDoc.containsKey("config")) {
      return;
    }
    String provider = providerOf(configDoc);
    BsonDocument configField = configDoc.getDocument("config");

    // tag modelConfig with _provider so the polymorphic parser picks the right ModelConfig subtype
    tagProvider(configField, "modelConfig", provider);

    // callers skip VOYAGE entries when credentials are absent, so orElseThrow is safe here
    if ("VOYAGE".equals(provider)) {
      injectVoyageCredentials(configField, credentials.orElseThrow());
      // YAML-supplied Voyage credentials also need the discriminator
      tagProvider(configField, "credentials", provider);
    } else {
      // other providers carry their own credentials in the YAML; just ensure the doc exists +
      // tagged
      BsonDocument credentialsDoc =
          configField.containsKey("credentials")
              ? configField.getDocument("credentials")
              : new BsonDocument();
      if (!credentialsDoc.containsKey("_provider")) {
        credentialsDoc.put("_provider", new BsonString(provider));
      }
      configField.put("credentials", credentialsDoc);
    }

    // per-tier overrides use the same polymorphic parsers as the base fields
    for (String workloadKey : List.of("query", "collectionScan", "changeStream")) {
      tagWorkloadProviderFields(configField, workloadKey, provider);
    }

    if (!configField.containsKey("isDedicatedCluster")) {
      configField.put("isDedicatedCluster", BsonBoolean.TRUE);
    }
  }

  /**
   * Tag polymorphic nested docs inside a workload override block ({@code query} /
   * {@code collectionScan} / {@code changeStream}).
   */
  private static void tagWorkloadProviderFields(
      BsonDocument configField, String workloadKey, String provider) {
    if (!configField.containsKey(workloadKey) || !configField.get(workloadKey).isDocument()) {
      return;
    }
    BsonDocument workload = configField.getDocument(workloadKey);
    tagProvider(workload, "modelConfig", provider);
    tagProvider(workload, "credentials", provider);
    tagProvider(workload, "tenantCredentials", provider);
  }

  /** Add {@code _provider} to a nested document field when present and untagged. */
  private static void tagProvider(BsonDocument parent, String fieldName, String provider) {
    if (!parent.containsKey(fieldName) || !parent.get(fieldName).isDocument()) {
      return;
    }
    BsonDocument doc = parent.getDocument(fieldName);
    if (!doc.containsKey("_provider")) {
      doc.put("_provider", new BsonString(provider));
    }
  }

  /** Injects the loaded Voyage base and query credentials into a {@code VOYAGE} config entry. */
  private static void injectVoyageCredentials(
      BsonDocument configField, VoyageCredentials credentials) {
    if (!configField.containsKey("credentials")) {
      BsonDocument credentialsDoc = credentials.indexingCredentials.toBson();
      credentialsDoc.put("_provider", new BsonString("VOYAGE"));
      configField.put("credentials", credentialsDoc);
    }

    // query tier overrides with the query key
    if (!configField.containsKey("query")) {
      BsonDocument queryCredentialsDoc = credentials.queryCredentials.toBson();
      queryCredentialsDoc.put("_provider", new BsonString("VOYAGE"));

      BsonDocument queryParams = new BsonDocument();
      queryParams.put("credentials", queryCredentialsDoc);

      configField.put("query", queryParams);
    }
  }

  @Override
  public BsonDocument toBson() {
    List<BsonDocument> configDocs = new ArrayList<>();
    for (EmbeddingServiceConfig config : this.configs) {
      configDocs.add(config.toBson());
    }
    Field.Required<List<BsonDocument>> configsField =
        Field.builder("configs").documentField().asList().required();
    return BsonDocumentBuilder.builder().field(configsField, configDocs).build();
  }
}
