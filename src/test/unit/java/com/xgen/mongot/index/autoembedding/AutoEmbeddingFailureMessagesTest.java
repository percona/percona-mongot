package com.xgen.mongot.index.autoembedding;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AutoEmbeddingFailureMessagesTest {

  // Hardcodes the literal on purpose: it is a cross-service contract matched by the MMS alert, so a
  // change to FAILURE_PREFIX must break this test.
  @Test
  public void failurePrefix_isExactCrossServiceLiteral() {
    assertEquals("Automated Embedding Index Failed: ", AutoEmbeddingFailureMessages.FAILURE_PREFIX);
  }

  @Test
  public void withFailurePrefix_prependsPrefix() {
    assertEquals(
        "Automated Embedding Index Failed: boom",
        AutoEmbeddingFailureMessages.withFailurePrefix("boom"));
  }
}
