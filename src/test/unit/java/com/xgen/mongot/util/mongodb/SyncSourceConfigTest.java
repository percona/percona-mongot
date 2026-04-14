package com.xgen.mongot.util.mongodb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncSourceConfigTest {

  private static final ConnectionInfo MONGOD =
      ConnectionStringUtil.toConnectionInfoUnchecked("mongodb://localhost:27017/");
  private static final ConnectionInfo MONGOS =
      ConnectionStringUtil.toConnectionInfoUnchecked("mongodb://localhost:27018/");

  /** Builds a base config with only the required (non-nullable) cluster URIs set. */
  private static SyncSourceConfig.Builder baseBuilder() {
    return SyncSourceConfig.builder()
        .mongodClusterReplicationUri(MONGOD)
        .mongodClusterReadWriteUri(MONGOD);
  }

  @Test
  public void hasReplicationUrisAvailable_mongodUriAbsent_returnsFalse() {
    assertFalse(baseBuilder().build().hasReplicationUrisAvailable());
  }

  @Test
  public void hasReplicationUrisAvailable_mongodUriPresent_notSharded_returnsTrue() {
    assertTrue(
        baseBuilder().mongodSingleHostReplicationUri(MONGOD).build().hasReplicationUrisAvailable());
  }

  @Test
  public void hasReplicationUrisAvailable_sharded_mongosUriAbsent_returnsFalse() {
    // mongosClusterReadWriteUri is required by the builder for sharded configs;
    // this test specifically checks that an absent mongosSingleHostReplicationUri returns false.
    assertFalse(
        baseBuilder()
            .mongodSingleHostReplicationUri(MONGOD)
            .mongosClusterReadWriteUri(MONGOS)
            .isSharded(true)
            .build()
            .hasReplicationUrisAvailable());
  }

  @Test
  public void hasReplicationUrisAvailable_sharded_allUrisPresent_returnsTrue() {
    assertTrue(
        baseBuilder()
            .mongodSingleHostReplicationUri(MONGOD)
            .mongosSingleHostReplicationUri(MONGOS)
            .mongosClusterReadWriteUri(MONGOS)
            .isSharded(true)
            .build()
            .hasReplicationUrisAvailable());
  }

  @Test(expected = IllegalArgumentException.class)
  public void build_mongodClusterReplicationUriAbsent_throws() {
    SyncSourceConfig.builder().mongodClusterReadWriteUri(MONGOD).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void build_mongodClusterReadWriteUriAbsent_throws() {
    SyncSourceConfig.builder().mongodClusterReplicationUri(MONGOD).build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void build_sharded_mongosClusterReadWriteUriAbsent_throws() {
    baseBuilder().isSharded(true).build();
  }

  @Test
  public void build_sharded_mongosClusterReadWriteUriPresent_doesNotThrow() {
    baseBuilder().isSharded(true).mongosClusterReadWriteUri(MONGOS).build();
  }

  @Test(expected = IllegalStateException.class)
  public void validateReplicationUrisAvailable_mongodUriAbsent_throws() {
    baseBuilder().build().validateReplicationUrisAvailable();
  }

  @Test
  public void validateReplicationUrisAvailable_mongodUriPresent_notSharded_doesNotThrow() {
    baseBuilder().mongodSingleHostReplicationUri(MONGOD).build().validateReplicationUrisAvailable();
  }

  @Test(expected = IllegalStateException.class)
  public void validateReplicationUrisAvailable_sharded_mongosUriAbsent_throws() {
    baseBuilder()
        .mongodSingleHostReplicationUri(MONGOD)
        .mongosClusterReadWriteUri(MONGOS)
        .isSharded(true)
        .build()
        .validateReplicationUrisAvailable();
  }

  @Test
  public void validateReplicationUrisAvailable_sharded_allUrisPresent_doesNotThrow() {
    baseBuilder()
        .mongodSingleHostReplicationUri(MONGOD)
        .mongosSingleHostReplicationUri(MONGOS)
        .mongosClusterReadWriteUri(MONGOS)
        .isSharded(true)
        .build()
        .validateReplicationUrisAvailable();
  }
}
