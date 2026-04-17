package com.xgen.mongot.index.autoembedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import com.xgen.mongot.embedding.config.MaterializedViewCollectionMetadata.MaterializedViewSchemaMetadata;
import com.xgen.mongot.embedding.mongodb.leasing.LeaseManager;
import com.xgen.mongot.index.IndexMetricValuesSupplier;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.definition.MaterializedViewIndexDefinitionGeneration;
import com.xgen.mongot.index.mongodb.MaterializedViewWriter;
import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.index.version.MaterializedViewGenerationId;
import com.xgen.mongot.metrics.MeterAndFtdcRegistry;
import com.xgen.mongot.metrics.PerIndexMetricsFactory;
import com.xgen.testing.mongot.mock.index.IndexMetricsSupplier;
import com.xgen.testing.mongot.mock.index.MaterializedViewIndex;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.types.ObjectId;
import org.junit.Test;

/**
 * Tests for {@link InitializedMaterializedViewIndex}, including that closing the index unregisters
 * the leader-status gauge (Bug: gauge remained after auto-embedding index drop).
 */
public class InitializedMaterializedViewIndexTest {

  private static final ObjectId INDEX_ID = new ObjectId();
  private static final String NAMESPACE = "embedding.materializedView.stats";

  @Test
  public void getStatus_returnsRecoveringTransient_whenPreviouslyQueryableAndNowInitialSync()
      throws Exception {
    InitializedMaterializedViewIndex index = createIndex(IndexStatus.unknown());

    // Transition to STEADY (queryable)
    index.setStatus(IndexStatus.steady());
    assertEquals(IndexStatus.StatusCode.STEADY, index.getStatus().getStatusCode());

    // Fall off the oplog -> INITIAL_SYNC: should report RECOVERING_TRANSIENT (STALE), not BUILDING
    index.setStatus(IndexStatus.initialSync());
    assertEquals(IndexStatus.StatusCode.RECOVERING_TRANSIENT, index.getStatus().getStatusCode());
  }

  @Test
  public void getStatus_returnsRecoveringTransient_whenPreviouslyQueryableAndNowNotStarted()
      throws Exception {
    InitializedMaterializedViewIndex index = createIndex(IndexStatus.unknown());

    index.setStatus(IndexStatus.steady());
    assertEquals(IndexStatus.StatusCode.STEADY, index.getStatus().getStatusCode());

    // ReplicationManager clears to NOT_STARTED before enqueueing initial sync
    index.setStatus(IndexStatus.notStarted());
    assertEquals(IndexStatus.StatusCode.RECOVERING_TRANSIENT, index.getStatus().getStatusCode());
  }

  @Test
  public void getStatus_returnsInitialSync_whenNeverQueryableAndNowInitialSync() throws Exception {
    InitializedMaterializedViewIndex index = createIndex(IndexStatus.unknown());

    index.setStatus(IndexStatus.initialSync());
    assertEquals(IndexStatus.StatusCode.INITIAL_SYNC, index.getStatus().getStatusCode());
  }

  @Test
  public void getStatus_returnsNotStarted_whenNeverQueryableAndNowNotStarted() throws Exception {
    InitializedMaterializedViewIndex index = createIndex(IndexStatus.unknown());

    index.setStatus(IndexStatus.notStarted());
    assertEquals(IndexStatus.StatusCode.NOT_STARTED, index.getStatus().getStatusCode());
  }

  @Test
  public void getStatus_returnsRecoveringTransient_whenInitializedAsQueryable() throws Exception {
    // Index rehydrated from a previous STEADY state
    InitializedMaterializedViewIndex index = createIndex(IndexStatus.steady());

    index.setStatus(IndexStatus.initialSync());
    assertEquals(IndexStatus.StatusCode.RECOVERING_TRANSIENT, index.getStatus().getStatusCode());
  }

