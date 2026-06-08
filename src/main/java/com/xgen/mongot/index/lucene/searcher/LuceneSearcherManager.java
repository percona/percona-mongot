package com.xgen.mongot.index.lucene.searcher;

import static com.xgen.mongot.util.Check.checkArg;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.featureflag.dynamic.DynamicFeatureFlagRegistry;
import com.xgen.mongot.index.lucene.codec.bloom.BloomCodecPolicy;
import com.xgen.mongot.index.lucene.codec.bloom.MongotBloomReadPolicy;
import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.index.version.Generation;
import com.xgen.mongot.index.version.GenerationId;
import com.xgen.mongot.metrics.CachedGauge;
import com.xgen.mongot.metrics.PerIndexMetricsFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.SearcherManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to safely share {@link LuceneIndexSearcher} instances across multiple threads,
 * while periodically reopening. This class ensures each searcher is closed only once all threads
 * have finished using it. This class does the same as {@link SearcherManager}, but generalized with
 * {@link LuceneIndexSearcher} and accepts {@link LuceneSearcherFactory} in the constructor.
 */
public class LuceneSearcherManager extends ReferenceManager<LuceneIndexSearcher> {

  @VisibleForTesting static final String SEGMENT_COUNT_METRIC = "segment_count";

  private static final Logger LOG = LoggerFactory.getLogger(LuceneSearcherManager.class);

  private final LuceneSearcherFactory factory;
  private final DynamicFeatureFlagRegistry dynamicFeatureFlagRegistry;
  private final boolean enableNaturalOrderScan;
  private final Supplier<IndexStatus> indexStatusSupplier;
  private final IndexWriter indexWriter;

  /**
   * Tracks whether bloom bitsets were loaded on the last reader open. Used to detect the
   * initial-sync → steady-state transition so bloom can be proactively evicted from heap.
   *
   * <p>Accessed only inside {@link #refreshIfNeeded}, which {@link ReferenceManager} calls under
   * its refresh lock, so no additional synchronization is needed.
   */
  private volatile boolean lastBloomLoadState;

  /**
   * Protected constructor - does not register gauges. Use {@link #create} to construct instances.
   * Protected to allow test subclasses.
   */
  protected LuceneSearcherManager(IndexWriter writer, LuceneSearcherFactory factory)
      throws IOException {
    this(
        writer,
        factory,
        DynamicFeatureFlagRegistry.empty(),
        false,
        IndexStatus::steady,
        new GenerationId(factory.indexDefinition.getIndexId(), Generation.CURRENT));
  }

  /**
   * Protected constructor - does not register gauges. Use {@link #create} to construct instances.
   * Protected to allow test subclasses.
   */
  protected LuceneSearcherManager(
      IndexWriter writer,
      LuceneSearcherFactory factory,
      DynamicFeatureFlagRegistry dynamicFeatureFlagRegistry,
      boolean enableNaturalOrderScan,
      Supplier<IndexStatus> indexStatusSupplier,
      GenerationId generationId)
      throws IOException {
    this.factory = factory;
    this.dynamicFeatureFlagRegistry = dynamicFeatureFlagRegistry;
    this.enableNaturalOrderScan = enableNaturalOrderScan;
    this.indexStatusSupplier = indexStatusSupplier;
    this.indexWriter = writer;
    Objects.requireNonNull(generationId, "generationId");
    boolean initialBloomLoad =
        BloomCodecPolicy.getBloomFilterEnabledForIdField(
                dynamicFeatureFlagRegistry,
                enableNaturalOrderScan,
                factory.indexDefinition,
                indexStatusSupplier)
            .getAsBoolean();
    this.lastBloomLoadState = initialBloomLoad;
    MongotBloomReadPolicy.setLoadBloomOnHeap(writer.getDirectory(), initialBloomLoad);
    this.current = getSearcher(DirectoryReader.open(writer));
  }

  /** Static factory method that constructs the manager and registers all gauges. */
  public static LuceneSearcherManager create(
      IndexWriter writer, LuceneSearcherFactory factory, PerIndexMetricsFactory metrics)
      throws IOException {
    return create(
        writer,
        factory,
        metrics,
        DynamicFeatureFlagRegistry.empty(),
        false,
        IndexStatus::steady,
        new GenerationId(factory.indexDefinition.getIndexId(), Generation.CURRENT));
  }

