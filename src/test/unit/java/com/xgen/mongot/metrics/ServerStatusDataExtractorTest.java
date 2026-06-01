package com.xgen.mongot.metrics;

import com.xgen.mongot.metrics.ServerStatusDataExtractor.LoadSheddingMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.LuceneMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.MmsMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.ProcessMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.ReplicationMeterData.ChangeStreamMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.ReplicationMeterData.IndexingMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.ReplicationMeterData.InitialSyncMeterData;
import com.xgen.mongot.metrics.ServerStatusDataExtractor.ReplicationMeterData.MongodbClientMeterData;
import com.xgen.testing.mongot.metrics.micrometer.SerializableDistributionSummaryBuilder;
import com.xgen.testing.mongot.metrics.micrometer.SerializableTimerBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ServerStatusDataExtractorTest {
  public MeterRegistry meterRegistry;

  // Micrometer Gauges use weak references. We need to store state objects so that they aren't gc'd.
  private static final Integer TOTAL_HEAP = 4000;

  private static final Integer USED_MEM = 2;

  private static final double PROCESS_UPTIME = 3.0;

  @Before
  public void setupTest() {
    this.meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  public void testJvmMeterData() {
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.MAX_MEMORY_KEY,
        Tags.of(
            ServerStatusDataExtractor.Scope.JVM.getTag(),
            ServerStatusDataExtractor.JvmMeterData.HEAP_TAGS),
        TOTAL_HEAP);
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.USED_MEMORY_KEY,
        Tags.of(
            ServerStatusDataExtractor.Scope.JVM.getTag(),
            ServerStatusDataExtractor.JvmMeterData.HEAP_TAGS),
        USED_MEM);
    var gcTimer =
        this.meterRegistry.timer(
            ServerStatusDataExtractor.JvmMeterData.GC_PAUSE_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.JVM.getTag()));
    gcTimer.record(Duration.ofSeconds(5));
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.PROCESS_UPTIME,
        Tags.of(ServerStatusDataExtractor.Scope.JVM.getTag()),
        PROCESS_UPTIME);

    var meterDataExtractor = new ServerStatusDataExtractor(this.meterRegistry);
    var jvmMeterData = meterDataExtractor.createJvmMeterData();

    Assert.assertEquals(4000.0, jvmMeterData.totalHeapMemory, 0.0);
    Assert.assertEquals(2.0, jvmMeterData.usedHeapMemory, 0.0);
    Assert.assertEquals(
        SerializableTimerBuilder.builder().count(1).totalTime(5.0).max(5.0).mean(5.0).build(),
        jvmMeterData.jvmGcPause);
  }

  @Test
  public void testJvmMeterDataNoGcPause() {
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.MAX_MEMORY_KEY,
        Tags.of(
            ServerStatusDataExtractor.Scope.JVM.getTag(),
            ServerStatusDataExtractor.JvmMeterData.HEAP_TAGS),
        TOTAL_HEAP);
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.USED_MEMORY_KEY,
        Tags.of(
            ServerStatusDataExtractor.Scope.JVM.getTag(),
            ServerStatusDataExtractor.JvmMeterData.HEAP_TAGS),
        USED_MEM);
    this.meterRegistry.gauge(
        ServerStatusDataExtractor.JvmMeterData.PROCESS_UPTIME,
        Tags.of(ServerStatusDataExtractor.Scope.JVM.getTag()),
        PROCESS_UPTIME);

    var jvmMeterData = new ServerStatusDataExtractor(this.meterRegistry).createJvmMeterData();

    Assert.assertEquals(4000.0, jvmMeterData.totalHeapMemory, 0.0);
    Assert.assertEquals(2.0, jvmMeterData.usedHeapMemory, 0.0);
    Assert.assertEquals(SerializableTimerBuilder.builder().build(), jvmMeterData.jvmGcPause);
  }

  @Test
  public void testLuceneMeterData() {
    this.meterRegistry
        .counter(
            LuceneMeterData.NUM_MERGES_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .increment(4);
    this.meterRegistry
        .counter(
            LuceneMeterData.NUM_SEGMENTS_MERGED_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .increment(10);
    this.meterRegistry
        .summary(
            LuceneMeterData.MERGE_SIZE_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .record(3);
    this.meterRegistry
        .summary(
            LuceneMeterData.MERGE_RESULT_SIZE_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .record(3);
    this.meterRegistry
        .summary(
            LuceneMeterData.MERGED_DOCS_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .record(5);
    this.meterRegistry
        .timer(
            LuceneMeterData.SEGMENT_MERGE_TIME_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .record(Duration.ofSeconds(5));
    this.meterRegistry
        .timer(
            LuceneMeterData.MERGE_CANCELLATION_TIME_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .record(Duration.ofMillis(100));
    this.meterRegistry
        .counter(
            LuceneMeterData.NUM_MERGES_ABORTED_KEY,
            Tags.of(ServerStatusDataExtractor.Scope.LUCENE.getTag()))
        .increment(2);

    var luceneMeterData = new ServerStatusDataExtractor(this.meterRegistry).createLuceneMeterData();

    Assert.assertEquals(4.0, luceneMeterData.numMerges, 0.0);
    Assert.assertEquals(10.0, luceneMeterData.numSegmentsMerged, 0.0);
    Assert.assertEquals(
        SerializableDistributionSummaryBuilder.builder()
            .count(1L)
            .total(3.0)
            .max(3.0)
            .mean(3.0)
            .build(),
        luceneMeterData.mergeSize);
    Assert.assertEquals(
        SerializableDistributionSummaryBuilder.builder()
            .count(1L)
            .total(5.0)
            .max(5.0)
            .mean(5.0)
            .build(),
        luceneMeterData.mergedDocs);
    Assert.assertEquals(
        SerializableTimerBuilder.builder().count(1).totalTime(5.0).max(5.0).mean(5.0).build(),
        luceneMeterData.segmentMerge);
    Assert.assertEquals(
        SerializableTimerBuilder.builder().count(1).totalTime(0.1).max(0.1).mean(0.1).build(),
        luceneMeterData.mergeCancellationTime);
    Assert.assertEquals(2.0, luceneMeterData.numMergesAborted, 0.0);
    Assert.assertEquals((Integer) 5, this.meterRegistry.gauge("currentlyMergingDocs", 5));
  }

  @Test
  public void testReplicationMeterData() {
    populateReplicationMeterData(this.meterRegistry);

    ServerStatusDataExtractor.ReplicationMeterData meterData =
        new ServerStatusDataExtractor(this.meterRegistry).createReplicationMeterData();

    Assert.assertEquals(1.0, meterData.stagedIndexes, 0.0);
    Assert.assertEquals(2.0, meterData.liveIndexes, 0.0);
    Assert.assertEquals(3.0, meterData.phasingOutIndexes, 0.0);

    assertIndexingMeterData(meterData.indexingMeterData);
    assertInitialSyncMeterData(meterData.initialSyncMeterData);
    assertChangeStreamMeterData(meterData.changeStreamMeterData);
    assertMongoDbClientMeterData(meterData.mongodbClientMeterData);
  }

  @Test
  public void testDisableReplicationMeterData() {
    populateDisableReplicationMeterData(this.meterRegistry);

    ServerStatusDataExtractor.ReplicationMeterData meterData =
        new ServerStatusDataExtractor(this.meterRegistry).createReplicationMeterData();

    Assert.assertEquals(1.0, meterData.stagedIndexes, 0.0);
    Assert.assertEquals(2.0, meterData.liveIndexes, 0.0);
    Assert.assertEquals(3.0, meterData.phasingOutIndexes, 0.0);

    assertEmptyIndexingMeterData(meterData.indexingMeterData);
    assertEmptyInitialSyncMeterData(meterData.initialSyncMeterData);
    assertEmptyChangeStreamMeterData(meterData.changeStreamMeterData);
    assertEmptyMongoDbClientMeterData(meterData.mongodbClientMeterData);
  }

  public static void populateReplicationMeterData(MeterRegistry meterRegistry) {
    populateDisableReplicationMeterData(meterRegistry);
    populateIndexingMeterData(meterRegistry);
    populateInitialSyncMeterData(meterRegistry);
    populateChangeStreamMeterData(meterRegistry);
    populateMongoDbClientMeterData(meterRegistry);
  }

  public static void populateDisableReplicationMeterData(MeterRegistry meterRegistry) {
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    meterRegistry
        .counter(ServerStatusDataExtractor.ReplicationMeterData.STAGED_INDEXES, replicationTags)
        .increment(1.0);
    meterRegistry
        .counter(ServerStatusDataExtractor.ReplicationMeterData.LIVE_INDEXES, replicationTags)
        .increment(2.0);
    meterRegistry
        .counter(
            ServerStatusDataExtractor.ReplicationMeterData.PHASING_OUT_INDEXES, replicationTags)
        .increment(3.0);
  }

  private static void populateIndexingMeterData(MeterRegistry meterRegistry) {
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    meterRegistry.summary(IndexingMeterData.INDEXING_BATCH_DISTRIBUTION, replicationTags).record(5);
    meterRegistry
        .timer(IndexingMeterData.INDEXING_BATCH_DURATIONS, replicationTags)
        .record(Duration.ofSeconds(2));
  }

  private static void populateInitialSyncMeterData(MeterRegistry meterRegistry) {
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());
    String defaultNamespace = "initialSyncManager";
    meterRegistry
        .counter(defaultNamespace + "." + InitialSyncMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(4);
    meterRegistry
        .counter(defaultNamespace + "." + InitialSyncMeterData.APPLICABLE_UPDATES, replicationTags)
        .increment(5);
  }

  private static void populateChangeStreamMeterData(MeterRegistry meterRegistry) {
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());
    String defaultNamespace = "indexing.steadyStateChangeStream";
    meterRegistry
        .counter(defaultNamespace + "." + ChangeStreamMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(2);
    meterRegistry
        .counter(defaultNamespace + "." + ChangeStreamMeterData.APPLICABLE_UPDATES, replicationTags)
        .increment(3);
  }

  private static void populateMongoDbClientMeterData(MeterRegistry meterRegistry) {
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    meterRegistry
        .counter(
            "replication.sessionRefresher." + MongodbClientMeterData.FAILED_SESSION_REFRESHES,
            replicationTags)
        .increment();
    meterRegistry
        .timer(
            "replication.sessionRefresher." + MongodbClientMeterData.SESSION_REFRESH_DURATIONS,
            replicationTags)
        .record(Duration.ofSeconds(12));
    meterRegistry
        .counter(
            "mongoClientBuilder." + MongodbClientMeterData.SUCCESSFUL_DYNAMIC_LINKING,
            replicationTags)
        .increment();
    meterRegistry
        .counter(
            "mongoClientBuilder." + MongodbClientMeterData.FAILED_DYNAMIC_LINKING, replicationTags)
        .increment(2);
  }

  private void assertIndexingMeterData(IndexingMeterData meterData) {
    Assert.assertEquals(
        SerializableDistributionSummaryBuilder.builder()
            .count(1L)
            .total(5.0)
            .max(5.0)
            .mean(5.0)
            .build(),
        meterData.indexingBatchDistribution);
    Assert.assertEquals(
        SerializableTimerBuilder.builder().count(1).totalTime(2.0).max(2.0).mean(2.0).build(),
        meterData.indexingBatchDurations);
  }

  private void assertEmptyIndexingMeterData(IndexingMeterData meterData) {
    Assert.assertEquals(
        SerializableDistributionSummaryBuilder.builder()
            .count(0L)
            .total(0.0)
            .max(0.0)
            .mean(0.0)
            .build(),
        meterData.indexingBatchDistribution);
    Assert.assertEquals(
        SerializableTimerBuilder.builder()
            .timeUnit(TimeUnit.NANOSECONDS)
            .count(0)
            .totalTime(0.0)
            .max(0.0)
            .mean(0.0)
            .build(),
        meterData.indexingBatchDurations);
  }

  private void assertInitialSyncMeterData(InitialSyncMeterData meterData) {
    Assert.assertEquals(4.0, meterData.witnessedInitialSyncUpdates, 0.0);
    Assert.assertEquals(5.0, meterData.applicableInitialSyncUpdates, 0.0);
  }

  private void assertEmptyInitialSyncMeterData(InitialSyncMeterData meterData) {
    Assert.assertEquals(0.0, meterData.witnessedInitialSyncUpdates, 0.0);
    Assert.assertEquals(0.0, meterData.applicableInitialSyncUpdates, 0.0);
  }

  private void assertChangeStreamMeterData(ChangeStreamMeterData meterData) {
    Assert.assertEquals(2.0, meterData.witnessedChangeStreamUpdates, 0.0);
    Assert.assertEquals(3.0, meterData.applicableChangeStreamUpdates, 0.0);
  }

  private void assertEmptyChangeStreamMeterData(ChangeStreamMeterData meterData) {
    Assert.assertEquals(0.0, meterData.witnessedChangeStreamUpdates, 0.0);
    Assert.assertEquals(0.0, meterData.applicableChangeStreamUpdates, 0.0);
  }

  private void assertMongoDbClientMeterData(MongodbClientMeterData meterData) {
    Assert.assertEquals(1.0, meterData.failedSessionRefreshes, 0.0);
    Assert.assertEquals(
        SerializableTimerBuilder.builder()
            .timeUnit(TimeUnit.SECONDS)
            .count(1)
            .max(12.0)
            .mean(12.0)
            .totalTime(12.0)
            .build(),
        meterData.sessionRefreshDurations);
    Assert.assertEquals(1.0, meterData.successfulDynamicLinking, 0.0);
    Assert.assertEquals(2.0, meterData.failedDynamicLinking, 0.0);
  }

  private void assertEmptyMongoDbClientMeterData(MongodbClientMeterData meterData) {
    Assert.assertEquals(0.0, meterData.failedSessionRefreshes, 0.0);
    Assert.assertEquals(
        SerializableTimerBuilder.builder()
            .timeUnit(TimeUnit.NANOSECONDS)
            .count(0)
            .max(0.0)
            .mean(0.0)
            .totalTime(0.0)
            .build(),
        meterData.sessionRefreshDurations);
    Assert.assertEquals(0.0, meterData.successfulDynamicLinking, 0.0);
    Assert.assertEquals(0.0, meterData.failedDynamicLinking, 0.0);
  }

  @Test
  public void testMmsMeterData() {
    populateMmsMeterData(this.meterRegistry);
    var mmsMeterData = new ServerStatusDataExtractor(this.meterRegistry).createMmsMeterData();

    Assert.assertEquals(
        SerializableTimerBuilder.builder().count(1).totalTime(5.0).max(5.0).mean(5.0).build(),
        mmsMeterData.confCallDurations);
    Assert.assertEquals(4.0, mmsMeterData.successfulConfCalls, 0.0);
    Assert.assertEquals(2.0, mmsMeterData.failedConfCalls, 0.0);
  }

  @Test
  public void testEmptyMmsMeterData() {
    var mmsMeterData = new ServerStatusDataExtractor(this.meterRegistry).createMmsMeterData();

    Assert.assertEquals(
        SerializableTimerBuilder.builder()
            .timeUnit(TimeUnit.NANOSECONDS)
            .count(0)
            .totalTime(0.0)
            .max(0.0)
            .mean(0.0)
            .build(),
        mmsMeterData.confCallDurations);
    Assert.assertEquals(0.0, mmsMeterData.successfulConfCalls, 0.0);
    Assert.assertEquals(0.0, mmsMeterData.failedConfCalls, 0.0);
  }

  public static void populateMmsMeterData(MeterRegistry meterRegistry) {
    Tags mmsTags = Tags.of(ServerStatusDataExtractor.Scope.MMS.getTag());

    meterRegistry
        .timer(MmsMeterData.CONF_CALL_DURATIONS_KEY, mmsTags)
        .record(Duration.ofSeconds(5));
    meterRegistry.counter(MmsMeterData.SUCCESSFUL_CONF_CALLS_KEY, mmsTags).increment(4);
    meterRegistry.counter(MmsMeterData.FAILED_CONF_CALLS_KEY, mmsTags).increment(2);
  }

  @Test
  public void testInitialSyncNamespacePrefixFiltersOutOtherNamespaces() {
    populateDisableReplicationMeterData(this.meterRegistry);
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    this.meterRegistry
        .counter("initialSyncManager." + InitialSyncMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(4);
    this.meterRegistry
        .counter("initialSyncManager." + InitialSyncMeterData.APPLICABLE_UPDATES, replicationTags)
        .increment(5);

    // Same short name but different namespace — should be filtered out
    this.meterRegistry
        .counter("autoEmbedding." + InitialSyncMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(100);
    this.meterRegistry
        .counter("autoEmbedding." + InitialSyncMeterData.APPLICABLE_UPDATES, replicationTags)
        .increment(200);

    ServerStatusDataExtractor.ReplicationMeterData meterData =
        new ServerStatusDataExtractor(this.meterRegistry).createReplicationMeterData();

    Assert.assertEquals(4.0, meterData.initialSyncMeterData.witnessedInitialSyncUpdates, 0.0);
    Assert.assertEquals(5.0, meterData.initialSyncMeterData.applicableInitialSyncUpdates, 0.0);
  }

  @Test
  public void testNamespacePrefixReturnsZeroWhenNoMetersMatchPrefix() {
    populateDisableReplicationMeterData(this.meterRegistry);
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    // Register meters with the correct short name but under a non-matching namespace
    this.meterRegistry
        .counter("autoEmbedding." + InitialSyncMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(100);
    this.meterRegistry
        .counter("autoEmbedding." + InitialSyncMeterData.APPLICABLE_UPDATES, replicationTags)
        .increment(200);

    ServerStatusDataExtractor.ReplicationMeterData meterData =
        new ServerStatusDataExtractor(this.meterRegistry).createReplicationMeterData();

    // "initialSyncManager" prefix matches nothing, so values should be zero
    Assert.assertEquals(0.0, meterData.initialSyncMeterData.witnessedInitialSyncUpdates, 0.0);
    Assert.assertEquals(0.0, meterData.initialSyncMeterData.applicableInitialSyncUpdates, 0.0);
  }

  @Test(expected = IllegalStateException.class)
  public void testNamespacePrefixThrowsWhenMultipleMetersMatchSamePrefix() {
    populateDisableReplicationMeterData(this.meterRegistry);
    Tags replicationTags = Tags.of(ServerStatusDataExtractor.Scope.REPLICATION.getTag());

    // Two meters under the same namespace prefix with the same short name
    this.meterRegistry
        .counter("initialSyncManager." + InitialSyncMeterData.WITNESSED_UPDATES, replicationTags)
        .increment(1);
    this.meterRegistry
        .counter(
            "initialSyncManager." + InitialSyncMeterData.WITNESSED_UPDATES,
            replicationTags.and("extraTag", "value"))
        .increment(2);

    // This should throw because getOptionalSingleMeters finds 2 meters matching the prefix
    new ServerStatusDataExtractor(this.meterRegistry).createReplicationMeterData();
  }

  @Test
  public void testProcessMeterData() {
    this.meterRegistry.gauge(ProcessMeterData.MAJOR_PAGE_FAULTS, 3);
    this.meterRegistry.gauge(ProcessMeterData.MINOR_PAGE_FAULTS, 11);

    var processMeterData =
        new ServerStatusDataExtractor(this.meterRegistry).createProcessMeterData();

    Assert.assertEquals(3.0, processMeterData.majorPageFaults, 0.0);
    Assert.assertEquals(11.0, processMeterData.minorPageFaults, 0.0);
  }

  @Test
  public void testLoadSheddingMeterData() {
    // Variant 1 host: only the wouldHaveRejected counter is registered.
    this.meterRegistry
        .counter(LoadSheddingMeterData.WOULD_HAVE_REJECTED, "executor", "blocking-server-worker")
        .increment(7);

    var loadSheddingMeterData =
        new ServerStatusDataExtractor(this.meterRegistry).createLoadSheddingMeterData();

    Assert.assertEquals(7.0, loadSheddingMeterData.wouldHaveRejectedTotal, 0.0);
    Assert.assertEquals(0.0, loadSheddingMeterData.rejectedTotal, 0.0);
  }

  @Test
  public void testLoadSheddingMeterDataRejected() {
    // Variant 2 host: only the rejected counter is registered.
    this.meterRegistry
        .counter(LoadSheddingMeterData.REJECTED, "executor", "blocking-server-worker")
        .increment(9);

    var loadSheddingMeterData =
        new ServerStatusDataExtractor(this.meterRegistry).createLoadSheddingMeterData();

    Assert.assertEquals(0.0, loadSheddingMeterData.wouldHaveRejectedTotal, 0.0);
    Assert.assertEquals(9.0, loadSheddingMeterData.rejectedTotal, 0.0);
  }

  @Test
  public void testEmptyLoadSheddingMeterData() {
    var loadSheddingMeterData =
        new ServerStatusDataExtractor(this.meterRegistry).createLoadSheddingMeterData();

    Assert.assertEquals(0.0, loadSheddingMeterData.wouldHaveRejectedTotal, 0.0);
    Assert.assertEquals(0.0, loadSheddingMeterData.rejectedTotal, 0.0);
  }
}
