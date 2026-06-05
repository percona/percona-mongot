package com.xgen.mongot.util;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import org.jetbrains.annotations.Nullable;

public class MongotVersionResolver {
  private static final String BUILD_DATA_PATH = "/build-data.properties";
  private static final String VERSION_KEY = "build.label";

  private final String mongotVersion;

  MongotVersionResolver(String mongotVersion) {
    this.mongotVersion = mongotVersion;
  }

  public static MongotVersionResolver create() {
    return new MongotVersionResolver(
        Crash.because("could not read mongotVersion").ifThrows(MongotVersionResolver::readVersion));
  }

  public static Optional<String> getOptional() {
    try {
      return Optional.of(readVersion());
    } catch (VersionNotFoundException e) {
      return Optional.empty();
    }
  }

  public String getVersion() {
    return this.mongotVersion;
  }

  private static String readVersion() throws VersionNotFoundException {
    try (@Nullable
        var inputStream = MongotVersionResolver.class.getResourceAsStream(BUILD_DATA_PATH)) {
      if (inputStream == null) {
        throw new VersionNotFoundException(String.format("could not find %s", BUILD_DATA_PATH));
      }

      Properties properties = new Properties();
      try {
        properties.load(inputStream);
      } catch (IllegalArgumentException e) {
        throw new VersionNotFoundException(
            String.format("%s is improperly encoded", BUILD_DATA_PATH), e);
      }

      Optional<String> optionalVersion = Optional.ofNullable(properties.getProperty(VERSION_KEY));
      return optionalVersion.orElseThrow(
          () ->
              new VersionNotFoundException(
                  String.format("could not find %s in %s", VERSION_KEY, BUILD_DATA_PATH)));
    } catch (IOException e) {
      throw new VersionNotFoundException(String.format("could not read %s", BUILD_DATA_PATH), e);
    }
  }

  static class VersionNotFoundException extends LoggableException {

    VersionNotFoundException(String message) {
      super(message);
    }

    VersionNotFoundException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
