package com.xgen.mongot.catalogservice;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.client.MongoClient;
import com.xgen.mongot.util.mongodb.MongoClientBuilder;
import com.xgen.mongot.util.mongodb.SyncSourceConfig;
import io.micrometer.core.instrument.MeterRegistry;

public class DefaultMetadataService implements MetadataService {

  private static final String MONGO_CLIENT_DESCRIPTION = "mongot metadata service";

  private final MongoClient mongoClient;
  private final AuthoritativeIndexCatalog authoritativeIndexCatalog;
  private final IndexStats indexStats;
  private final ServerState serverState;

  @VisibleForTesting
  public DefaultMetadataService(
      MongoClient mongoClient,
      AuthoritativeIndexCatalog authoritativeIndexCatalog,
      IndexStats indexStats,
      ServerState serverState) {
    this.mongoClient = mongoClient;
    this.authoritativeIndexCatalog = authoritativeIndexCatalog;
    this.indexStats = indexStats;
    this.serverState = serverState;
  }

  @Override
  public AuthoritativeIndexCatalog getAuthoritativeIndexCatalog() {
    return this.authoritativeIndexCatalog;
  }

  @Override
  public IndexStats getIndexStats() {
    return this.indexStats;
  }

  @Override
  public ServerState getServerState() {
    return this.serverState;
  }

  @Override
  public void close() {
    this.mongoClient.close();
  }

  @VisibleForTesting
  public static MetadataService createForTesting(MongoClient client, String databaseName) {
    return new DefaultMetadataService(
        client,
        DefaultAuthoritativeIndexCatalog.createForTesting(client, databaseName),
        DefaultIndexStats.createForTesting(client, databaseName),
        DefaultServerState.createForTesting(client, databaseName));
  }

  public static MetadataService create(
      SyncSourceConfig syncSourceConfig, MeterRegistry meterRegistry) {

    MongoClient client =
        MongoClientBuilder.buildClusterReadWriteClient(
            syncSourceConfig, MONGO_CLIENT_DESCRIPTION, meterRegistry);

    return new DefaultMetadataService(
        client,
        DefaultAuthoritativeIndexCatalog.create(client),
        DefaultIndexStats.create(client),
        DefaultServerState.create(client));
  }
}
