package com.xgen.mongot.index.lucene.searcher;

import static com.google.common.truth.Truth.assertThat;

import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import io.micrometer.core.instrument.Gauge;
import java.io.IOException;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.Assert;
import org.junit.Test;

public class LuceneSearcherManagerTest {

  @Test
  public void testNoRefreshIfIndexNotChanged() throws IOException {
    try (var directory = new ByteBuffersDirectory()) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);
        var searcherBefore = manager.acquire();
        var searcherAfter = manager.refreshIfNeeded(searcherBefore);
        Assert.assertNull(searcherAfter);

        manager.maybeRefreshBlocking();
        Assert.assertSame(searcherBefore, manager.acquire());
      }
    }
  }

  @Test
  public void testMetricsPopulatedWithSegmentCount() throws IOException {
    try (var directory = new ByteBuffersDirectory()) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var metricsFactory = SearchIndex.mockMetricsFactory();
        LuceneSearcherManager.create(
            writer,
            new LuceneSearcherFactory(
                getIndexDefinition(),
                false,
                new QueryCacheProvider.DefaultQueryCacheProvider(),
                Optional.empty(),
                SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
            metricsFactory,
            () -> false);

        Gauge segmentGauge = metricsFactory.get(LuceneSearcherManager.SEGMENT_COUNT_METRIC).gauge();
        assertThat(segmentGauge.value()).isNotNaN();
      }
    }
  }

  @Test
  public void testRefreshWhenIndexHasChanged() throws IOException {
    try (var directory = new ByteBuffersDirectory()) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);
        var searcherBefore = manager.acquire();

        var doc = new Document();
        doc.add(new StringField("name", "value", Field.Store.YES));
        writer.addDocument(doc);

        var searcherAfter = manager.refreshIfNeeded(searcherBefore);
        Assert.assertNotNull(searcherAfter);

        manager.maybeRefreshBlocking();
        Assert.assertNotSame(searcherBefore, manager.acquire());
      }
    }
  }

  @Test
  public void testDecRef() throws IOException {
    try (var directory = new ByteBuffersDirectory()) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);
        var searcher = manager.acquire();
        var count = searcher.getIndexReader().getRefCount();
        Assert.assertEquals(count, manager.getRefCount(searcher));
        manager.decRef(searcher);
        Assert.assertEquals(count - 1, manager.getRefCount(searcher));
      }
    }
  }

  @Test
  public void testTryDecRefWhenReaderIsStillOpen() throws IOException {
    try (var directory = new ByteBuffersDirectory()) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);
        var searcher = manager.acquire();
        var count = searcher.getIndexReader().getRefCount();
        Assert.assertEquals(count, manager.getRefCount(searcher));
        Assert.assertTrue(manager.tryIncRef(searcher));
        Assert.assertEquals(count + 1, manager.getRefCount(searcher));
      }
    }
  }

  @Test
  public void testTryDecRefWhenReaderIsClosed() throws IOException {
    try (Directory directory = new ByteBuffersDirectory()) {
      try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);
        var searcher = manager.acquire();
        manager.decRef(searcher);
        manager.decRef(searcher);
        Assert.assertFalse(manager.tryIncRef(searcher));
      }
    }
  }

  @Test
  public void testNrtSearcherExposesUncommittedChangesMadeBeforeOpen() throws IOException {

    var folder = TestUtils.getTempFolder();

    try (var directory = new NIOFSDirectory(folder.getRoot().toPath())) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {

        var doc = new Document();
        doc.add(new StringField("name", "value", Field.Store.YES));
        writer.addDocument(doc);

        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);

        var resultAfterRefresh = manager.acquire().search(new MatchAllDocsQuery(), 10);
        Assert.assertEquals(1, resultAfterRefresh.totalHits.value());
      }
    }
  }

  @Test
  public void testNrtRefreshExposesUncommittedChangesAfterReopen() throws IOException {

    var folder = TestUtils.getTempFolder();

    try (var directory = new NIOFSDirectory(folder.getRoot().toPath())) {
      try (var writer = new IndexWriter(directory, new IndexWriterConfig())) {
        var manager =
            LuceneSearcherManager.create(
                writer,
                new LuceneSearcherFactory(
                    getIndexDefinition(),
                    false,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    SearchIndex.mockQueryMetricsUpdater(IndexDefinition.Type.SEARCH)),
                SearchIndex.mockMetricsFactory(),
                () -> false);

        var doc = new Document();
        doc.add(new StringField("name", "value", Field.Store.YES));
        writer.addDocument(doc);

        var resultBeforeRefresh = manager.acquire().search(new MatchAllDocsQuery(), 10);
        Assert.assertEquals(0, resultBeforeRefresh.totalHits.value());

        manager.maybeRefreshBlocking();

        var resultAfterRefresh = manager.acquire().search(new MatchAllDocsQuery(), 10);
        Assert.assertEquals(1, resultAfterRefresh.totalHits.value());
      }
    }
  }

  private SearchIndexDefinition getIndexDefinition() {
    return SearchIndexDefinitionBuilder.builder()
        .defaultMetadata()
        .mappings(DocumentFieldDefinitionBuilder.builder().dynamic(true).build())
        .build();
  }
}
