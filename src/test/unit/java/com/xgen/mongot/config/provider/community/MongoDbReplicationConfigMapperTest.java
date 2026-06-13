package com.xgen.mongot.config.provider.community;

import static org.junit.Assert.assertEquals;

import com.xgen.mongot.replication.mongodb.common.CommonReplicationConfig;
import com.xgen.mongot.replication.mongodb.common.MongoDbReplicationConfig;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Runtime;
import com.xgen.testing.util.MockRuntimeBuilder;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

public class MongoDbReplicationConfigMapperTest {

  private static final Path TUNING_CONFIG =
      Path.of("src/test/unit/resources/config/provider/community/communityConfigTuning.yaml");

  @Test
  public void toMongoDbReplicationConfig_appliesNumIndexingThreadsDefaultWhenUnset() {
    Runtime runtime =
        new MockRuntimeBuilder().withNumCpus(7).withMaxHeapSize(Bytes.ofMebi(512)).build();

    MongoDbReplicationConfig rc =
        MongoDbReplicationConfigMapper.toMongoDbReplicationConfig(
            CommonReplicationConfig.defaultGlobalReplicationConfig(), runtime, Optional.empty());
    assertEquals(7, rc.numIndexingThreads);
  }

  @Test
  public void toMongoDbReplicationConfig_honorsConfiguredValues() throws Exception {
    CommunityConfig config = CommunityConfig.readFromFile(TUNING_CONFIG).config();
    Runtime runtime =
        new MockRuntimeBuilder().withNumCpus(99).withMaxHeapSize(Bytes.ofMebi(512)).build();

    MongoDbReplicationConfig rc =
        MongoDbReplicationConfigMapper.toMongoDbReplicationConfig(
            CommonReplicationConfig.defaultGlobalReplicationConfig(),
            runtime,
            config.replicationConfig());

    assertEquals(3, rc.numConcurrentInitialSyncs);
    assertEquals(12, rc.numConcurrentChangeStreams);
    assertEquals(8, rc.numIndexingThreads);
    assertEquals(2, rc.numConcurrentSynonymSyncs);
  }
}
