package com.xgen.mongot.index.lucene;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.xgen.mongot.index.query.DeadlineExceededException;
import java.util.Optional;
import org.apache.lucene.index.QueryTimeout;
import org.junit.Test;

public class TestDeadlineQueryTimeout {

  @Test
  public void shouldNotExitBeforeDeadline() {
    long futureDeadline = System.currentTimeMillis() + 60_000;
    var timeout = new DeadlineQueryTimeout(futureDeadline);
    assertThat(timeout.shouldExit()).isFalse();
  }

  @Test
  public void shouldExitAfterDeadline() {
    long pastDeadline = System.currentTimeMillis() - 1;
    var timeout = new DeadlineQueryTimeout(pastDeadline);
    assertThat(timeout.shouldExit()).isTrue();
  }

  @Test
  public void fromDeadline_present_returnsTimeout() {
    Optional<QueryTimeout> result =
        DeadlineQueryTimeout.fromDeadline(Optional.of(System.currentTimeMillis() + 60_000));
    assertThat(result).isPresent();
    assertThat(result.get()).isInstanceOf(DeadlineQueryTimeout.class);
  }

  @Test
  public void fromDeadline_empty_returnsEmpty() {
    Optional<QueryTimeout> result = DeadlineQueryTimeout.fromDeadline(Optional.empty());
    assertThat(result).isEmpty();
  }

  @Test
  public void throwIfExceeded_pastDeadline_throws() {
    Optional<QueryTimeout> timeout =
        DeadlineQueryTimeout.fromDeadline(Optional.of(System.currentTimeMillis() - 1));
    assertThrows(
        DeadlineExceededException.class, () -> DeadlineQueryTimeout.throwIfExceeded(timeout));
  }

  @Test
  public void throwIfExceeded_futureDeadline_isNoOp() {
    Optional<QueryTimeout> timeout =
        DeadlineQueryTimeout.fromDeadline(Optional.of(System.currentTimeMillis() + 60_000));
    DeadlineQueryTimeout.throwIfExceeded(timeout);
  }

  @Test
  public void throwIfExceeded_empty_isNoOp() {
    DeadlineQueryTimeout.throwIfExceeded(Optional.empty());
  }
}
