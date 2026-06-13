package com.xgen.mongot.config.provider.community;

import com.xgen.mongot.replication.mongodb.common.CommonReplicationConfig.GlobalReplicationConfig;
import com.xgen.mongot.replication.mongodb.common.MongoDbReplicationConfig;
import com.xgen.mongot.util.Runtime;
import java.util.Optional;

/**
 * Maps the Community {@code replication.mongodb} config block onto the runtime {@link
 * MongoDbReplicationConfig}.
 */
public final class MongoDbReplicationConfigMapper {

  private MongoDbReplicationConfigMapper() {}

  /**
   * Builds a {@link MongoDbReplicationConfig} from the parsed Community {@code replication} block.
   *
   * @param globalReplicationConfig deployment-wide replication settings
   * @param replicationConfig the parsed {@code replication} block, if present
   * @param runtime runtime used to get numCpus
   */
  public static MongoDbReplicationConfig toMongoDbReplicationConfig(
      GlobalReplicationConfig globalReplicationConfig,
      Runtime runtime,
      Optional<CommunityReplicationConfig> replicationConfig) {
    var mongodb = replicationConfig.flatMap(CommunityReplicationConfig::mongoDbConfig);

    var numConcurrentInitialSyncs = mongodb.flatMap(m -> m.numConcurrentInitialSyncs());
    var numConcurrentChangeStreams = mongodb.flatMap(m -> m.numConcurrentChangeStreams());
    var numIndexingThreads =
        mongodb
            .flatMap(m -> m.numIndexingThreads())
            .or(() -> Optional.of(runtime.getNumCpus()));
    var numConcurrentSynonymSyncs = mongodb.flatMap(m -> m.numConcurrentSynonymSyncs());

    return MongoDbReplicationConfig.create(
        globalReplicationConfig,
        numConcurrentInitialSyncs,
        numConcurrentChangeStreams,
        numIndexingThreads,
        Optional.empty(),
        numConcurrentSynonymSyncs,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
