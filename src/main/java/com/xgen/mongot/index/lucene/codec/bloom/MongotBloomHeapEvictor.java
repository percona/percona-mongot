package com.xgen.mongot.index.lucene.codec.bloom;

import com.xgen.mongot.index.lucene.codec.bloom.MongotBloomFilteringPostingsFormat.MongotBloomFilteredFieldsProducer;
import com.xgen.mongot.index.lucene.field.FieldName;
import java.util.Optional;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentReader;

/**
 * Clears heap-resident bloom bitsets from open segment readers without closing them.
 *
 * <p>Called from {@link com.xgen.mongot.index.lucene.searcher.LuceneSearcherManager} when the bloom
 * read policy flips from initial sync (load bloom on heap) to steady state (skip heap load). That
 * transition is driven by dynamic feature flags at searcher refresh time, not by Lucene merges.
 *
 * <p>A merge-scheduler hook is not sufficient: {@code DirectoryReader.openIfChanged} can reuse
 * existing {@link SegmentReader} instances that already deserialized bloom bitsets, and
 * initial-sync bloom segments may remain in the index until background merges compact them away.
 * Eviction must run on those live readers at policy flip time so memory is released without
 * breaking near-real-time refresh.
 */
public final class MongotBloomHeapEvictor {

  private static final String ID_FIELD = FieldName.MetaField.ID.getLuceneFieldName();

  private MongotBloomHeapEvictor() {}

  public static void evictHeapBloomFromReader(DirectoryReader reader) {
    for (LeafReaderContext leaf : reader.leaves()) {
      if (leaf.reader() instanceof SegmentReader segmentReader) {
        findIdBloomFieldsProducer(segmentReader.getPostingsReader(), segmentReader)
            .ifPresent(MongotBloomFilteredFieldsProducer::evictHeapBloom);
      }
    }
  }

  private static Optional<MongotBloomFilteredFieldsProducer> findIdBloomFieldsProducer(
      FieldsProducer producer, SegmentReader segmentReader) {
    if (producer instanceof MongotBloomFilteredFieldsProducer bloomProducer) {
      return Optional.of(bloomProducer);
    }
    return MongotBloomFieldsProducerRegistry.lookup(segmentReader, ID_FIELD);
  }
}
