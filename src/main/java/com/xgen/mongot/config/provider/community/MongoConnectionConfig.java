package com.xgen.mongot.config.provider.community;

import com.google.common.net.HostAndPort;
import com.xgen.mongot.config.provider.community.parser.PathField;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import com.xgen.mongot.util.bson.parser.Value;
import com.xgen.mongot.util.mongodb.Databases;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class MongoConnectionConfig implements DocumentEncodable
    permits ReplicaSetConfig, RouterConfig {

  private static final Logger LOG = LoggerFactory.getLogger(MongoConnectionConfig.class);

  private final List<HostAndPort> hostandPorts;

  private final Optional<ScramConfig> scram;

  private final Optional<X509Config> x509;

  // TODO(CLOUDP-395903) - remove all @Deprecated members below before community GA.
  @Deprecated(forRemoval = true)
  private final Optional<String> username;

  @Deprecated(forRemoval = true)
  private final Optional<Path> passwordFile;

  @Deprecated(forRemoval = true)
  private final Optional<String> authSource;

  @Deprecated(forRemoval = true)
  private final Optional<Boolean> tls;

  @Deprecated(forRemoval = true)
  private final Optional<MongoReadPreferenceName> readPreference;

  public MongoConnectionConfig(
      List<HostAndPort> hostandPorts,
      Optional<String> username,
      Optional<Path> passwordFile,
      Optional<String> authSource,
      Optional<Boolean> tls,
      Optional<MongoReadPreferenceName> readPreference,
      Optional<X509Config> x509,
      Optional<ScramConfig> scram) {
    this.hostandPorts = hostandPorts;
    this.username = username;
    this.passwordFile = passwordFile;
    this.authSource = authSource;
    this.tls = tls;
    this.readPreference = readPreference;
    this.x509 = x509;
    this.scram = scram;
  }

  protected static class Fields {
    public static final Field.Required<List<String>> HOST_AND_PORT =
        Field.builder("hostAndPort")
            .singleValueOrListOf(Value.builder().stringValue().mustNotBeEmpty().required())
            .mustNotBeEmpty()
            .required();

    public static final Field.Optional<String> USERNAME =
        Field.builder("username").stringField().optional().noDefault();

    public static final Field.Optional<Path> PASSWORD_FILE =
        Field.builder("passwordFile")
            .classField(PathField.PARSER, PathField.ENCODER)
            .optional()
            .noDefault();

    public static final Field.Optional<String> AUTH_SOURCE =
        Field.builder("authSource").stringField().mustNotBeEmpty().optional().noDefault();

    public static final Field.Optional<Boolean> TLS =
        Field.builder("tls").booleanField().optional().noDefault();

    public static final Field.Optional<MongoReadPreferenceName> READ_PREFERENCE =
        Field.builder("readPreference")
            .enumField(MongoReadPreferenceName.class)
            .asCamelCase()
            .optional()
            .noDefault();

    public static final Field.Optional<X509Config> X509 =
        Field.builder("x509")
            .classField(X509Config::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<ScramConfig> SCRAM =
        Field.builder("scramAuth")
            .classField(ScramConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();
  }

  public List<HostAndPort> hostandPorts() {
    return this.hostandPorts;
  }

  public Optional<String> username() {
    return this.username;
  }

  public Optional<Path> passwordFile() {
    return this.passwordFile;
  }

  public String authSource() {
    return this.authSource.orElse(Databases.ADMIN);
  }

  public boolean tls() {
    if (this.x509.isPresent()) {
      return Boolean.TRUE;
    }
    return this.tls.orElse(Boolean.FALSE);
  }

  @Deprecated(forRemoval = true)
  public Optional<MongoReadPreferenceName> readPreference() {
    return this.readPreference;
  }

  public Optional<X509Config> x509() {
    return this.x509;
  }

  public Optional<ScramConfig> scram() {
    return this.scram;
  }

  @Override
  public BsonDocument toBson() {
    // TODO(CLOUDP-395903) - remove conditional serialization with removal of @Deprecated members
    return BsonDocumentBuilder.builder()
        .field(Fields.HOST_AND_PORT, this.hostandPorts.stream().map(HostAndPort::toString).toList())
        .field(Fields.USERNAME, this.username)
        .field(Fields.PASSWORD_FILE, this.passwordFile)
        .field(
            Fields.AUTH_SOURCE,
            (this.x509.isPresent() || this.scram.isPresent())
                ? Optional.empty()
                : Optional.of(this.authSource()))
        .field(
            Fields.TLS,
            (this.x509.isPresent() || this.scram.isPresent())
                ? Optional.empty()
                : Optional.of(this.tls()))
        .field(Fields.READ_PREFERENCE, this.readPreference)
        .field(Fields.X509, this.x509)
        .field(Fields.SCRAM, this.scram)
        .build();
  }

  /**
   * Validates the configured authentication mechanism. x509 and scram are mutually exclusive; once
   * that check passes the call is dispatched to a path-specific validator.
   */
  public void validate(DocumentParser parser, Optional<Path> caFile) throws BsonParseException {
    this.validateOnlyOneAuthType(parser);

    if (this.x509.isPresent()) {
      validateX509(parser, caFile);
    } else if (this.scram.isPresent()) {
      validateScram(parser, caFile);
    } else {
      validateLegacy(parser);
    }
  }

  /** Validates the legacy top-level authentication path. */
  @Deprecated(forRemoval = true)
  private void validateLegacy(DocumentParser parser) throws BsonParseException {
    LOG.warn(
        """
            The dedicated SCRAM configuration options in the \
            'syncSource.<replicaSet/router>' configuration block will be deprecated\
             in a future release. Please migrate these configurations to the dedicated \
            'syncSource.<replicaSet/router>.scram' configuration block.""");
    long presentCount =
        Stream.of(this.username, this.passwordFile).filter(Optional::isPresent).count();

    if (presentCount != 2) {
      parser
          .getContext()
          .handleSemanticError("username/passwordFile is required for authentication");
    }
  }

  /** Validates the x509 authentication path (+ legacy x509 path). */
  private void validateX509(DocumentParser parser, Optional<Path> caFile)
      throws BsonParseException {
    if (this.tls.isPresent() && !this.tls.get()) {
      parser.getContext().handleSemanticError("tls must be set to true when using x509");
      return;
    }

    this.x509.get().validate(parser, caFile);
  }

  /** Validates the SCRAM authentication path. */
  private void validateScram(DocumentParser parser, Optional<Path> caFile)
      throws BsonParseException {
    this.scram.get().validate(parser, caFile);
  }

  private void validateOnlyOneAuthType(DocumentParser parser) throws BsonParseException {
    long presentCount =
        Stream.of(this.scram, this.x509, this.username).filter(Optional::isPresent).count();
    if (presentCount != 1) {
      parser
          .getContext()
          .handleSemanticError(
              "One authentication mechanism must be used (username/passwordFile, x509 or scram)");
    }

    if (this.scram.isPresent()
        && (this.passwordFile.isPresent() || this.tls.isPresent() || this.authSource.isPresent())) {
      parser
          .getContext()
          .handleSemanticError(
              "deprecated fields (username, passwordFile, authSource, tls) cannot"
                  + " be used when scram auth is configured");
    }

    // Legacy x509 Checks
    if (this.x509.isPresent() && this.passwordFile.isPresent()) {
      parser
          .getContext()
          .handleSemanticError("x509 and username/passwordFile cannot be used together");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MongoConnectionConfig that = (MongoConnectionConfig) o;
    return this.tls() == that.tls()
        && Objects.equals(this.hostandPorts, that.hostandPorts)
        && Objects.equals(this.username, that.username)
        && Objects.equals(this.passwordFile, that.passwordFile)
        && Objects.equals(this.authSource(), that.authSource())
        && Objects.equals(this.readPreference, that.readPreference)
        && Objects.equals(this.x509, that.x509)
        && Objects.equals(this.scram, that.scram);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.hostandPorts,
        this.username,
        this.passwordFile,
        this.authSource(),
        this.tls(),
        this.readPreference,
        this.x509,
        this.scram);
  }
}
