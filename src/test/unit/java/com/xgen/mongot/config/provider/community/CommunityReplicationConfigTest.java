package com.xgen.mongot.config.provider.community;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.PermissiveBsonParseContext;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.Test;

public class CommunityReplicationConfigTest {

  private static CommunityReplicationConfig parse(BsonDocument doc) throws BsonParseException {
    PermissiveBsonParseContext context = PermissiveBsonParseContext.root();
    try (var parser = BsonDocumentParser.withContext(context, doc).build()) {
      return CommunityReplicationConfig.fromBson(parser);
    }
  }

  @Test
  public void roundTrip_full_preservesAllFields() throws BsonParseException {
    CommunityReplicationConfig original =
        new CommunityReplicationConfig(
            Optional.of(
                new CommunityReplicationConfig.MongoDbConfig(
                    Optional.of(3), Optional.of(12), Optional.of(8), Optional.of(2))));
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void roundTrip_empty_preservesEmptyBlock() throws BsonParseException {
    CommunityReplicationConfig original = new CommunityReplicationConfig(Optional.empty());
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void parse_exposesAllFields() throws BsonParseException {
    BsonDocument mongodb =
        new BsonDocument()
            .append("numConcurrentInitialSyncs", new BsonInt32(3))
            .append("numConcurrentChangeStreams", new BsonInt32(12))
            .append("numIndexingThreads", new BsonInt32(8))
            .append("numConcurrentSynonymSyncs", new BsonInt32(2));
    var mongo = parse(new BsonDocument("mongodb", mongodb)).mongoDbConfig().get();
    assertEquals(Optional.of(3), mongo.numConcurrentInitialSyncs());
    assertEquals(Optional.of(12), mongo.numConcurrentChangeStreams());
    assertEquals(Optional.of(8), mongo.numIndexingThreads());
    assertEquals(Optional.of(2), mongo.numConcurrentSynonymSyncs());
  }

  @Test
  public void parse_negativeNumIndexingThreads_throws() {
    BsonDocument doc =
        new BsonDocument("mongodb", new BsonDocument("numIndexingThreads", new BsonInt32(-1)));
    assertThrows(BsonParseException.class, () -> parse(doc));
  }

  @Test
  public void parse_unknownFieldInMongodbBlock_isIgnored() throws BsonParseException {
    BsonDocument mongodb =
        new BsonDocument("numIndexingThreads", new BsonInt32(8))
            .append("notARealField", new BsonInt32(1));
    CommunityReplicationConfig parsed = parse(new BsonDocument("mongodb", mongodb));
    assertEquals(Optional.of(8), parsed.mongoDbConfig().get().numIndexingThreads());
  }
}
