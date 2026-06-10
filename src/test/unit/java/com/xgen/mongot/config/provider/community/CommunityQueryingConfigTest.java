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

public class CommunityQueryingConfigTest {

  private static CommunityQueryingConfig parse(BsonDocument doc) throws BsonParseException {
    PermissiveBsonParseContext context = PermissiveBsonParseContext.root();
    try (var parser = BsonDocumentParser.withContext(context, doc).build()) {
      return CommunityQueryingConfig.fromBson(parser);
    }
  }

  @Test
  public void roundTrip_full_preservesValue() throws BsonParseException {
    CommunityQueryingConfig original =
        new CommunityQueryingConfig(
            Optional.of(new CommunityQueryingConfig.LuceneConfig(Optional.of(2048))));
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void roundTrip_empty_preservesEmptyBlock() throws BsonParseException {
    CommunityQueryingConfig original = new CommunityQueryingConfig(Optional.empty());
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void parse_exposesMaxClauseLimit() throws BsonParseException {
    BsonDocument doc =
        new BsonDocument("lucene", new BsonDocument("maxClauseLimit", new BsonInt32(2048)));
    assertEquals(Optional.of(2048), parse(doc).luceneConfig().get().maxClauseLimit());
  }

  @Test
  public void parse_negativeMaxClauseLimit_throws() {
    BsonDocument doc =
        new BsonDocument("lucene", new BsonDocument("maxClauseLimit", new BsonInt32(-1)));
    assertThrows(BsonParseException.class, () -> parse(doc));
  }

  @Test
  public void parse_unknownFieldInLuceneBlock_isIgnored() throws BsonParseException {
    BsonDocument lucene =
        new BsonDocument("maxClauseLimit", new BsonInt32(1024))
            .append("notARealField", new BsonInt32(1));
    CommunityQueryingConfig parsed = parse(new BsonDocument("lucene", lucene));
    assertEquals(Optional.of(1024), parsed.luceneConfig().get().maxClauseLimit());
  }
}
