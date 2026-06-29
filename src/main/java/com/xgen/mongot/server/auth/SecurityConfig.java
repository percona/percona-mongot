package com.xgen.mongot.server.auth;

import com.xgen.mongot.config.util.AuthMode;
import com.xgen.mongot.config.util.TlsMode;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.Crash;
import com.xgen.mongot.util.mongodb.MongoCredentials;
import java.nio.file.Path;
import java.util.Optional;

public record SecurityConfig(
    AuthMode authMode,
    Optional<String> authUser,
    Optional<String> authPassword,
    TlsMode tlsMode,
    Optional<Path> certKeyFilePath,
    Optional<Path> certAuthFilePath,
    Optional<Path> certKeyFilePasswordFilePath) {
  public SecurityConfig {
    Check.argNotNull(authMode, "authMode");
    Check.argNotNull(tlsMode, "tlsMode");

    if (AuthMode.KEYFILE == authMode && authPassword.isEmpty()) {
      throw new IllegalArgumentException(
          "keyFile cannot be empty when authentication mode is keyfile");
    }

    if (TlsMode.TLS == tlsMode && certKeyFilePath.isEmpty()) {
      throw new IllegalArgumentException(
          "certificateKeyFile cannot be empty when TLS mode is enabled");
    }

    if (TlsMode.MTLS == tlsMode && certAuthFilePath.isEmpty()) {
      throw new IllegalArgumentException("caFile cannot be empty when TLS mode is enabled");
    }
  }

  public static SecurityConfig create(AuthMode authMode, Optional<String> keyfile) {
    return SecurityConfig.create(authMode, keyfile, Optional.of(MongoCredentials.SYSTEM_USER));
  }

  public static SecurityConfig create(
      AuthMode authMode, Optional<String> keyfile, Optional<String> authUser) {
    return new SecurityConfig(
        authMode,
        authUser,
        keyfile,
        TlsMode.DISABLED,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public static SecurityConfig create(
      AuthMode authMode,
      Optional<String> keyfile,
      TlsMode tlsMode,
      Optional<Path> certKeyFilePath) {
    return new SecurityConfig(
        authMode,
        Optional.of(MongoCredentials.SYSTEM_USER),
        keyfile,
        tlsMode,
        certKeyFilePath,
        Optional.empty(),
        Optional.empty());
  }

  public static SecurityConfig createAuthDisabled(
      TlsMode tlsMode,
      Optional<Path> certKeyFilePath,
      Optional<Path> certKeyFilePasswordFilePath,
      Optional<Path> caFilePath) {
    return new SecurityConfig(
        AuthMode.DISABLED,
        Optional.empty(),
        Optional.empty(),
        tlsMode,
        certKeyFilePath,
        caFilePath,
        certKeyFilePasswordFilePath);
  }

  public static Optional<String> getKeyFile(Optional<Path> keyFilePath) {
    return keyFilePath.map(
        path ->
            Crash.because("Failed to read keyFile")
                .ifThrows(() -> MongoCredentials.readKeyFile(path)));
  }
}
