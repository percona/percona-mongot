package com.xgen.mongot.replication.mongodb;

import static com.google.common.truth.Truth.assertThat;
import static com.xgen.testing.mongot.mock.index.IndexGeneration.mockIndexGeneration;
import static org.mockito.Mockito.mock;

import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.VectorIndexDefinitionBuilder;
import org.junit.Test;

public class ReplicationIndexManagerFactoryTest {

  @Test
  public void forIndexGeneration_vectorLiteIndex_doesNotReturnDefaultFactory() {
    var vectorLiteDef =
        VectorIndexDefinitionBuilder.builder().withCustomEngineVectorField("vector", 3).build();
    var indexGeneration = mockIndexGeneration(vectorLiteDef);
    var defaultFactory = mock(ReplicationIndexManagerFactory.class);

    var factory =
        ReplicationIndexManagerFactory.forIndexGeneration(indexGeneration, defaultFactory);

    assertThat(factory).isNotSameInstanceAs(defaultFactory);
  }

  @Test
  public void forIndexGeneration_nonCustomVectorIndex_returnsDefaultFactory() {
    var stdVectorDef =
        VectorIndexDefinitionBuilder.builder().withCosineVectorField("vector", 3).build();
    var indexGeneration = mockIndexGeneration(stdVectorDef);
    var defaultFactory = mock(ReplicationIndexManagerFactory.class);

    var factory =
        ReplicationIndexManagerFactory.forIndexGeneration(indexGeneration, defaultFactory);

    assertThat(factory).isSameInstanceAs(defaultFactory);
  }

  @Test
  public void forIndexGeneration_nonVectorIndex_returnsDefaultFactory() {
    var indexGeneration = mockIndexGeneration(SearchIndexDefinitionBuilder.VALID_INDEX);
    var defaultFactory = mock(ReplicationIndexManagerFactory.class);

    var factory =
        ReplicationIndexManagerFactory.forIndexGeneration(indexGeneration, defaultFactory);

    assertThat(factory).isSameInstanceAs(defaultFactory);
  }
}
