package com.xgen.mongot.config.provider.community;

import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Optional;
import org.bson.BsonDocument;

/**
 * The {@code querying} block of the Community mongot config file.
 */
public record CommunityQueryingConfig(Optional<LuceneConfig> luceneConfig)
    implements DocumentEncodable {

  static class Fields {
    public static final Field.Optional<LuceneConfig> LUCENE_CONFIG =
        Field.builder("lucene")
            .classField(LuceneConfig::fromBson)
            .disallowUnknownFields()
            .optional()
            .noDefault();
  }

  public static CommunityQueryingConfig fromBson(DocumentParser parser) throws BsonParseException {
    return new CommunityQueryingConfig(parser.getField(Fields.LUCENE_CONFIG).unwrap());
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder().field(Fields.LUCENE_CONFIG, this.luceneConfig).build();
  }

  public record LuceneConfig(Optional<Integer> maxClauseLimit)
      implements DocumentEncodable {

    static class Fields {
      public static final Field.Optional<Integer> MAX_CLAUSE_LIMIT =
          Field.builder("maxClauseLimit").intField().mustBePositive().optional().noDefault();
    }

    public static LuceneConfig fromBson(DocumentParser parser) throws BsonParseException {
      return new LuceneConfig(parser.getField(Fields.MAX_CLAUSE_LIMIT).unwrap());
    }

    @Override
    public BsonDocument toBson() {
      return BsonDocumentBuilder.builder()
          .field(Fields.MAX_CLAUSE_LIMIT, this.maxClauseLimit)
          .build();
    }
  }
}
