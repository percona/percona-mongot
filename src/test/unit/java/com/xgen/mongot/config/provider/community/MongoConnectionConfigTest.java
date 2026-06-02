package com.xgen.mongot.config.provider.community;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.google.common.net.HostAndPort;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.mongodb.Databases;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.bson.BsonDocument;
import org.junit.Test;

/** Unit tests for {@link MongoConnectionConfig#validate} validation logic. */
public class MongoConnectionConfigTest {

  private static final List<HostAndPort> HOSTS = List.of(HostAndPort.fromParts("localhost", 27017));

  private static ReplicaSetConfig config(
      Optional<String> username, Optional<Path> passwordFile, Optional<X509Config> x509) {
    return config(username, passwordFile, x509, false);
  }

  private static ReplicaSetConfig config(
      Optional<String> username,
      Optional<Path> passwordFile,
      Optional<X509Config> x509,
      boolean tls) {
    return new ReplicaSetConfig(
        HOSTS,
        username,
        passwordFile,
        Optional.of(Databases.ADMIN),
        Optional.of(tls),
        Optional.empty(),
        x509,
        Optional.empty());
  }

  private static X509Config x509(Path certKeyFile) {
    TlsConfig tlsConfig =
        new TlsConfig(true, Optional.of(certKeyFile), Optional.empty(), Optional.empty());
    return new X509Config(tlsConfig);
  }

