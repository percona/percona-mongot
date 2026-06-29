package com.xgen.mongot.index.lucene.codec.bloom;

import com.xgen.mongot.index.lucene.codec.bloom.MongotBloomFilteringPostingsFormat.MongotBloomFilteredFieldsProducer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.store.Directory;

/**
 * Tracks heap-loaded {@link MongotBloomFilteredFieldsProducer} instances by segment and field.
 *
 * <p>Lucene's {@code PerFieldPostingsFormat.FieldsReader} nests per-field producers without a
 * public accessor (unlike {@code PerFieldKnnVectorsFormat.FieldsReader#getFieldReader}).
 * Registration lets {@link MongotBloomHeapEvictor} reach the {@code _id} bloom producer without
 * reflection.
 */
final class MongotBloomFieldsProducerRegistry {

  private static final ConcurrentHashMap<Key, MongotBloomFilteredFieldsProducer> PRODUCERS =
      new ConcurrentHashMap<>();

  private MongotBloomFieldsProducerRegistry() {}

  static Key register(
      SegmentReadState state, String fieldName, MongotBloomFilteredFieldsProducer producer) {
    Key key = Key.from(state, fieldName);
    PRODUCERS.put(key, producer);
    return key;
  }

  static void unregister(Key key, MongotBloomFilteredFieldsProducer producer) {
    PRODUCERS.remove(key, producer);
  }

  static Optional<MongotBloomFilteredFieldsProducer> lookup(
      SegmentReader reader, String fieldName) {
    return Optional.ofNullable(PRODUCERS.get(Key.from(reader, fieldName)));
  }

  static final class Key {
    private final Directory directory;
    private final String segmentName;
    private final byte[] segmentId;
    private final String fieldName;

    private Key(Directory directory, String segmentName, byte[] segmentId, String fieldName) {
      this.directory = directory;
      this.segmentName = segmentName;
      this.segmentId = segmentId;
      this.fieldName = fieldName;
    }

    static Key from(SegmentReadState state, String fieldName) {
      return new Key(
          state.segmentInfo.dir, state.segmentInfo.name, state.segmentInfo.getId(), fieldName);
    }

    static Key from(SegmentReader reader, String fieldName) {
      var segmentInfo = reader.getSegmentInfo().info;
      return new Key(reader.directory(), segmentInfo.name, segmentInfo.getId(), fieldName);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Key that)) {
        return false;
      }
      return this.directory == that.directory
          && this.segmentName.equals(that.segmentName)
          && Arrays.equals(this.segmentId, that.segmentId)
          && this.fieldName.equals(that.fieldName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          System.identityHashCode(this.directory),
          this.segmentName,
          Arrays.hashCode(this.segmentId),
          this.fieldName);
    }
  }
}
