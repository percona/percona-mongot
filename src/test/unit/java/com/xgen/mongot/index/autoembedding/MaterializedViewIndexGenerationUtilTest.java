package com.xgen.mongot.index.autoembedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.index.definition.SearchAutoEmbedFieldDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.definition.VectorAutoEmbedFieldDefinition;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import com.xgen.mongot.index.definition.VectorIndexDefinition;
import com.xgen.mongot.index.definition.VectorIndexFieldDefinition;
import com.xgen.mongot.index.definition.VectorIndexFilterFieldDefinition;
import com.xgen.mongot.index.definition.VectorIndexingAlgorithm;
import com.xgen.mongot.index.definition.VectorSimilarity;
import com.xgen.mongot.index.definition.quantization.VectorAutoEmbedQuantization;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.VectorIndexDefinitionBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.Test;

/** Unit tests for {@link MaterializedViewIndexGenerationUtil#skipInitialSync}. */
public class MaterializedViewIndexGenerationUtilTest {

  private static final ObjectId INDEX_ID = new ObjectId();

  @Test
  public void skipInitialSync_sameDefinitionDifferentOrder_returnsTrue() {
    VectorIndexFieldDefinition autoEmbedFieldDefinition =
        new VectorAutoEmbedFieldDefinition(
            "voyage-3-large",
            "text",
            FieldPath.parse("desc"),
            512,
            VectorSimilarity.DOT_PRODUCT,
            VectorAutoEmbedQuantization.FLOAT);
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    autoEmbedFieldDefinition,
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category")),
                    autoEmbedFieldDefinition))
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_onlyIncreaseDefinitionVersion_returnsFalse() {
    List<VectorIndexFieldDefinition> fields =
        List.of(
            new VectorAutoEmbedFieldDefinition(
                "voyage-3-large",
                "text",
                FieldPath.parse("desc"),
                512,
                VectorSimilarity.DOT_PRODUCT,
                VectorAutoEmbedQuantization.FLOAT),
            new VectorIndexFilterFieldDefinition(FieldPath.parse("category")));
    VectorIndexDefinition defVersion1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(fields)
            .build();
    VectorIndexDefinition defVersion2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(fields)
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(defVersion1, defVersion2));
  }

  @Test
  public void skipInitialSync_redefineHnswOptions_returnsTrue() {
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT,
                        new VectorIndexingAlgorithm.HnswIndexingAlgorithm(
                            new VectorFieldSpecification.HnswOptions(32, 200))),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_onlyIndexingMethodDiffer_returnsTrue() {
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT,
                        new VectorIndexingAlgorithm.FlatIndexingAlgorithm()),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_onlySimilarityDiffer_returnsTrue() {
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.COSINE,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_redefineQuantization_returnsFalse() {
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.SCALAR),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_redefinePath_returnsFalse() {
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("title"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_redefineFilterField_returnsFalse() {
    VectorIndexFieldDefinition autoEmbedFieldDefinition =
        new VectorAutoEmbedFieldDefinition(
            "voyage-3-large",
            "text",
            FieldPath.parse("desc"),
            512,
            VectorSimilarity.DOT_PRODUCT,
            VectorAutoEmbedQuantization.FLOAT);
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    autoEmbedFieldDefinition,
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    autoEmbedFieldDefinition,
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("genre"))))
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_onlyIncreaseNumPartitions_returnsTrue() {
    List<VectorIndexFieldDefinition> fields =
        List.of(
            new VectorAutoEmbedFieldDefinition(
                "voyage-3-large",
                "text",
                FieldPath.parse("desc"),
                512,
                VectorSimilarity.DOT_PRODUCT,
                VectorAutoEmbedQuantization.FLOAT),
            new VectorIndexFilterFieldDefinition(FieldPath.parse("category")));
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .numPartitions(2)
            .setFields(fields)
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .numPartitions(3)
            .setFields(fields)
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_firstAutoEmbedLuceneOnlySecondQuantization_returnsFalse() {
    VectorIndexFieldDefinition filter =
        new VectorIndexFilterFieldDefinition(FieldPath.parse("category"));
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("title"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    filter))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT,
                        new VectorIndexingAlgorithm.HnswIndexingAlgorithm(
                            new VectorFieldSpecification.HnswOptions(32, 200))),
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("title"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.SCALAR),
                    filter))
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_onlyIncreaseIndexFeatureVersion_returnsTrue() {
    List<VectorIndexFieldDefinition> fields =
        List.of(
            new VectorAutoEmbedFieldDefinition(
                "voyage-3-large",
                "text",
                FieldPath.parse("desc"),
                512,
                VectorSimilarity.DOT_PRODUCT,
                VectorAutoEmbedQuantization.FLOAT),
            new VectorIndexFilterFieldDefinition(FieldPath.parse("category")));
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .indexFeatureVersion(2)
            .setFields(fields)
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .indexFeatureVersion(3)
            .setFields(fields)
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  @Test
  public void skipInitialSync_redefineNumDimensions_returnsFalse() {
    // Using different models with different dimensions (voyage-3-large=512, voyage-3-small=256)
    VectorIndexDefinition def1 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    VectorIndexDefinition def2 =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(2L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-small",
                        "text",
                        FieldPath.parse("desc"),
                        256,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT),
                    new VectorIndexFilterFieldDefinition(FieldPath.parse("category"))))
            .build();
    // Different numDimensions (via different model) should require resync
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def2, def1));
  }

  // ---- Search index ----

  private static final SearchAutoEmbedFieldDefinition SEARCH_AUTO_EMBED_FIELD =
      new SearchAutoEmbedFieldDefinition("voyage-3-large", FieldPath.parse("content"));

  private static SearchIndexDefinitionBuilder baseSearchBuilder(long defVersion) {
    return SearchIndexDefinitionBuilder.builder()
        .defaultMetadata()
        .indexId(INDEX_ID)
        .name("mock")
        .database("mock")
        .lastObservedCollectionName("mock")
        .collectionUuid(UUID.randomUUID())
        .definitionVersion(defVersion);
  }

  private static SearchIndexDefinition searchDefWithAutoEmbed(long defVersion, boolean dynamic) {
    return baseSearchBuilder(defVersion)
        .mappings(
            DocumentFieldDefinitionBuilder.builder()
                .dynamic(dynamic)
                .field(
                    "content_embed",
                    FieldDefinitionBuilder.builder()
                        .searchAutoEmbed(SEARCH_AUTO_EMBED_FIELD)
                        .build())
                .build())
        .build();
  }

  @Test
  public void skipInitialSync_search_sameDefinition_returnsTrue() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 = searchDefWithAutoEmbed(1L, false);
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_autoEmbedModelChanged_returnsFalse() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 =
        baseSearchBuilder(2L)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder()
                            .searchAutoEmbed(
                                new SearchAutoEmbedFieldDefinition(
                                    "voyage-3-small", FieldPath.parse("content")))
                            .build())
                    .build())
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_nonAutoEmbedFieldsChanged_returnsFalse() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 =
        baseSearchBuilder(2L)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder()
                            .searchAutoEmbed(SEARCH_AUTO_EMBED_FIELD)
                            .build())
                    .field("title", FieldDefinitionBuilder.builder().build())
                    .build())
            .build();
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_dynamicFlipped_returnsFalse() {
    // Regression: dynamic flip with no other change must trigger resync because the MV was
    // built with the old dynamic mode and won't carry the fields the new mode expects.
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 = searchDefWithAutoEmbed(2L, true);
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_numPartitionsOnly_returnsTrue() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 =
        baseSearchBuilder(2L)
            .numPartitions(4)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder()
                            .searchAutoEmbed(SEARCH_AUTO_EMBED_FIELD)
                            .build())
                    .build())
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_indexFeatureVersionOnly_returnsTrue() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 =
        baseSearchBuilder(2L)
            .indexFeatureVersion(2)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder()
                            .searchAutoEmbed(SEARCH_AUTO_EMBED_FIELD)
                            .build())
                    .build())
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_analyzerOnly_returnsTrue() {
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 =
        baseSearchBuilder(2L)
            .analyzerName("lucene.standard")
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder()
                            .searchAutoEmbed(SEARCH_AUTO_EMBED_FIELD)
                            .build())
                    .build())
            .build();
    assertTrue(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test
  public void skipInitialSync_search_versionOnlyDiffers_returnsFalse() {
    // Same shape, only definitionVersion changes: no schema-affecting change AND no
    // Lucene-only change detected → fall through to step 5 → resync.
    SearchIndexDefinition def1 = searchDefWithAutoEmbed(1L, false);
    SearchIndexDefinition def2 = searchDefWithAutoEmbed(2L, false);
    assertFalse(MaterializedViewIndexGenerationUtil.skipInitialSync(def1, def2));
  }

  @Test(expected = IllegalArgumentException.class)
  public void skipInitialSync_mixedTypes_throws() {
    SearchIndexDefinition searchDef = searchDefWithAutoEmbed(1L, false);
    VectorIndexDefinition vectorDef =
        VectorIndexDefinitionBuilder.builder()
            .indexId(INDEX_ID)
            .withDefinitionVersion(Optional.of(1L))
            .setFields(
                List.of(
                    new VectorAutoEmbedFieldDefinition(
                        "voyage-3-large",
                        "text",
                        FieldPath.parse("desc"),
                        512,
                        VectorSimilarity.DOT_PRODUCT,
                        VectorAutoEmbedQuantization.FLOAT)))
            .build();
    MaterializedViewIndexGenerationUtil.skipInitialSync(searchDef, vectorDef);
  }
}
