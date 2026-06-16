package com.xgen.mongot.config.provider.community;

import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Optional;
import org.bson.BsonDocument;

/**
 * The {@code advancedConfigs} block of the Community mongot config file. Groups the optional
 * tuning blocks under a single top-level field.
 */
public record AdvancedConfigs(
    Optional<CommunityIndexingConfig> indexingConfig,
    Optional<CommunityQueryingConfig> queryingConfig,
    Optional<CommunityReplicationConfig> replicationConfig,
    Optional<CommunityAutoEmbeddingConfig> autoEmbeddingConfig,
    Optional<CommunityCursorConfig> cursorConfig)
    implements DocumentEncodable {

  static class Fields {
    public static final Field.Optional<CommunityIndexingConfig> INDEXING =
        Field.builder("indexing")
            .classField(CommunityIndexingConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<CommunityQueryingConfig> QUERYING =
        Field.builder("querying")
            .classField(CommunityQueryingConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<CommunityReplicationConfig> REPLICATION =
        Field.builder("replication")
            .classField(CommunityReplicationConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<CommunityAutoEmbeddingConfig> AUTO_EMBEDDING =
        Field.builder("autoEmbedding")
            .classField(CommunityAutoEmbeddingConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();

    public static final Field.Optional<CommunityCursorConfig> CURSOR =
        Field.builder("cursor")
            .classField(CommunityCursorConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();
  }

  public static AdvancedConfigs fromBson(DocumentParser parser) throws BsonParseException {
    return new AdvancedConfigs(
        parser.getField(Fields.INDEXING).unwrap(),
        parser.getField(Fields.QUERYING).unwrap(),
        parser.getField(Fields.REPLICATION).unwrap(),
        parser.getField(Fields.AUTO_EMBEDDING).unwrap(),
        parser.getField(Fields.CURSOR).unwrap());
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.INDEXING, this.indexingConfig)
        .field(Fields.QUERYING, this.queryingConfig)
        .field(Fields.REPLICATION, this.replicationConfig)
        .field(Fields.AUTO_EMBEDDING, this.autoEmbeddingConfig)
        .field(Fields.CURSOR, this.cursorConfig)
        .build();
  }
}
