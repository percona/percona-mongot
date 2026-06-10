package com.xgen.mongot.config.provider.community;

import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Optional;
import org.bson.BsonDocument;

/**
 * The {@code replication} block of the Community mongot config file.
 */
public record CommunityReplicationConfig(Optional<MongoDbConfig> mongoDbConfig)
    implements DocumentEncodable {

  static class Fields {
    public static final Field.Optional<MongoDbConfig> MONGODB_CONFIG =
        Field.builder("mongodb")
            .classField(MongoDbConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();
  }

  public static CommunityReplicationConfig fromBson(DocumentParser parser)
      throws BsonParseException {
    return new CommunityReplicationConfig(parser.getField(Fields.MONGODB_CONFIG).unwrap());
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder().field(Fields.MONGODB_CONFIG, this.mongoDbConfig).build();
  }

  public record MongoDbConfig(
      Optional<Integer> numConcurrentInitialSyncs,
      Optional<Integer> numConcurrentChangeStreams,
      Optional<Integer> numIndexingThreads,
      Optional<Integer> numConcurrentSynonymSyncs)
      implements DocumentEncodable {

    static class Fields {
      public static final Field.Optional<Integer> NUM_CONCURRENT_INITIAL_SYNCS =
          Field.builder("numConcurrentInitialSyncs")
              .intField()
              .mustBePositive()
              .optional()
              .noDefault();

      public static final Field.Optional<Integer> NUM_CONCURRENT_CHANGE_STREAMS =
          Field.builder("numConcurrentChangeStreams")
              .intField()
              .mustBePositive()
              .optional()
              .noDefault();

      public static final Field.Optional<Integer> NUM_INDEXING_THREADS =
          Field.builder("numIndexingThreads").intField().mustBePositive().optional().noDefault();

      public static final Field.Optional<Integer> NUM_CONCURRENT_SYNONYM_SYNCS =
          Field.builder("numConcurrentSynonymSyncs")
              .intField()
              .mustBePositive()
              .optional()
              .noDefault();
    }

    public static MongoDbConfig fromBson(DocumentParser parser) throws BsonParseException {
      return new MongoDbConfig(
          parser.getField(Fields.NUM_CONCURRENT_INITIAL_SYNCS).unwrap(),
          parser.getField(Fields.NUM_CONCURRENT_CHANGE_STREAMS).unwrap(),
          parser.getField(Fields.NUM_INDEXING_THREADS).unwrap(),
          parser.getField(Fields.NUM_CONCURRENT_SYNONYM_SYNCS).unwrap());
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.NUM_CONCURRENT_INITIAL_SYNCS, this.numConcurrentInitialSyncs)
          .field(Fields.NUM_CONCURRENT_CHANGE_STREAMS, this.numConcurrentChangeStreams)
          .field(Fields.NUM_INDEXING_THREADS, this.numIndexingThreads)
          .field(Fields.NUM_CONCURRENT_SYNONYM_SYNCS, this.numConcurrentSynonymSyncs)
          .build();
    }
  }
}
