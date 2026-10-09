package com.xgen.mongot.embedding.providers.clients;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import javax.net.ssl.SSLException;
import org.junit.Test;

public class ConnectionFailuresTest {

  @Test
  public void indicatesConnectionLayerFailure_walksCauseChain() {
    assertTrue(ConnectionFailures.indicatesConnectionLayerFailure(new SSLException("tls")));
    assertTrue(
        ConnectionFailures.indicatesConnectionLayerFailure(
            new IOException("outer", new ConnectException("refused"))));
    assertFalse(ConnectionFailures.indicatesConnectionLayerFailure(new IOException("other")));
  }

  @Test
  public void indicatesConnectionLayerFailure_matchesTransportMessagesCaseInsensitively() {
    for (String message :
        new String[] {
          "Connection reset by peer",
          "Broken pipe",
          "Connection refused",
          "An existing connection was forcibly closed by the remote host",
          "unexpected end of stream on http://tei:80/...",
        }) {
      assertTrue(
          message,
          ConnectionFailures.indicatesConnectionLayerFailure(
              new RuntimeException(new IOException(message))));
    }
    assertFalse(
        ConnectionFailures.indicatesConnectionLayerFailure(new IOException("stream reset")));
    assertFalse(
        ConnectionFailures.indicatesConnectionLayerFailure(
            new IllegalStateException("connection reset")));
  }
}
