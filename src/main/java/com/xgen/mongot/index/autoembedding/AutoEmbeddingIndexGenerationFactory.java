package com.xgen.mongot.index.autoembedding;

import static com.xgen.mongot.embedding.utils.AutoEmbeddingIndexDefinitionUtils.getDerivedVectorIndexDefinition;
import static com.xgen.mongot.index.definition.MaterializedViewIndexDefinitionGeneration.MIN_VERSION_FOR_MATERIALIZED_VIEW_EMBEDDING;

import com.xgen.mongot.catalog.InitializedIndexCatalog;
import com.xgen.mongot.embedding.exceptions.MaterializedViewTransientException;
import com.xgen.mongot.index.Index;
import com.xgen.mongot.index.IndexFactory;
import com.xgen.mongot.index.IndexGeneration;
import com.xgen.mongot.index.InitializedVectorIndex;
import com.xgen.mongot.index.VectorIndex;
import com.xgen.mongot.index.analyzer.InvalidAnalyzerDefinitionException;
import com.xgen.mongot.index.definition.MaterializedViewIndexDefinitionGeneration;
import com.xgen.mongot.index.definition.VectorIndexDefinitionGeneration;
import com.xgen.mongot.index.version.MaterializedViewGeneration;
import com.xgen.mongot.util.Check;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory that generates composite AutoEmbedding index generation, used by ConfigManager to create
 * new IndexGeneration for any index lifecycle events (staged/live/recovered). Composite
 * IndexGeneration is used for easier index state management and status consolidation between
 * Materialized View and Lucene. For example, steady state Lucene on generationID:123_f1_u1_a1 could
 * be un-queryable if corresponding Materialized View status is in initial sync.
 */
public class AutoEmbeddingIndexGenerationFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(AutoEmbeddingIndexGenerationFactory.class);

  /**
   * Returns a new AutoEmbeddingIndexGeneration or an UnresolvedAutoEmbeddingIndexGeneration if
   * syncSource is not available.
   */
  public static IndexGeneration getAutoEmbeddingIndexGeneration(
      IndexFactory indexFactory,
      MaterializedViewIndexFactory matViewIndexFactory,
      VectorIndexDefinitionGeneration rawDefinitionGeneration,
      InitializedIndexCatalog initializedIndexCatalog)
      throws IOException, InvalidAnalyzerDefinitionException {
    Check.checkArg(
        rawDefinitionGeneration.getIndexDefinition().isAutoEmbeddingIndex()
            && rawDefinitionGeneration.getIndexDefinition().getParsedAutoEmbeddingFeatureVersion()
                >= MIN_VERSION_FOR_MATERIALIZED_VIEW_EMBEDDING,
        "Input definition is not materialized view based vector index");
    InitializedMaterializedViewIndex matViewIndex;
    try {
      matViewIndex =
          matViewIndexFactory.getIndex(
              createMaterializedViewIndexDefinitionGeneration(rawDefinitionGeneration));
    } catch (MaterializedViewTransientException e) {
      LOG.atError()
          .setCause(e)
          .addKeyValue("generationId", rawDefinitionGeneration.getGenerationId())
          .log(
              "Failed to create materialized view index, "
                  + "creates unresolved index instead, will retry it when sync source is updated.");
      return UnresolvedAutoEmbeddingIndexGeneration.create(rawDefinitionGeneration, e.getReason());
    }

    VectorIndexDefinitionGeneration derivedIndexDefinitionGeneration =
        derivedIndexDefinitionGeneration(rawDefinitionGeneration, matViewIndex);
    Index vectorIndex = indexFactory.getIndex(derivedIndexDefinitionGeneration);
    // Supplier that looks up initialized index from catalog
    Supplier<Optional<InitializedVectorIndex>> initializedIndexSupplier =
        () ->
            initializedIndexCatalog
                .getIndex(derivedIndexDefinitionGeneration.getGenerationId())
                .filter(idx -> idx instanceof InitializedVectorIndex)
                .map(idx -> (InitializedVectorIndex) idx);
    LOG.atInfo()
        .addKeyValue("generationId", rawDefinitionGeneration.getGenerationId())
        .log("Created auto-embedding composite index generation");
    return new AutoEmbeddingIndexGeneration(
        new AutoEmbeddingCompositeIndex(
            matViewIndex,
            Check.instanceOf(vectorIndex, VectorIndex.class),
            initializedIndexSupplier),
        rawDefinitionGeneration,
        derivedIndexDefinitionGeneration);
  }

  /**
   * Helper function to check whether IndexGeneration resolution fails when generating derived
   * definition for auto embedding index, only returns true when auto-embedding index is unresolved.
   */
  public static boolean isAutoEmbeddingResolutionFailed(IndexGeneration indexGeneration) {
    return indexGeneration instanceof UnresolvedAutoEmbeddingIndexGeneration
        || indexGeneration.getIndex() instanceof UnresolvedAutoEmbeddingIndex;
  }

  static MaterializedViewIndexDefinitionGeneration createMaterializedViewIndexDefinitionGeneration(
      VectorIndexDefinitionGeneration rawDefinitionGeneration) {
    return new MaterializedViewIndexDefinitionGeneration(
        rawDefinitionGeneration.getIndexDefinition(),
        new MaterializedViewGeneration(rawDefinitionGeneration.generation()));
  }

  private static VectorIndexDefinitionGeneration derivedIndexDefinitionGeneration(
      VectorIndexDefinitionGeneration rawDefinitionGeneration,
      InitializedMaterializedViewIndex matViewIndex) {
    return new VectorIndexDefinitionGeneration(
        getDerivedVectorIndexDefinition(
            rawDefinitionGeneration.getIndexDefinition(),
            matViewIndex.getMaterializedViewDatabaseName(),
            matViewIndex.getMaterializedViewCollectionUuid(),
            matViewIndex.getSchemaMetadata()),
        rawDefinitionGeneration.generation());
  }
}
