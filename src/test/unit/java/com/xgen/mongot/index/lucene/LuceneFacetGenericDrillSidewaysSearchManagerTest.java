package com.xgen.mongot.index.lucene;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.analyzer.wrapper.LuceneAnalyzer;
import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.lucene.codec.LuceneCodec;
import com.xgen.mongot.index.lucene.searcher.LuceneSearcherFactory;
import com.xgen.mongot.index.lucene.searcher.LuceneSearcherManager;
import com.xgen.mongot.index.lucene.searcher.QueryCacheProvider;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.util.concurrent.Executors;
import com.xgen.mongot.util.concurrent.NamedExecutorService;
import com.xgen.testing.mongot.index.analyzer.AnalyzerRegistryBuilder;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.DrillSideways.ConcurrentDrillSidewaysResult;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Theories.class)
public class LuceneFacetGenericDrillSidewaysSearchManagerTest {

  private static final String FACET_1 = "categoryFacet";
  private static final String FACET_2 = "priceFacet";
  private static final String FACET_3 = "brandFacet";

  @Mock private MongotDrillSideways mockGenericDrillSideways;
  @Mock private ConcurrentDrillSidewaysResult mockResult;

  private LuceneIndexSearcherReference searcherReference;
  private IndexWriter indexWriter;
  private Directory directory;
  private Query testQuery;

  private Map<String, LuceneDrillSideways> facetQueries;
  private LuceneDrillSideways mockLuceneDrillSideways;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    this.directory = new ByteBuffersDirectory();
    IndexWriterConfig config =
        new IndexWriterConfig(
            LuceneAnalyzer.indexAnalyzer(
                SearchIndex.MOCK_INDEX_DEFINITION, AnalyzerRegistryBuilder.empty()));
    config.setCodec(new LuceneCodec());
    this.indexWriter = new IndexWriter(this.directory, config);

    addTestDocuments(true);

    LuceneSearcherManager searcherManager =
        LuceneSearcherManager.create(
            this.indexWriter,
            new LuceneSearcherFactory(
                SearchIndex.MOCK_INDEX_DEFINITION,
                false,
                new QueryCacheProvider.DefaultQueryCacheProvider(),
                Optional.empty(),
                SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
            SearchIndex.mockMetricsFactory(),
            () -> false);

    this.searcherReference =
        LuceneIndexSearcherReference.create(
            searcherManager,
            SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH),
            FeatureFlags.getDefault());

    this.testQuery = new MatchAllDocsQuery();
    this.facetQueries = new HashMap<>();
    DrillDownQuery mockDrillDownQuery = new DrillDownQuery(null, this.testQuery);
    this.mockLuceneDrillSideways =
        new LuceneDrillSideways(this.mockGenericDrillSideways, mockDrillDownQuery);

    when(this.mockGenericDrillSideways.searchSafe(
            any(DrillDownQuery.class), any(CollectorManager.class)))
        .thenReturn(this.mockResult);
  }

  @After
  public void tearDown() throws IOException {
    this.indexWriter.close();
    this.directory.close();
    this.searcherReference.close();
  }

  @DataPoints
  public static final Optional<NamedExecutorService>[] concurrentFacetExecutor =
      new Optional[] {
        Optional.empty(),
        Optional.of(Executors.singleThreadScheduledExecutor("test", new SimpleMeterRegistry()))
      };

  @Theory
  public void initialSearch_singleFacet_returnsExpectedResult(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 10);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertNotNull(result.facetResults);
    assertEquals(1, result.facetResults.size());
    assertThat(result.facetResults).containsKey(FACET_1);
    assertThat(result.luceneExhausted).isTrue(); // Should be exhausted with small dataset
  }

  @Theory
  public void initialSearch_multipleFacets_returnsExpectedResults(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    // Arrange
    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);
    this.facetQueries.put(FACET_2, this.mockLuceneDrillSideways);
    this.facetQueries.put(FACET_3, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 10);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertNotNull(result.facetResults);
    assertEquals(3, result.facetResults.size());
    assertThat(result.facetResults).containsKey(FACET_1);
    assertThat(result.facetResults).containsKey(FACET_2);
    assertThat(result.facetResults).containsKey(FACET_3);
  }

  @Theory
  public void initialSearch_emptyFacets_returnsExpectedResult(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    // Arrange
    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 10);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertNotNull(result.facetResults);
    assertEquals(0, result.facetResults.size());
    assertThat(result.luceneExhausted).isTrue();
  }

  @Theory
  public void initialSearch_largeBatchSize_returnsExpectedResult(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    // Arrange
    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 1000);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertThat(result.luceneExhausted).isTrue(); // Should be exhausted with only 3 documents
    assertEquals(1, result.facetResults.size());
  }

  @Theory
  public void initialSearch_smallBatchSize_returnsExpectedResult(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    // Arrange

    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 2);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertThat(result.luceneExhausted)
        .isFalse(); // Should not be exhausted with batch size < total docs
    assertEquals(1, result.facetResults.size());
  }

  @Theory
  public void initialSearch_withSort_returnsExpectedResult(
      Optional<NamedExecutorService> concurrentFacetExecutor)
      throws IOException, InvalidQueryException {
    // Arrange
    Sort sort = new Sort(new SortField("category", SortField.Type.STRING));

    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.of(sort),
            Optional.empty(),
            concurrentFacetExecutor);

    // Act
    LuceneFacetGenericDrillSidewaysSearchManager.GenericDrillSidewaysResultFacetCollectorQueryInfo
        result = searchManager.initialSearch(this.searcherReference, 10);

    // Assert
    assertNotNull(result);
    assertNotNull(result.topDocs);
    assertEquals(1, result.facetResults.size());
  }

  @Test
  public void getMoreTopDocs_happyPath_hasNonNullDocs() throws IOException {
    // Arrange
    this.facetQueries.put(FACET_1, this.mockLuceneDrillSideways);
    this.facetQueries.put(FACET_2, this.mockLuceneDrillSideways);

    LuceneFacetGenericDrillSidewaysSearchManager searchManager =
        new LuceneFacetGenericDrillSidewaysSearchManager(
            this.testQuery,
            this.facetQueries,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    ScoreDoc lastScoreDoc = new ScoreDoc(0, 1.0f);

    // Act
    TopDocs result = searchManager.getMoreTopDocs(this.searcherReference, lastScoreDoc, 5);

    // Assert
    assertNotNull(result);
    assertNotNull(result.scoreDocs);
  }

  private void addTestDocuments(boolean createSorted) throws IOException {
    Document doc1 = new Document();
    doc1.add(new StringField("id", "1", Field.Store.YES));
    doc1.add(newCategoryField("electronics", createSorted));
    this.indexWriter.addDocument(doc1);

    Document doc2 = new Document();
    doc2.add(new StringField("id", "2", Field.Store.YES));
    doc2.add(newCategoryField("books", createSorted));
    this.indexWriter.addDocument(doc2);

    Document doc3 = new Document();
    doc3.add(new StringField("id", "3", Field.Store.YES));
    doc3.add(newCategoryField("electronics", createSorted));
    this.indexWriter.addDocument(doc3);

    this.indexWriter.commit();
  }

  private Field newCategoryField(String categoryValue, boolean createSorted) {
    return createSorted
        ? new SortedDocValuesField("category", new BytesRef(categoryValue))
        : new StringField("category", categoryValue, Field.Store.NO);
  }
}
