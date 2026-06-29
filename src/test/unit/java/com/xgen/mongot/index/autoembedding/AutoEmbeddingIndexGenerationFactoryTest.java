package com.xgen.mongot.index.autoembedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xgen.mongot.embedding.config.MaterializedViewCollectionMetadata.MaterializedViewSchemaMetadata;
import com.xgen.mongot.index.definition.AnalyzerBoundSearchIndexDefinition;
import com.xgen.mongot.index.definition.IndexDefinitionGeneration;
import com.xgen.mongot.index.definition.SearchAutoEmbedFieldDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinitionGeneration;
import com.xgen.mongot.index.definition.VectorIndexDefinition;
import com.xgen.mongot.index.definition.VectorIndexDefinitionGeneration;
import com.xgen.mongot.index.version.Generation;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.VectorIndexDefinitionBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.Test;

/**
 * Tests for {@link AutoEmbeddingIndexGenerationFactory#derivedIndexDefinitionGeneration}, covering
 * both vector and search auto-embed source definitions.
 */
public class AutoEmbeddingIndexGenerationFactoryTest {

  private static final String MV_DATABASE_NAME = "__mdb_internal_search";
  private static final UUID MV_COLLECTION_UUID = UUID.randomUUID();

  @Test
  public void derivedIndexDefinitionGeneration_vectorIndex_buildsVectorDerivedDefinition() {
    ObjectId indexId = new ObjectId();
    VectorIndexDefinition vectorDef =
        VectorIndexDefinitionBuilder.builder()
            .indexId(indexId)
            .name("mock")
            .database("mock")
            .lastObservedCollectionName("mock")
            .collectionUuid(UUID.randomUUID())
            .withAutoEmbedField("title")
            .build();
    VectorIndexDefinitionGeneration rawGen =
        new VectorIndexDefinitionGeneration(vectorDef, Generation.CURRENT);
    InitializedMaterializedViewIndex matViewIndex = matViewIndexMock();

    IndexDefinitionGeneration derived =
        AutoEmbeddingIndexGenerationFactory.derivedIndexDefinitionGeneration(rawGen, matViewIndex);

    assertNotNull(derived);
    assertTrue(
        "Vector source must derive a vector index definition generation",
        derived instanceof VectorIndexDefinitionGeneration);
    assertEquals(MV_DATABASE_NAME, derived.getIndexDefinition().getDatabase());
    assertEquals(MV_COLLECTION_UUID, derived.getIndexDefinition().getCollectionUuid());
  }

  @Test
  public void derivedIndexDefinitionGeneration_searchIndex_buildsSearchDerivedDefinition() {
    ObjectId indexId = new ObjectId();
    SearchAutoEmbedFieldDefinition autoEmbedField =
        new SearchAutoEmbedFieldDefinition("voyage-3-large", FieldPath.parse("content"));
    SearchIndexDefinition searchDef =
        SearchIndexDefinitionBuilder.builder()
            .defaultMetadata()
            .indexId(indexId)
            .name("mock")
            .database("mock")
            .lastObservedCollectionName("mock")
            .collectionUuid(UUID.randomUUID())
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(false)
                    .field(
                        "content_embed",
                        FieldDefinitionBuilder.builder().searchAutoEmbed(autoEmbedField).build())
                    .build())
            .build();
    SearchIndexDefinitionGeneration rawGen =
        new SearchIndexDefinitionGeneration(
            AnalyzerBoundSearchIndexDefinition.create(searchDef, Collections.emptyList()),
            Generation.CURRENT);
    InitializedMaterializedViewIndex matViewIndex = matViewIndexMock();

    IndexDefinitionGeneration derived =
        AutoEmbeddingIndexGenerationFactory.derivedIndexDefinitionGeneration(rawGen, matViewIndex);

    assertNotNull(derived);
    assertTrue(
        "Search source must derive a search index definition generation",
        derived instanceof SearchIndexDefinitionGeneration);
    assertEquals(MV_DATABASE_NAME, derived.getIndexDefinition().getDatabase());
    assertEquals(MV_COLLECTION_UUID, derived.getIndexDefinition().getCollectionUuid());
  }

  private static InitializedMaterializedViewIndex matViewIndexMock() {
    InitializedMaterializedViewIndex matViewIndex = mock(InitializedMaterializedViewIndex.class);
    when(matViewIndex.getMaterializedViewDatabaseName()).thenReturn(MV_DATABASE_NAME);
    when(matViewIndex.getMaterializedViewCollectionUuid()).thenReturn(MV_COLLECTION_UUID);
    when(matViewIndex.getSchemaMetadata())
        .thenReturn(new MaterializedViewSchemaMetadata(0, Map.of()));
    return matViewIndex;
  }
}
