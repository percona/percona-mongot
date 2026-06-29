package com.xgen.mongot.index.lucene.codec.bloom;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.junit.Test;

public class MongotBloomReadPolicyTest {

  @Test
  public void setLoadBloomOnHeap_registersUnderlyingStorageDirectory() throws IOException {
    try (Directory base = new ByteBuffersDirectory();
        Directory wrapped = new FilterDirectory(base) {}) {
      MongotBloomReadPolicy.setLoadBloomOnHeap(wrapped, true);
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(base)).isTrue();
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(wrapped)).isTrue();

      MongotBloomReadPolicy.setLoadBloomOnHeap(base, false);
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(base)).isFalse();
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(wrapped)).isFalse();
    }
  }

  @Test
  public void setLoadBloomOnHeap_unwrapsNestedFilterDirectories() throws IOException {
    try (Directory base = new ByteBuffersDirectory();
        Directory outer = new FilterDirectory(new FilterDirectory(base) {}) {}) {
      MongotBloomReadPolicy.setLoadBloomOnHeap(outer, true);
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(base)).isTrue();

      MongotBloomReadPolicy.setLoadBloomOnHeap(outer, false);
      assertThat(MongotBloomReadPolicy.shouldLoadBloomOnRead(base)).isFalse();
    }
  }
}
