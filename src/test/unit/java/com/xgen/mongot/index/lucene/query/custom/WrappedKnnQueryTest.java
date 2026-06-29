package com.xgen.mongot.index.lucene.query.custom;

import com.google.common.truth.Truth;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.query.VectorSearchQuery;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.io.IOException;
import java.util.Optional;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WrappedKnnQueryTest {

  private static final IndexMetricsUpdater.QueryingMetricsUpdater metrics =
      new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory());

  private static final String DEFAULT_PATH = "path";
  private static final float[] DEFAULT_TARGET = new float[] {1F, 2F, 3F};
  private static final int DEFAULT_K = 100;
  private static Directory directory;
  private static IndexWriter writer;
  private static IndexReader reader;

  public static WrappedKnnQuery getWrappedKnnQuery(
      Query query, VectorSearchQuery vectorSearchQuery, Optional<FieldPath> operatorPath) {
    return new WrappedKnnQuery(query);
  }

  @Before
  public void setUp() throws IOException {
    TemporaryFolder temporaryFolder = TestUtils.getTempFolder();
    directory = new MMapDirectory(temporaryFolder.getRoot().toPath());
    writer = new IndexWriter(directory, new IndexWriterConfig());
    writer.commit();
    reader = DirectoryReader.open(directory);
  }

  /** Closes resources required to run tests. */
  @After
  public void tearDown() throws IOException {
    writer.close();
    reader.close();
    directory.close();
  }

  @Test
  public void testRewriteReturns() throws IOException {
    Query query = knnQuery(DEFAULT_TARGET, DEFAULT_K);
    var searcher = new IndexSearcher(reader);
    WrappedKnnQuery wrappedKnnQuery = new WrappedKnnQuery(query);
    Query rewritten = wrappedKnnQuery.rewrite(searcher);
    Assert.assertNotEquals("KnnFloatVectorQuery should be rewritten", wrappedKnnQuery, rewritten);

    Truth.assertThat(rewritten).isInstanceOf(WrappedKnnQuery.class);
    WrappedKnnQuery wrappedRewritten = (WrappedKnnQuery) rewritten;
    Assert.assertEquals(
        "KnnFloatVectorQuery rewrites to MatchNoDocsQuery when there's no documents to return",
        MatchNoDocsQuery.class.getName(),
        wrappedRewritten.getQuery().getClass().getName());
  }

  @Test
  public void testAsWrapped() {
    Query query = knnQuery(DEFAULT_TARGET, DEFAULT_K);
    WrappedKnnQuery wrappedKnnQuery = new WrappedKnnQuery(query);

    Assert.assertEquals(Optional.of(wrappedKnnQuery), WrappedKnnQuery.asWrapped(wrappedKnnQuery));
    Assert.assertEquals(Optional.empty(), WrappedKnnQuery.asWrapped(query));
  }

  private static Query knnQuery(float[] target, int k) {
    return new MongotKnnFloatQuery(
        metrics, FeatureFlags.getDefault(), DEFAULT_PATH, target, k, null);
  }
}
