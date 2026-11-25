package com.xgen.mongot.embedding.providers.clients;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Locale;
import javax.net.ssl.SSLException;

/**
 * Classifies transport errors for the HTTP embedding clients. Mirrors the private copy in {@link
 * VoyageClient} (upstream code, left untouched to avoid merge conflicts); keep the two in sync.
 */
final class ConnectionFailures {

  private ConnectionFailures() {}

  /**
   * True if the error (or a cause) is a TLS/connection failure where a fresh client may help the
   * retry.
   */
  static boolean indicatesConnectionLayerFailure(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      if (t instanceof SSLException || t instanceof ConnectException) {
        return true;
      }
      if (t instanceof IOException) {
        String message = t.getMessage();
        if (message != null) {
          String lower = message.toLowerCase(Locale.ROOT);
          if (lower.contains("connection reset")
              || lower.contains("broken pipe")
              || lower.contains("connection refused")
              || lower.contains("forcibly closed")
              || lower.contains("unexpected end of stream")) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