  /** Valid: username + passwordFile. */
  @Test
  public void testValidate_UsernameAndPasswordFile_Passes() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(Optional.of("user"), Optional.of(Path.of("/etc/passwd")), Optional.empty());

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      cfg.validate(parser, Optional.empty());
    }
  }

  /** Valid: x509 + caFile + tls. */
  @Test
  public void testValidate_x509WithCaFileAndTls_Passes() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(
            Optional.empty(),
            Optional.empty(),
            Optional.of(x509(Path.of("/etc/tls/client.pem"))),
            true);

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      cfg.validate(parser, Optional.of(Path.of("/etc/ca.pem")));
    }
  }

  @Test
  public void testValidate_x509WithoutTls_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(
            Optional.empty(), Optional.empty(), Optional.of(x509(Path.of("/etc/tls/client.pem"))));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(
              BsonParseException.class,
              () -> cfg.validate(parser, Optional.of(Path.of("/etc/ca.pem"))));
      assertEquals("tls must be set to true when using x509", e.getMessage());
    }
  }

  @Test
  public void testValidate_x509WithUsername_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(
            Optional.of("user"),
            Optional.empty(),
            Optional.of(x509(Path.of("/etc/tls/client.pem"))));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(
              BsonParseException.class,
              () -> cfg.validate(parser, Optional.of(Path.of("/etc/ca.pem"))));
      assertEquals(
          "One authentication mechanism must be used (username/passwordFile, x509 or scram)",
          e.getMessage());
    }
  }

  @Test
  public void testValidate_x509WithPasswordFile_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(
            Optional.empty(),
            Optional.of(Path.of("/etc/passwd")),
            Optional.of(x509(Path.of("/etc/tls/client.pem"))));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(
              BsonParseException.class,
              () -> cfg.validate(parser, Optional.of(Path.of("/etc/ca.pem"))));
      assertEquals("x509 and username/passwordFile cannot be used together", e.getMessage());
    }
  }

  @Test
  public void testValidate_x509WithMultipleCaFiles_ThrowsError() throws BsonParseException {
    TlsConfig tlsConfig =
        new TlsConfig(
            true,
            Optional.of(Path.of("/etc/tls/client.pem")),
            Optional.empty(),
            Optional.of(Path.of("/etc/tls/client.pem")));
    X509Config x509Config = new X509Config(tlsConfig);
    ReplicaSetConfig cfg =
        config(Optional.empty(), Optional.empty(), Optional.of(x509Config), true);

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(
              BsonParseException.class,
              () -> cfg.validate(parser, Optional.of(Path.of("/etc/tls/client.pem"))));
      assertEquals(
          "caFile must be set either within x509 config or parent sync source.", e.getMessage());
    }
  }

  @Test
  public void testValidate_UsernameWithoutPasswordFile_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg = config(Optional.of("user"), Optional.empty(), Optional.empty());

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(BsonParseException.class, () -> cfg.validate(parser, Optional.empty()));
      assertEquals("username/passwordFile is required for authentication", e.getMessage());
    }
  }

  @Test
  public void testValidate_PasswordFileWithoutUsername_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg =
        config(Optional.empty(), Optional.of(Path.of("/etc/passwd")), Optional.empty());

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(BsonParseException.class, () -> cfg.validate(parser, Optional.empty()));
      assertEquals(
          "One authentication mechanism must be used (username/passwordFile, x509 or scram)",
          e.getMessage());
    }
  }

  @Test
  public void testValidate_ScramConfig_delegatesToScramValidate() throws BsonParseException {
    TlsConfig tls = new TlsConfig(false, Optional.empty(), Optional.empty(), Optional.empty());
    ScramConfig scram =
        new ScramConfig(
            Databases.ADMIN, "__system", Path.of("/etc/mongot/keyfile"), tls);
    ReplicaSetConfig cfg =
        new ReplicaSetConfig(
            HOSTS,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(scram));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      cfg.validate(parser, Optional.empty());
    }
  }

  @Test
  public void testValidate_X509Config_delegatesToX509Validate() throws BsonParseException {
    X509Config x509 = x509(Path.of("/etc/tls/client.pem"));
    ReplicaSetConfig cfg = config(Optional.empty(), Optional.empty(), Optional.of(x509), true);
    Optional<Path> caFile = Optional.of(Path.of("/etc/ca.pem"));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      cfg.validate(parser, caFile);
    }
  }

  @Test
  public void testValidate_X509ConfigAndScram_ThrowsError() throws BsonParseException {
    X509Config x509 = x509(Path.of("/etc/tls/client.pem"));
    TlsConfig tls = new TlsConfig(false, Optional.empty(), Optional.empty(), Optional.empty());
    ScramConfig scram =
        new ScramConfig(
            Databases.ADMIN, "__system", Path.of("/etc/mongot/keyfile"), tls);
    ReplicaSetConfig cfg =
        new ReplicaSetConfig(
            HOSTS,
            Optional.empty(),
            Optional.empty(),
            Optional.of(Databases.ADMIN),
            Optional.of(true),
            Optional.empty(),
            Optional.of(x509),
            Optional.of(scram));
    Optional<Path> caFile = Optional.of(Path.of("/etc/ca.pem"));

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(BsonParseException.class, () -> cfg.validate(parser, caFile));
      assertEquals(
          "One authentication mechanism must be used (username/passwordFile, x509 or scram)",
          e.getMessage());
    }
  }

  @Test
  public void testValidate_NoAuth_ThrowsError() throws BsonParseException {
    ReplicaSetConfig cfg = config(Optional.empty(), Optional.empty(), Optional.empty());

    try (var parser = BsonDocumentParser.fromRoot(new org.bson.BsonDocument()).build()) {
      BsonParseException e =
          assertThrows(BsonParseException.class, () -> cfg.validate(parser, Optional.empty()));
      assertEquals(
          "One authentication mechanism must be used (username/passwordFile, x509 or scram)",
          e.getMessage());
    }
  }

  @Test
  public void testToBson_ScramConfigured_OmitsDeprecatedFieldsAndRoundTrips()
      throws BsonParseException {
    TlsConfig tls = new TlsConfig(false, Optional.empty(), Optional.empty(), Optional.empty());
    ScramConfig scram =
        new ScramConfig(Databases.ADMIN, "__system", Path.of("/etc/mongot/keyfile"), tls);
    ReplicaSetConfig cfg =
        new ReplicaSetConfig(
            HOSTS,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(scram));

    BsonDocument encoded = cfg.toBson();
    assertFalse(
        "top-level authSource must not be emitted when scram is configured",
        encoded.containsKey("authSource"));
    assertFalse(
        "top-level tls must not be emitted when scram is configured", encoded.containsKey("tls"));

    try (var parser = BsonDocumentParser.fromRoot(encoded).build()) {
      ReplicaSetConfig roundTripped = ReplicaSetConfig.fromBson(parser);
      assertEquals(cfg, roundTripped);
      roundTripped.validate(parser, Optional.empty());
    }
  }

  @Test
  public void testToBson_X509Configured_OmitsDeprecatedFieldsAndRoundTrips()
      throws BsonParseException {
    ReplicaSetConfig cfg =
        new ReplicaSetConfig(
            HOSTS,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(x509(Path.of("/etc/tls/client.pem"))),
            Optional.empty());

    BsonDocument encoded = cfg.toBson();
    assertFalse(
        "top-level authSource must not be emitted when x509 is configured",
        encoded.containsKey("authSource"));
    assertFalse(
        "top-level tls must not be emitted when x509 is configured", encoded.containsKey("tls"));

    try (var parser = BsonDocumentParser.fromRoot(encoded).build()) {
      ReplicaSetConfig roundTripped = ReplicaSetConfig.fromBson(parser);
      assertEquals(cfg, roundTripped);
      roundTripped.validate(parser, Optional.of(Path.of("/etc/ca.pem")));
    }
  }
}
