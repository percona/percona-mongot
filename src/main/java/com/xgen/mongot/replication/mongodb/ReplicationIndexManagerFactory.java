package com.xgen.mongot.replication.mongodb;

import com.xgen.mongot.cursor.MongotCursorManager;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.IndexGeneration;
import com.xgen.mongot.index.InitializedIndex;
import com.xgen.mongot.index.definition.VectorIndexDefinition;
import com.xgen.mongot.replication.mongodb.common.DocumentIndexer;
import com.xgen.mongot.replication.mongodb.initialsync.InitialSyncQueue;
import com.xgen.mongot.replication.mongodb.steadystate.SteadyStateManager;
import com.xgen.mongot.replication.mongodb.synonyms.SynonymManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public interface ReplicationIndexManagerFactory {

  IndexManager create(
      Executor executor,
      MongotCursorManager cursorManager,
      InitialSyncQueue initialSyncQueue,
      SteadyStateManager steadyStateManager,
      Optional<SynonymManager> synonymManager,
      IndexGeneration indexGeneration,
      InitializedIndex index,
      DocumentIndexer documentIndexer,
      ScheduledExecutorService commitExecutor,
      Duration commitInterval,
      Duration requestRateLimitBackoff,
      MeterRegistry meterRegistry,
      FeatureFlags featureFlags,
      boolean enableNaturalOrderScan);

  /**
   * Returns the appropriate factory for the given index generation: a {@link
   * RecyclableReplicationIndexManager} factory for VectorLite indexes, or {@code defaultFactory}
   * for all other indexes.
   */
  static ReplicationIndexManagerFactory forIndexGeneration(
      IndexGeneration indexGeneration, ReplicationIndexManagerFactory defaultFactory) {
    if (indexGeneration.getDefinition() instanceof VectorIndexDefinition vectorDef
        && vectorDef.isCustomVectorEngineIndex()) {
      return RecyclableReplicationIndexManager::create;
    }
    return defaultFactory;
  }
}
