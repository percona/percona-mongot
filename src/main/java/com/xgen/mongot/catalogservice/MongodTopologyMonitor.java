package com.xgen.mongot.catalogservice;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedScheduledExecutorService;
import com.xgen.mongot.util.mongodb.CheckedMongoException;
import com.xgen.mongot.util.mongodb.MongoClientBuilder;
import com.xgen.mongot.util.mongodb.SyncSourceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether the connected mongod is part of a sharded cluster. Consumed by
 * {@link CatalogAccessGuard} to block catalog reads/writes when the mongot's
 * {@code syncSource.router} config does not match the real topology.
 *
 * <p>The cached value may lag real topology by up to one refresh interval. This is safe for the
 * catalog-access guard because the harmful case (RS-to-sharded conversion) requires moving the
 * catalog database to another shard, and {@code moveCollection} takes at least 5 minutes. With a
 * 1-minute refresh interval, a stale {@code isSharded=false} read cannot cause problems.
 */
public class MongodTopologyMonitor implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(MongodTopologyMonitor.class);

  public static final String MONGO_CLIENT_NAME = "MongodTopologyMonitor";
  public static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofMinutes(1);
  public static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(3);

  private final MongoClient topologyClient;
  private final NamedScheduledExecutorService scheduler;
  private final Duration refreshInterval;
  private final Duration expiry;
  private final Clock clock;

  private final AtomicReference<CacheEntry> cache = new AtomicReference<>();
  private final AtomicBoolean probeInFlight = new AtomicBoolean(false);
  private volatile boolean closed = false;

  @VisibleForTesting
  record CacheEntry(boolean sharded, Instant refreshedAt) {}

  public MongodTopologyMonitor(SyncSourceConfig syncSource, MeterRegistry meterRegistry) {
    this(
        buildClient(syncSource, meterRegistry),
        Executors.singleThreadScheduledExecutor("mongod-topology-monitor", meterRegistry),
        DEFAULT_REFRESH_INTERVAL,
        DEFAULT_EXPIRY,
        Clock.systemUTC());
  }

  @VisibleForTesting
  MongodTopologyMonitor(
      MongoClient topologyClient,
      NamedScheduledExecutorService scheduler,
      Duration refreshInterval,
      Duration expiry,
      Clock clock) {
    this.topologyClient = topologyClient;
    this.scheduler = scheduler;
    this.refreshInterval = refreshInterval;
    this.expiry = expiry;
    this.clock = clock;
  }

  private static MongoClient buildClient(SyncSourceConfig syncSource, MeterRegistry meterRegistry) {
    return MongoClientBuilder.buildNonReplicationWithDefaults(syncSource.mongodClusterReadWriteUri,
        MONGO_CLIENT_NAME, meterRegistry);
  }

  public void start() {
    LOG.atInfo()
        .addKeyValue("refreshInterval", this.refreshInterval)
        .addKeyValue("expiry", this.expiry)
        .log("Starting mongod topology monitor schedule.");
    this.scheduler.scheduleWithFixedDelay(
        this::refreshCache,
        0L,
        this.refreshInterval.toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Returns the cached topology if it is present and not expired. Otherwise, kicks off an
   * asynchronous probe to populate the cache and throws {@link CheckedMongoException}.
   */
  public boolean isShardedCluster() throws CheckedMongoException {
    CacheEntry entry = this.cache.get();
    if (entry != null && isFresh(entry)) {
      return entry.sharded();
    }
    if (entry == null) {
      // Trigger async probe to initialize the cache to prevent index management commands failures
      // up to the REFRESH_INTERVAL period when mongod is started after mongot.
      triggerAsyncProbe();
    }
    throw new CheckedMongoException(new MongoException("Topology cache is unavailable."));
  }

  private void triggerAsyncProbe() {
    // Using probeInFlight to prevent submitting duplicated tasks.
    if (this.closed || !this.probeInFlight.compareAndSet(false, true)) {
      return;
    }
    try {
      this.scheduler.submit(() -> {
        try {
          if (this.cache.get() != null) {
            // cache is populated by the periodic task.
            return;
          }
          refreshCache();
        } finally {
          this.probeInFlight.set(false);
        }
      });
    } catch (RejectedExecutionException e) {
      this.probeInFlight.set(false);
      LOG.atWarn().setCause(e).log("Failed to schedule async topology probe.");
    }
  }

  @VisibleForTesting
  void refreshCache() {
    if (this.closed) {
      return;
    }
    try {
      boolean sharded = probeShardedCluster();
      updateCache(sharded);
    } catch (Throwable e) {
      LOG.atError()
          .setCause(e)
          .log("Periodic topology refresh failed.");
    }
  }

  /**
   * Issues {@code getCmdLineOpts} on the dedicated mongod client and parses
   * {@code parsed.sharding}. Any node started with a {@code clusterRole} (i.e. {@code --shardsvr}
   * or {@code --configsvr}) has {@code parsed.sharding} populated; a plain replica-set member
   * does not.
   */
  private boolean probeShardedCluster() throws CheckedMongoException {
    try {
      BsonDocument result =
          this.topologyClient
              .getDatabase("admin")
              .runCommand(new BsonDocument("getCmdLineOpts", new BsonInt32(1)), BsonDocument.class);
      return result.getDocument("parsed", new BsonDocument()).containsKey("sharding");
    } catch (MongoException e) {
      throw new CheckedMongoException(e);
    }
  }

  private void updateCache(boolean sharded) {
    CacheEntry previous = this.cache.getAndSet(new CacheEntry(sharded, this.clock.instant()));
    if (previous == null) {
      LOG.info("Mongod topology cache is initialized to: sharded = {}", sharded);
    } else if (previous.sharded() != sharded) {
      LOG.warn(
          "Mongod topology change detected: was {}, now {}",
          previous.sharded() ? "sharded" : "replica set",
          sharded ? "sharded" : "replica set");
    }
  }

  private boolean isFresh(CacheEntry entry) {
    return Duration.between(entry.refreshedAt(), this.clock.instant()).compareTo(this.expiry) <= 0;
  }

  @VisibleForTesting
  Optional<CacheEntry> cachedEntry() {
    return Optional.ofNullable(this.cache.get());
  }

  @Override
  public void close() {
    if (this.closed) {
      return;
    }
    this.closed = true;
    Executors.shutdownOrFail(this.scheduler);
    this.topologyClient.close();
  }
}
