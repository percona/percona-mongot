package com.xgen.mongot.catalogservice;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.xgen.mongot.util.concurrent.NamedScheduledExecutorService;
import com.xgen.mongot.util.mongodb.CheckedMongoException;
import com.xgen.testing.util.ManuallyUpdatedClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MongodTopologyMonitorTest {

  private static final Duration REFRESH_INTERVAL = Duration.ofMillis(200);
  private static final Duration EXPIRY = Duration.ofMillis(500);
  private static final Instant INITIAL_INSTANT = Instant.parse("2026-05-24T00:00:00Z");
  private static final BsonDocument GET_CMD_LINE_OPTS =
      new BsonDocument("getCmdLineOpts", new BsonInt32(1));
  private static final BsonDocument SHARDED_RESULT =
      new BsonDocument("parsed", new BsonDocument("sharding", new BsonDocument()));
  private static final BsonDocument NOT_SHARDED_RESULT =
      new BsonDocument("parsed", new BsonDocument());

  @Mock
  private MongoClient mongoClient;
  @Mock
  private MongoDatabase mongoDatabase;
  @Mock
  private NamedScheduledExecutorService scheduler;
  private ManuallyUpdatedClock clock;
  private MongodTopologyMonitor monitor;

  @Before
  public void setUp() throws InterruptedException {
    MockitoAnnotations.openMocks(this);
    when(this.mongoClient.getDatabase("admin")).thenReturn(this.mongoDatabase);
    when(this.scheduler.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
    this.clock = new ManuallyUpdatedClock(Clock.fixed(INITIAL_INSTANT, ZoneId.of("UTC")));
    this.monitor = new MongodTopologyMonitor(
        this.mongoClient, this.scheduler, REFRESH_INTERVAL, EXPIRY, this.clock);
  }

  @After
  public void tearDown() {
    if (this.monitor != null) {
      this.monitor.close();
    }
  }

  @Test
  public void isShardedCluster_throwsWhenCacheEmptyAndSubmitsAsyncProbe() {
    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);
    verify(this.scheduler, times(1)).submit(any(Runnable.class));
  }

  @Test
  public void isShardedCluster_coalescesConcurrentAsyncProbes() {
    for (int i = 0; i < 5; i++) {
      assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);
    }
    verify(this.scheduler, times(1)).submit(any(Runnable.class));
  }

  @Test
  public void isShardedCluster_asyncProbePopulatesCache() throws Exception {
    runAsyncProbesSynchronously();
    stubGetCmdLineOpts(/* sharded= */ true);

    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);

    assertThat(this.monitor.isShardedCluster()).isTrue();
    verify(this.scheduler, times(1)).submit(any(Runnable.class));
    verify(this.mongoDatabase, times(1))
        .runCommand(eq(GET_CMD_LINE_OPTS), eq(BsonDocument.class));
  }

  @Test
  public void isShardedCluster_asyncProbeSkipsRefreshWhenCacheAlreadyPopulated() throws Exception {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);
    verify(this.scheduler).submit(captor.capture());

    stubGetCmdLineOpts(/* sharded= */ true);
    this.monitor.refreshCache();
    clearInvocations(this.mongoDatabase);

    captor.getValue().run();

    verify(this.mongoDatabase, never())
        .runCommand(eq(GET_CMD_LINE_OPTS), eq(BsonDocument.class));
  }

  @Test
  public void isShardedCluster_doesNotSubmitProbeWhenCacheStale() throws Exception {
    stubGetCmdLineOpts(/* sharded= */ false);
    this.monitor.refreshCache();
    this.clock.update(EXPIRY.plusSeconds(1));

    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);

    verify(this.scheduler, never()).submit(any(Runnable.class));
  }

  @Test
  public void isShardedCluster_doesNotSubmitProbeAfterClose() {
    this.monitor.close();

    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);

    verify(this.scheduler, never()).submit(any(Runnable.class));
  }

  @Test
  public void isShardedCluster_resetsProbeFlightOnRejectedExecution() {
    when(this.scheduler.submit(any(Runnable.class)))
        .thenThrow(new RejectedExecutionException("simulated"));

    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);
    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);

    verify(this.scheduler, times(2)).submit(any(Runnable.class));
  }

  @Test
  public void isShardedCluster_subsequentReadsServeFromCache() throws Exception {
    stubGetCmdLineOpts(/* sharded= */ false);
    this.monitor.refreshCache();

    for (int i = 0; i < 5; i++) {
      assertThat(this.monitor.isShardedCluster()).isFalse();
    }
    verify(this.mongoDatabase, times(1))
        .runCommand(eq(GET_CMD_LINE_OPTS), eq(BsonDocument.class));
  }

  @Test
  public void isShardedCluster_throwsAfterExpiry() throws Exception {
    stubGetCmdLineOpts(/* sharded= */ false);
    this.monitor.refreshCache();
    assertThat(this.monitor.isShardedCluster()).isFalse();

    // Advance the clock past the expiry; the cached value is now stale, so isShardedCluster
    // throws until the periodic scheduler refreshes the cache.
    this.clock.update(EXPIRY.plusSeconds(1));
    assertThrows(CheckedMongoException.class, this.monitor::isShardedCluster);

    // The next periodic refreshCache repopulates the cache; subsequent reads serve the new value.
    stubGetCmdLineOpts(/* sharded= */ true);
    this.monitor.refreshCache();
    assertThat(this.monitor.isShardedCluster()).isTrue();
  }

  @Test
  public void refreshCache_updatesTimestampAndShardedValue() throws Exception {
    stubGetCmdLineOpts(/* sharded= */ true);
    this.monitor.refreshCache();
    var primed = this.monitor.cachedEntry().orElseThrow();
    assertThat(primed.sharded()).isTrue();
    assertThat(primed.refreshedAt()).isEqualTo(INITIAL_INSTANT);

    // Advance the clock, change the underlying query result, and refresh again.
    Duration delta = Duration.ofSeconds(30);
    this.clock.update(delta);
    stubGetCmdLineOpts(/* sharded= */ false);
    this.monitor.refreshCache();

    var refreshed = this.monitor.cachedEntry().orElseThrow();
    assertThat(refreshed.sharded()).isFalse();
    assertThat(refreshed.refreshedAt()).isEqualTo(INITIAL_INSTANT.plus(delta));
  }

  @Test
  public void refreshCache_keepsLastGoodCacheOnPeriodicFailure() throws Exception {
    stubGetCmdLineOpts(/* sharded= */ true);
    this.monitor.refreshCache();
    var primed = this.monitor.cachedEntry().orElseThrow();

    stubGetCmdLineOptsThrows();
    this.monitor.refreshCache();
    assertThat(this.monitor.cachedEntry()).hasValue(primed);
  }

  @Test
  public void close_stopsSchedulerAndClosesClient() {
    this.monitor.start();
    this.monitor.close();

    verify(this.scheduler).shutdown();
    verify(this.mongoClient).close();
  }

  private void stubGetCmdLineOpts(boolean sharded) {
    when(this.mongoDatabase.runCommand(eq(GET_CMD_LINE_OPTS), eq(BsonDocument.class)))
        .thenReturn(sharded ? SHARDED_RESULT : NOT_SHARDED_RESULT);
  }

  private void stubGetCmdLineOptsThrows() {
    when(this.mongoDatabase.runCommand(eq(GET_CMD_LINE_OPTS), eq(BsonDocument.class)))
        .thenThrow(new MongoException("simulated failure"));
  }

  private void runAsyncProbesSynchronously() {
    when(this.scheduler.submit(any(Runnable.class)))
        .thenAnswer(invocation -> {
          invocation.<Runnable>getArgument(0).run();
          return null;
        });
  }

}
