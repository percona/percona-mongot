package com.xgen.mongot.catalogservice;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.xgen.mongot.util.mongodb.CheckedMongoException;
import org.junit.Before;
import org.junit.Test;

public class CatalogAccessGuardTest {

  private MongodTopologyMonitor topologyMonitor;

  @Before
  public void setUp() {
    this.topologyMonitor = mock(MongodTopologyMonitor.class);
  }

  @Test
  public void requireTopologyMatch_passesWhenShardedAndRouterConfigured() throws Exception {
    when(this.topologyMonitor.isShardedCluster()).thenReturn(true);
    var guard = new CatalogAccessGuard(this.topologyMonitor, true);

    guard.requireTopologyMatch(); // should not throw
  }

  @Test
  public void requireTopologyMatch_passesWhenReplicaSetAndRouterMissing() throws Exception {
    when(this.topologyMonitor.isShardedCluster()).thenReturn(false);
    var guard = new CatalogAccessGuard(this.topologyMonitor, false);

    guard.requireTopologyMatch(); // should not throw
  }

  @Test
  public void requireTopologyMatch_throwsWhenShardedButRouterMissing() throws Exception {
    when(this.topologyMonitor.isShardedCluster()).thenReturn(true);
    var guard = new CatalogAccessGuard(this.topologyMonitor, false);

    var e = assertThrows(TopologyMismatchException.class, guard::requireTopologyMatch);
    assertThat(e.getMessage()).contains("sharded");
    assertThat(e.getMessage()).contains("not configured");
  }

  @Test
  public void requireTopologyMatch_throwsWhenReplicaSetButRouterConfigured() throws Exception {
    when(this.topologyMonitor.isShardedCluster()).thenReturn(false);
    var guard = new CatalogAccessGuard(this.topologyMonitor, true);

    var e = assertThrows(TopologyMismatchException.class, guard::requireTopologyMatch);
    assertThat(e.getMessage()).contains("replica set");
    assertThat(e.getMessage()).contains("configured");
  }

  @Test
  public void requireTopologyMatch_propagatesCheckedMongoExceptionFromDetection() throws Exception {
    when(this.topologyMonitor.isShardedCluster())
        .thenThrow(new CheckedMongoException(new MongoException("mongod unavailable")));
    var guard = new CatalogAccessGuard(this.topologyMonitor, true);

    assertThrows(CheckedMongoException.class, guard::requireTopologyMatch);
  }
}
