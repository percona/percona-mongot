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

public class CommunityIndexingConfigTest {

  private static CommunityIndexingConfig fullConfig() {
    var vector =
        new CommunityIndexingConfig.LuceneConfig.MergePolicyConfig.TieredMergePolicyConfig
            .VectorMergePolicyConfig(Optional.of(512));
    var tiered =
        new CommunityIndexingConfig.LuceneConfig.MergePolicyConfig.TieredMergePolicyConfig(
            Optional.of(vector));
    var mergePolicy =
        new CommunityIndexingConfig.LuceneConfig.MergePolicyConfig(Optional.of(tiered));
    var concurrentScheduler =
        new CommunityIndexingConfig.LuceneConfig.MergeSchedulerConfig
            .ConcurrentMergeSchedulerConfig(Optional.of(6));
    var mergeScheduler =
        new CommunityIndexingConfig.LuceneConfig.MergeSchedulerConfig(
            Optional.of(concurrentScheduler));
    var refresh = new CommunityIndexingConfig.LuceneConfig.RefreshConfig(Optional.of(2000));
    var lucene =
        new CommunityIndexingConfig.LuceneConfig(
            Optional.of(refresh),
            Optional.of(mergePolicy),
            Optional.of(mergeScheduler),
            Optional.of(500));
    var definition = new CommunityIndexingConfig.IndexDefinitionConfig(Optional.of(7));
    return new CommunityIndexingConfig(Optional.of(lucene), Optional.of(definition));
  }

  private static CommunityIndexingConfig parse(BsonDocument doc) throws BsonParseException {
    PermissiveBsonParseContext context = PermissiveBsonParseContext.root();
    try (var parser = BsonDocumentParser.withContext(context, doc).build()) {
      return CommunityIndexingConfig.fromBson(parser);
    }
  }

  @Test
  public void roundTrip_full_preservesAllFields() throws BsonParseException {
    CommunityIndexingConfig original = fullConfig();
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void roundTrip_empty_preservesEmptyBlocks() throws BsonParseException {
    CommunityIndexingConfig original =
        new CommunityIndexingConfig(Optional.empty(), Optional.empty());
    assertEquals(original, parse(original.toBson()));
  }

  @Test
  public void parse_full_exposesExpectedValues() throws BsonParseException {
    CommunityIndexingConfig parsed = parse(fullConfig().toBson());

    var lucene = parsed.luceneConfig().get();
    assertEquals(Optional.of(2000), lucene.refreshConfig().get().intervalMs());
    assertEquals(Optional.of(500), lucene.fieldLimit());
    var tiered = lucene.mergePolicyConfig().get().tieredMergePolicyConfig().get();
    assertEquals(Optional.of(512), tiered.vectorMergePolicyConfig().get().mergeBudgetMb());
    assertEquals(
        Optional.of(6),
        lucene.mergeSchedulerConfig().get().concurrentSchedulerConfig().get().maxThreadCount());
    assertEquals(
        Optional.of(7), parsed.definitionConfig().get().maxEmbeddedDocumentsNestingLevel());
  }

  @Test
  public void parse_negativeFieldLimit_throws() {
    BsonDocument doc =
        new BsonDocument("lucene", new BsonDocument("fieldLimit", new BsonInt32(-1)));
    assertThrows(BsonParseException.class, () -> parse(doc));
  }

  @Test
  public void parse_unknownFieldInLuceneBlock_isIgnored() throws BsonParseException {
    BsonDocument lucene =
        new BsonDocument("fieldLimit", new BsonInt32(500))
            .append("notARealField", new BsonInt32(1));
    CommunityIndexingConfig parsed = parse(new BsonDocument("lucene", lucene));
    assertEquals(Optional.of(500), parsed.luceneConfig().get().fieldLimit());
  }
}