  private InitializedMaterializedViewIndex createIndex(IndexStatus initialStatus) throws Exception {
    MeterAndFtdcRegistry meterAndFtdcRegistry = MeterAndFtdcRegistry.createWithSimpleRegistries();
    MaterializedViewIndexDefinitionGeneration defGen =
        MaterializedViewIndex.mockMatViewDefinitionGeneration(INDEX_ID);
    MaterializedViewGenerationId generationId = defGen.getGenerationId();
    String uniqueString = generationId.uniqueString();
    String collectionName = "matview-" + INDEX_ID.toHexString();
    PerIndexMetricsFactory metricsFactory =
        new PerIndexMetricsFactory(NAMESPACE, meterAndFtdcRegistry, uniqueString, collectionName);
    IndexMetricValuesSupplier metricValuesSupplier =
        IndexMetricsSupplier.mockEmptyIndexMetricsSupplier();
    IndexMetricsUpdater indexMetricsUpdater =
        new IndexMetricsUpdater(defGen.getIndexDefinition(), metricValuesSupplier, metricsFactory);
    MaterializedViewWriter writer = mock(MaterializedViewWriter.class);
    LeaseManager leaseManager = mock(LeaseManager.class);
    doNothing().when(leaseManager).updateReplicationStatus(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.any());
    AtomicReference<IndexStatus> statusRef = new AtomicReference<>(initialStatus);
    MaterializedViewSchemaMetadata schemaMetadata =
        new MaterializedViewSchemaMetadata(0, java.util.Map.of());
    return new InitializedMaterializedViewIndex(
        defGen, writer, indexMetricsUpdater, statusRef, leaseManager, schemaMetadata,
        "__mdb_internal_search");
  }

  @Test
  public void close_whenCalled_unregistersLeaderStatusGauge() throws Exception {
    MeterAndFtdcRegistry meterAndFtdcRegistry = MeterAndFtdcRegistry.createWithSimpleRegistries();
    MeterRegistry meterRegistry = meterAndFtdcRegistry.meterRegistry();

    MaterializedViewIndexDefinitionGeneration defGen =
        MaterializedViewIndex.mockMatViewDefinitionGeneration(INDEX_ID);
    MaterializedViewGenerationId generationId = defGen.getGenerationId();
    String uniqueString = generationId.uniqueString();
    String collectionName = "matview-" + INDEX_ID.toHexString();

    PerIndexMetricsFactory metricsFactory =
        new PerIndexMetricsFactory(NAMESPACE, meterAndFtdcRegistry, uniqueString, collectionName);
    IndexMetricValuesSupplier metricValuesSupplier =
        IndexMetricsSupplier.mockEmptyIndexMetricsSupplier();
    IndexMetricsUpdater indexMetricsUpdater =
        new IndexMetricsUpdater(defGen.getIndexDefinition(), metricValuesSupplier, metricsFactory);

    MaterializedViewWriter writer = mock(MaterializedViewWriter.class);
    LeaseManager leaseManager = mock(LeaseManager.class);
    AtomicReference<IndexStatus> statusRef = new AtomicReference<>(IndexStatus.unknown());
    MaterializedViewSchemaMetadata schemaMetadata =
        new MaterializedViewSchemaMetadata(0, java.util.Map.of());

    InitializedMaterializedViewIndex index =
        new InitializedMaterializedViewIndex(
            defGen, writer, indexMetricsUpdater, statusRef, leaseManager, schemaMetadata,
            "__mdb_internal_search");

    long leaderStatusGaugesBefore =
        meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().contains("leaderStatus"))
            .count();
    assertTrue(
        "leaderStatus gauge should be registered after construction",
        leaderStatusGaugesBefore >= 1);

    index.close();

    long leaderStatusGaugesAfter =
        meterRegistry.getMeters().stream()
            .filter(m -> m.getId().getName().contains("leaderStatus"))
            .count();
    assertEquals(
        "leaderStatus gauge should be unregistered after close()", 0, leaderStatusGaugesAfter);
  }
}
