package com.xgen.mongot.index.autoembedding;

import com.xgen.mongot.index.IndexGeneration;
import com.xgen.mongot.index.definition.MaterializedViewIndexDefinitionGeneration;
import com.xgen.mongot.index.definition.VectorIndexDefinition;
import com.xgen.mongot.index.version.MaterializedViewGenerationId;
import com.xgen.mongot.util.Check;

/**
 * MaterializedViewIndexGeneration is index generation subtype for auto-embedding Materialized View
 * usage only, this is only used in MaterializedViewGenerator for replication workload, ConfigState
 * uses AutoEmbeddingIndexGeneration instead for status reporting and query workload.
 */
public class MaterializedViewIndexGeneration extends IndexGeneration {

  public MaterializedViewIndexGeneration(
      InitializedMaterializedViewIndex index,
      MaterializedViewIndexDefinitionGeneration definitionGeneration) {
    super(index, definitionGeneration);
  }

  @Override
  public MaterializedViewIndexDefinitionGeneration getDefinitionGeneration() {
    return Check.instanceOf(
        super.getDefinitionGeneration(), MaterializedViewIndexDefinitionGeneration.class);
  }

  @Override
  public MaterializedViewGenerationId getGenerationId() {
    return getDefinitionGeneration().getGenerationId();
  }

  @Override
  public InitializedMaterializedViewIndex getIndex() {
    return Check.instanceOf(super.getIndex(), InitializedMaterializedViewIndex.class);
  }

  @Override
  public VectorIndexDefinition getDefinition() {
    // TODO(CLOUDP-353553): Handle search index version - getIndexDefinition() now returns
    //  IndexDefinition which may be a SearchIndexDefinition.
    return getDefinitionGeneration().getIndexDefinition().asVectorDefinition();
  }

}
