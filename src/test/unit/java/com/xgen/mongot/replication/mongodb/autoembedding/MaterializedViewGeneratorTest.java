package com.xgen.mongot.replication.mongodb.autoembedding;

import static com.xgen.testing.mongot.mock.index.MaterializedViewIndex.mockMatViewDefinitionGeneration;
import static com.xgen.testing.mongot.mock.index.MaterializedViewIndex.mockMatViewIndexGeneration;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.xgen.mongot.cursor.MongotCursorManager;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.autoembedding.InitializedMaterializedViewIndex;
import com.xgen.mongot.index.autoembedding.MaterializedViewIndexGeneration;
import com.xgen.mongot.replication.mongodb.common.DocumentIndexer;
import com.xgen.mongot.replication.mongodb.common.PeriodicIndexCommitter;
import com.xgen.mongot.replication.mongodb.initialsync.InitialSyncQueue;
import com.xgen.mongot.replication.mongodb.steadystate.SteadyStateManager;
import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedExecutorService;
import com.xgen.testing.mongot.metrics.SimpleMetricsFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link MaterializedViewGenerator}'s role-switching functionality.
 *
 * <p>These tests verify the behavior of the leader/follower role management methods: {@link
 * MaterializedViewGenerator#isLeader()} and {@link MaterializedViewGenerator#becomeLeader()}.
 *
 * <p>All generators are created as followers. Call {@link MaterializedViewGenerator#becomeLeader()}
 * to activate leader mode. To transition back to follower mode, the generator must be shut down and
 * replaced with a new generator (see {@link MaterializedViewManager#transitionToFollower}).
 */
public class MaterializedViewGeneratorTest {

  private NamedExecutorService executorService;

  @Before
  public void setUp() {
    this.executorService =
        Executors.fixedSizeThreadPool("test-indexing", 1, new SimpleMeterRegistry());
  }

  @After
  public void tearDown() {
    if (this.executorService != null) {
      this.executorService.shutdown();
    }
  }

  @Test
  public void isLeader_newlyCreated_returnsFalse() {
    MaterializedViewGenerator generator = createGenerator();
    assertFalse(generator.isLeader());
  }

  @Test
  public void becomeLeader_whenFollower_setsIsLeaderTrue() {
    MaterializedViewGenerator generator = createGenerator();
    assertFalse(generator.isLeader());

    generator.becomeLeader();

    assertTrue(generator.isLeader());
  }

  @Test
  public void becomeLeader_whenAlreadyLeader_remainsLeader() {
    MaterializedViewGenerator generator = createGenerator();
    generator.becomeLeader();
    assertTrue(generator.isLeader());

    generator.becomeLeader();

    assertTrue(generator.isLeader());
  }

  @Test
  public void becomeLeader_callsSetLeaderModeTrue() {
    InitializedMaterializedViewIndex matViewIndex = mock(InitializedMaterializedViewIndex.class);
    MaterializedViewGenerator generator = createGeneratorWithIndex(matViewIndex);

    generator.becomeLeader();

    assertTrue(generator.isLeader());
    verify(matViewIndex).setLeaderMode(true);
  }

  @Test
  public void shutdown_callsSetLeaderModeFalse() throws Exception {
    InitializedMaterializedViewIndex matViewIndex = mock(InitializedMaterializedViewIndex.class);
    MaterializedViewGenerator generator = createGeneratorWithIndex(matViewIndex);
    generator.becomeLeader();

    generator.shutdown().get();

    // setLeaderMode(false) may be called more than once: once by initReplication()'s terminal
    // state guard (if init fails before shutdown completes) and once by shutdown() itself.
    verify(matViewIndex, atLeastOnce()).setLeaderMode(false);
  }

  @Test
  public void shutdown_clearsIsLeader() throws Exception {
    MaterializedViewGenerator generator = createGenerator();
    generator.becomeLeader();
    assertTrue("Should be leader after becomeLeader()", generator.isLeader());

    generator.shutdown().get();

    assertFalse("Should not be leader after shutdown()", generator.isLeader());
  }

  @Test
  public void shutdown_calledTwice_returnsSameFuture() throws Exception {
    MaterializedViewGenerator generator = createGenerator();
    generator.becomeLeader();

    CompletableFuture<Void> first = generator.shutdown();
    CompletableFuture<Void> second = generator.shutdown();

    assertSame("Second shutdown() must return the same future as the first", first, second);
  }

  private MaterializedViewGenerator createGenerator() {
    return createGeneratorWithIndex(mock(InitializedMaterializedViewIndex.class));
  }

  private MaterializedViewGenerator createGeneratorWithIndex(
      InitializedMaterializedViewIndex matViewIndex) {
    SimpleMetricsFactory metricsFactory = new SimpleMetricsFactory();
    MaterializedViewIndexGeneration indexGeneration =
        mockMatViewIndexGeneration(mockMatViewDefinitionGeneration(new ObjectId()));

    return MaterializedViewGenerator.create(
        this.executorService,
        mock(MongotCursorManager.class),
        mock(InitialSyncQueue.class),
        mock(SteadyStateManager.class),
        indexGeneration,
        matViewIndex,
        mock(DocumentIndexer.class),
        mock(PeriodicIndexCommitter.class),
        Duration.ofSeconds(30),
        Duration.ofSeconds(30),
        Duration.ofSeconds(1),
        metricsFactory.meterRegistry,
        mock(FeatureFlags.class),
        false);
  }
}