  /** Static factory method that constructs the manager and registers all gauges. */
  public static LuceneSearcherManager create(
      IndexWriter writer,
      LuceneSearcherFactory factory,
      PerIndexMetricsFactory metrics,
      DynamicFeatureFlagRegistry dynamicFeatureFlagRegistry,
      boolean enableNaturalOrderScan,
      Supplier<IndexStatus> indexStatusSupplier,
      GenerationId generationId)
      throws IOException {
    LuceneSearcherManager manager =
        new LuceneSearcherManager(
            writer,
            factory,
            dynamicFeatureFlagRegistry,
            enableNaturalOrderScan,
            indexStatusSupplier,
            generationId);

    // Register gauges after construction is complete
    metrics.perIndexObjectValueGauge(
        SEGMENT_COUNT_METRIC,
        manager,
        CachedGauge.of(LuceneSearcherManager::getSegmentCount, Duration.ofMinutes(1)));

    return manager;
  }

  private double getSegmentCount() {
    try {
      return ((DirectoryReader) this.current.getIndexReader()).getIndexCommit().getSegmentCount();
    } catch (IOException e) {
      LOG.warn("Unable to get current segment count metric", e);
      return Double.NaN;
    }
  }

  @Nullable
  @Override
  protected LuceneIndexSearcher refreshIfNeeded(LuceneIndexSearcher referenceToRefresh)
      throws IOException {
    var oldReader = referenceToRefresh.getIndexReader();
    checkArg(oldReader instanceof DirectoryReader, "IndexReader should be a DirectoryReader");

    boolean loadBloomOnHeap =
        BloomCodecPolicy.getBloomFilterEnabledForIdField(
                this.dynamicFeatureFlagRegistry,
                this.enableNaturalOrderScan,
                this.factory.indexDefinition,
                this.indexStatusSupplier)
            .getAsBoolean();
    MongotBloomReadPolicy.setLoadBloomOnHeap(this.indexWriter.getDirectory(), loadBloomOnHeap);
    boolean evictBloom = this.lastBloomLoadState && !loadBloomOnHeap;
    this.lastBloomLoadState = loadBloomOnHeap;
    DirectoryReader newReader;
    if (evictBloom) {
      // Bloom eviction reopens only the DirectoryReader, not the Directory or IndexWriter.
      // The writer and on-disk index stay open for the life of the index partition.
      //
      // openIfChanged and DirectoryReader.open(IndexWriter) can reuse unchanged SegmentReaders
      // from a prior reader; those readers still hold heap-resident bloom bitsets opened under the
      // initial-sync policy. Reopening from the current IndexCommit snapshot creates new segment
      // readers that honor the updated MongotBloomReadPolicy (bloom sidecar remains on disk; heap
      // bitsets are not loaded when policy is false).
      //
      // ReferenceManager swaps to the returned searcher and release()s the previous one, which
      // decRefs and closes the old DirectoryReader. That closes bloom FieldsProducers and clears
      // their in-heap bitsets (see MongotBloomFilteredFieldsProducer#close).
      newReader = DirectoryReader.open(((DirectoryReader) oldReader).getIndexCommit());
    } else {
      newReader = DirectoryReader.openIfChanged((DirectoryReader) oldReader);
      if (newReader == null) {
        return null;
      }
    }
    return getSearcher(newReader);
  }

  @Override
  protected void decRef(LuceneIndexSearcher reference) throws IOException {
    reference.getIndexReader().decRef();
  }

  @Override
  protected boolean tryIncRef(LuceneIndexSearcher reference) {
    return reference.getIndexReader().tryIncRef();
  }

  @Override
  protected int getRefCount(LuceneIndexSearcher reference) {
    return reference.getIndexReader().getRefCount();
  }

  private LuceneIndexSearcher getSearcher(IndexReader reader) throws IOException {
    @Var var success = false;
    LuceneIndexSearcher searcher;
    try {
      searcher = this.factory.newSearcher(reader, Optional.ofNullable(this.current));
      if (searcher.getIndexReader() != reader) {
        throw new IllegalStateException(
            "SearcherFactory must wrap exactly the provided reader (got "
                + searcher.getIndexReader()
                + " but expected "
                + reader
                + ")");
      }
      success = true;
    } finally {
      if (!success) {
        reader.decRef();
      }
    }
    return searcher;
  }
}
