package com.xgen.mongot.index.lucene.explain;

import static com.xgen.mongot.util.bson.FloatVector.OriginalType.NATIVE;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.common.collect.Streams;
import com.google.common.truth.Truth;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.lucene.explain.explainers.CollectorTimingFeatureExplainer;
import com.xgen.mongot.index.lucene.explain.explainers.MetadataFeatureExplainer;
import com.xgen.mongot.index.lucene.explain.profiler.QueryProfiler;
import com.xgen.mongot.index.lucene.explain.query.QueryExecutionContextNode;
import com.xgen.mongot.index.lucene.explain.query.QueryVisitorQueryExecutionContextNode;
import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.mongot.index.lucene.query.custom.ExactVectorSearchQuery;
import com.xgen.mongot.index.lucene.query.custom.MongotKnnFloatQuery;
import com.xgen.mongot.index.lucene.query.util.BooleanComposer;
import com.xgen.mongot.index.lucene.searcher.LuceneIndexSearcher;
import com.xgen.mongot.index.lucene.searcher.QueryCacheProvider;
import com.xgen.mongot.util.bson.Vector;
import com.xgen.mongot.util.timers.InvocationCountingTimer;
import com.xgen.testing.LuceneIndexRule;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ProfilingIndexSearcherTest.ClassTest.class,
      ProfilingIndexSearcherTest.ConcurrencyTest.class
    })
public class ProfilingIndexSearcherTest {

  private static final IndexMetricsUpdater.QueryingMetricsUpdater metrics =
      new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory());

  public static class ClassTest {
    @Rule public final LuceneIndexRule validator = new LuceneIndexRule();

    @Test
    public void testMetadataExplainerRecordsTotalLuceneDocs() throws IOException {
      this.validator.getIndexWriter().addDocument(new Document());
      this.validator.getIndexWriter().addDocument(new Document());

      try (var unused =
          Explain.setup(
              Optional.of(Explain.Verbosity.QUERY_PLANNER),
              Optional.of(IndexDefinition.Fields.NUM_PARTITIONS.getDefaultValue()))) {

        var metadataExplainer =
            Explain.getExplainQueryState()
                .get()
                .getQueryInfo()
                .getFeatureExplainer(MetadataFeatureExplainer.class, MetadataFeatureExplainer::new);

        var indexSearcher =
            new ProfilingIndexSearcher(
                LuceneIndexSearcher.create(
                    this.validator.getIndexReader(),
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    false,
                    Optional.empty()),
                new QueryProfiler(),
                new CollectorTimingFeatureExplainer(),
                metadataExplainer);
        indexSearcher.search(new MatchNoDocsQuery(), new TopScoreDocCollectorManager(5, 0));

        Truth.assertThat(Explain.collect().get().metadata().get().lucene().get().totalDocs().get())
            .isEqualTo(2);
        Truth.assertThat(
                Explain.collect().get().metadata().get().lucene().get().totalSegments().get())
            .isEqualTo(1);
      }
    }

    @Test
    public void testDeprecatedSearchCreatesNewQueryExecutionContext() throws IOException {
      var mockQueryProfiler = spy(new QueryProfiler());

      var indexSearcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.validator.getIndexReader(),
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer());
      indexSearcher.search(new MatchAllDocsQuery(), new TopScoreDocCollectorManager(5, 0));

      Mockito.verify(mockQueryProfiler, Mockito.times(1)).createQueryExecutionContext();
    }

    @Test
    public void testSearchCreatesNewQueryExecutionContext() throws IOException {
      var mockQueryProfiler = spy(new QueryProfiler());

      var indexSearcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.validator.getIndexReader(),
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer());
      indexSearcher.search(new MatchAllDocsQuery(), new TopScoreDocCollectorManager(2, 0));

      Mockito.verify(mockQueryProfiler, Mockito.times(1)).createQueryExecutionContext();
    }

    @Test
    public void testSearchAfterNewQueryExecutionContext() throws IOException {
      var mockQueryProfiler = spy(new QueryProfiler());

      var indexSearcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.validator.getIndexReader(),
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer());
      indexSearcher.searchAfter(new ScoreDoc(0, 1.0f), new MatchAllDocsQuery(), 2);

      Mockito.verify(mockQueryProfiler, Mockito.times(1)).createQueryExecutionContext();
    }

    @Test
    public void testSimpleQuery() throws Exception {
      Query query = termQuery("value");
      QueryProfiler profiler = new QueryProfiler();

      RootQueryNode.create(
              this.validator.getIndexReader(),
              profiler,
              query,
              new QueryNodeDescriptor(query),
              false)
          .test();
    }

    @Test
    public void testSimpleNestedQuery() throws Exception {
      Query a = termQuery("a");
      Query b = termQuery("b");

      Query rootQuery =
          new BooleanQuery.Builder()
              .add(BooleanComposer.shouldClause(a))
              .add(BooleanComposer.shouldClause(b))
              .build();

      QueryProfiler profiler = new QueryProfiler();
      QueryNodeDescriptor expectedQueryDescriptor =
          new QueryNodeDescriptor(rootQuery, QueryNodeDescriptor.queries(a, b));

      RootQueryNode.create(
              this.validator.getIndexReader(), profiler, rootQuery, expectedQueryDescriptor, false)
          .test();
    }

    @Test
    public void testNestedQuery() throws Exception {
      Query a = termQuery("a");
      Query b = termQuery("b");
      Query c = termQuery("c");
      Query d = termQuery("d");
      Query e = termQuery("e");
      Query f = termQuery("f");
      Query g = termQuery("g");

      Query nested =
          new BooleanQuery.Builder()
              .add(BooleanComposer.mustClause(b))
              .add(BooleanComposer.mustClause(c))
              .add(BooleanComposer.mustClause(d))
              .build();

      Query rootQuery =
          new BooleanQuery.Builder()
              .add(BooleanComposer.shouldClause(a))
              .add(BooleanComposer.shouldClause(nested))
              .add(BooleanComposer.shouldClause(e))
              .add(BooleanComposer.shouldClause(f))
              .add(BooleanComposer.mustNotClause(g))
              .build();

      QueryProfiler profiler = new QueryProfiler();

      QueryNodeDescriptor expectedQueryDescriptor =
          new QueryNodeDescriptor(
              rootQuery,
              List.of(
                  new QueryNodeDescriptor(a),
                  new QueryNodeDescriptor(nested, QueryNodeDescriptor.queries(b, c, d)),
                  new QueryNodeDescriptor(e),
                  new QueryNodeDescriptor(f),
                  new QueryNodeDescriptor(g)));

      RootQueryNode.create(
              this.validator.getIndexReader(), profiler, rootQuery, expectedQueryDescriptor, false)
          .test();
    }

    @Test
    public void testRewrittenQuery() throws Exception {
      Query query =
          new BooleanQuery.Builder()
              .add(BooleanComposer.shouldClause(termQuery("a")))
              .add(
                  BooleanComposer.shouldClause(
                      new BooleanQuery.Builder()
                          .add(BooleanComposer.shouldClause(termQuery("b")))
                          .add(BooleanComposer.shouldClause(termQuery("c")))
                          .build()))
              .build();

      QueryProfiler profiler = new QueryProfiler();

      QueryNodeDescriptor expectedQueryDescriptor =
          new QueryNodeDescriptor(
              "path:a path:b path:c", QueryNodeDescriptor.queries("path:a", "path:b", "path:c"));

      RootQueryNode.create(
              this.validator.getIndexReader(), profiler, query, expectedQueryDescriptor, false)
          .test();
    }

    @Test
    public void testSimpleVectorQuery() throws Exception {
      Query query = knnQuery();

      RootQueryNode.create(
              this.validator.getIndexReader(),
              new QueryProfiler(),
              query,
              QueryNodeDescriptor.forVectorQueryRoot(query, Optional.empty()),
              true)
          .test();
    }

    @Test
    public void testRewrittenVectorQuery() throws Exception {
      Query filter =
          new BooleanQuery.Builder()
              .add(BooleanComposer.shouldClause(termQuery("a")))
              .add(
                  BooleanComposer.shouldClause(
                      new BooleanQuery.Builder()
                          .add(BooleanComposer.shouldClause(termQuery("b")))
                          .add(BooleanComposer.shouldClause(termQuery("c")))
                          .build()))
              .build();
      Query query = knnQueryWithFilter(filter);
      QueryNodeDescriptor expectedQueryDescriptor =
          QueryNodeDescriptor.forVectorQueryRoot(
              query, Optional.of(QueryNodeDescriptor.forVectorQueryFilter(filter)));

      RootQueryNode.create(
              this.validator.getIndexReader(),
              new QueryProfiler(),
              query,
              expectedQueryDescriptor,
              true)
          .test();
    }

    private static Query knnQuery() {
      return new MongotKnnFloatQuery(metrics, "path", new float[] {1F, 2F, 3F}, 100);
    }

    private static Query knnQueryWithFilter(Query filter) {
      return new MongotKnnFloatQuery(
          metrics, FeatureFlags.getDefault(), "path", new float[] {1F, 2F, 3F}, 100, filter);
    }

    private static Query termQuery(String value) {
      return new TermQuery(new Term("path", value));
    }

    static class QueryNodeDescriptor {
      final ArgumentMatcher<Query> queryArgumentMatcher;
      final List<QueryNodeDescriptor> children;

      public QueryNodeDescriptor(String toStringValue) {
        this(toStringValue, Collections.emptyList());
      }

      public QueryNodeDescriptor(String toStringValue, List<QueryNodeDescriptor> children) {
        this(arg -> arg.toString().equals(toStringValue), children);
      }

      public QueryNodeDescriptor(Query query) {
        this(query, Collections.emptyList());
      }

      public QueryNodeDescriptor(Query query, List<QueryNodeDescriptor> children) {
        this(argument -> argument.toString().equals(query.toString()), children);
      }

      public QueryNodeDescriptor(
          ArgumentMatcher<Query> queryArgumentMatcher, List<QueryNodeDescriptor> children) {
        this.queryArgumentMatcher = queryArgumentMatcher;
        this.children = children;
      }

      public static QueryNodeDescriptor forVectorQueryRoot(
          Query query, Optional<QueryNodeDescriptor> filter) {
        return new QueryNodeDescriptor(query, filter.map(List::of).orElse(new ArrayList<>()));
      }

      public static QueryNodeDescriptor forVectorQueryFilter(Query query) {
        return new QueryNodeDescriptor(
            // vectorSearch filters are rewritten before match, so it doesn't exact match, but
            // contains the original query
            argument -> argument.toString().contains(query.toString()),
            new ArrayList<>());
      }

      public static List<QueryNodeDescriptor> queries(String... queryToStrings) {
        return Arrays.stream(queryToStrings)
            .map(QueryNodeDescriptor::new)
            .collect(Collectors.toList());
      }

      public static List<QueryNodeDescriptor> queries(Query... queries) {
        return Arrays.stream(queries).map(QueryNodeDescriptor::new).collect(Collectors.toList());
      }
    }

    static class RootQueryNode {
      final QueryProfiler profiler;
      final ProfilingIndexSearcher searcher;
      final Query query;
      final QueryNode root;
      final InOrder inOrder;

      static RootQueryNode create(
          IndexReader reader,
          QueryProfiler realProfiler,
          Query query,
          QueryNodeDescriptor queryNodeDescriptor,
          boolean isVectorQuery)
          throws IOException {
        var mockQueryProfiler = spy(new QueryProfiler());
        return new RootQueryNode(
            new ProfilingIndexSearcher(
                LuceneIndexSearcher.create(
                    reader,
                    new QueryCacheProvider.DefaultQueryCacheProvider(),
                    Optional.empty(),
                    Optional.empty(),
                    false,
                    false,
                    Optional.empty()),
                mockQueryProfiler,
                new CollectorTimingFeatureExplainer(),
                new MetadataFeatureExplainer()),
            mockQueryProfiler,
            query,
            queryNodeDescriptor,
            isVectorQuery);
      }

      private RootQueryNode(
          ProfilingIndexSearcher searcher,
          QueryProfiler spyProfiler,
          Query query,
          QueryNodeDescriptor queryNodeDescriptor,
          boolean isVectorQuery) {
        this.profiler = spyProfiler;
        this.searcher = searcher;
        this.query = query;
        this.root =
            isVectorQuery
                ? setupVectorTree(this.profiler, queryNodeDescriptor)
                : setupTree(this.profiler, this.searcher, queryNodeDescriptor);
        List<Object> mocks = new ArrayList<>(mocks(this.root));
        mocks.add(this.profiler);
        this.inOrder = Mockito.inOrder(mocks.toArray(new Object[0]));
      }

      void test() throws IOException {
        this.searcher.search(this.query, 2);
        this.root.verify(this.inOrder);
      }

      static QueryNode setupTree(
          QueryProfiler profiler, ProfilingIndexSearcher searcher, QueryNodeDescriptor root) {
        List<QueryNode> children =
            root.children.stream()
                .map(child -> setupTree(profiler, searcher, child))
                .collect(Collectors.toList());

        return QueryNode.forSearchQuery(searcher, profiler, root.queryArgumentMatcher, children);
      }

      static QueryNode setupVectorTree(QueryProfiler profiler, QueryNodeDescriptor root) {
        List<QueryNode> children =
            root.children.stream()
                .map(filter -> setupVectorFilter(profiler, filter))
                .collect(Collectors.toList());

        return QueryNode.forVectorQuery(profiler, root.queryArgumentMatcher, children);
      }

      static QueryNode setupVectorFilter(QueryProfiler profiler, QueryNodeDescriptor filter) {
        return QueryNode.forVectorFilterQuery(profiler, filter.queryArgumentMatcher);
      }

      static List<Object> mocks(QueryNode node) {
        return Streams.concat(
                node.mocks.stream(),
                node.children.stream().map(RootQueryNode::mocks).flatMap(Collection::stream))
            .collect(Collectors.toList());
      }
    }

    static class QueryNode {
      final QueryProfiler profiler;
      final ArgumentMatcher<Query> queryMatcher;
      final List<QueryNode> children;

      final Optional<QueryExecutionContextNode> contextNode;
      final ExplainTimings timings;
      final InvocationCountingTimer.SafeClosable splitClosable;
      final List<Object> mocks;

      final boolean isVectorQuery;

      public static QueryNode forSearchQuery(
          ProfilingIndexSearcher searcher,
          QueryProfiler profiler,
          ArgumentMatcher<Query> queryMatcher,
          List<QueryNode> children) {

        QueryVisitorQueryExecutionContextNode contextNode =
            mock(QueryVisitorQueryExecutionContextNode.class);
        ExplainTimings timings = spy(ExplainTimings.builder().build());
        InvocationCountingTimer.SafeClosable splitClosable =
            mock(InvocationCountingTimer.SafeClosable.class);

        Mockito.doReturn(Optional.of(contextNode))
            .when(profiler)
            .getOrCreateNode(argThat(queryMatcher));
        Mockito.doReturn(timings).when(contextNode).getTimings();
        Mockito.doReturn(splitClosable).when(timings).split(ExplainTimings.Type.CREATE_WEIGHT);

        Mockito.doNothing().when(splitClosable).close();
        return new QueryNode(
            profiler,
            queryMatcher,
            children,
            Optional.of(contextNode),
            timings,
            splitClosable,
            false);
      }

      public static QueryNode forVectorQuery(
          QueryProfiler profiler, ArgumentMatcher<Query> queryMatcher, List<QueryNode> children) {

        QueryVisitorQueryExecutionContextNode contextNode =
            mock(QueryVisitorQueryExecutionContextNode.class);
        ExplainTimings timings = spy(ExplainTimings.builder().build());
        InvocationCountingTimer.SafeClosable splitClosable =
            mock(InvocationCountingTimer.SafeClosable.class);

        Mockito.doReturn(Optional.of(contextNode))
            .when(profiler)
            .getOrCreateNode(argThat(queryMatcher));
        Mockito.doReturn(timings).when(contextNode).getTimings();
        Mockito.doReturn(splitClosable)
            .when(timings)
            .split(
                or(
                    eq(ExplainTimings.Type.CREATE_WEIGHT),
                    eq(ExplainTimings.Type.VECTOR_EXECUTION)));
        Mockito.doNothing().when(profiler).addVectorMustNode(any(Query.class), any(Query.class));
        Mockito.doNothing().when(splitClosable).close();

        return new QueryNode(
            profiler,
            queryMatcher,
            children,
            Optional.of(contextNode),
            timings,
            splitClosable,
            true);
      }

      public static QueryNode forVectorFilterQuery(
          QueryProfiler profiler, ArgumentMatcher<Query> queryMatcher) {

        ExplainTimings timings = spy(ExplainTimings.builder().build());
        InvocationCountingTimer.SafeClosable splitClosable =
            mock(InvocationCountingTimer.SafeClosable.class);

        Mockito.doReturn(Optional.empty()).when(profiler).getOrCreateNode(argThat(queryMatcher));
        Mockito.doReturn(splitClosable).when(timings).split(ExplainTimings.Type.CREATE_WEIGHT);
        Mockito.doNothing().when(profiler).addVectorFilterNode(any(Query.class));
        Mockito.doNothing().when(splitClosable).close();

        return new QueryNode(
            profiler,
            queryMatcher,
            new ArrayList<>(),
            Optional.empty(),
            timings,
            splitClosable,
            true);
      }

      public QueryNode(
          QueryProfiler profiler,
          ArgumentMatcher<Query> queryMatcher,
          List<QueryNode> children,
          Optional<QueryExecutionContextNode> contextNode,
          ExplainTimings timings,
          InvocationCountingTimer.SafeClosable splitClosable,
          boolean isVectorQuery) {
        this.profiler = profiler;
        this.queryMatcher = queryMatcher;
        this.children = children;
        this.isVectorQuery = isVectorQuery;
        this.contextNode = contextNode;
        this.timings = timings;
        this.splitClosable = splitClosable;
        this.mocks =
            this.contextNode
                .map(node -> List.of(node, this.timings, this.splitClosable))
                .orElseGet(() -> List.of(this.timings, this.splitClosable));
      }

      void verify(InOrder inOrder) {
        verifyBefore(inOrder);
        for (var child : this.children) {
          child.verify(inOrder);
        }
        verifyAfter(inOrder);
      }

      private void verifyBefore(InOrder inOrder) {
        inOrder.verify(this.profiler, atLeastOnce()).getOrCreateNode(argThat(this.queryMatcher));
        this.contextNode.ifPresent(node -> inOrder.verify(node).getTimings());
        this.contextNode.ifPresent(
            node ->
                inOrder
                    .verify(this.timings)
                    .split(
                        this.isVectorQuery
                            ? or(
                                eq(ExplainTimings.Type.CREATE_WEIGHT),
                                eq(ExplainTimings.Type.VECTOR_EXECUTION))
                            : ExplainTimings.Type.CREATE_WEIGHT));
      }

      private void verifyAfter(InOrder inOrder) {
        this.contextNode.ifPresent(node -> inOrder.verify(this.splitClosable).close());
      }
    }
  }

  public static class ConcurrencyTest {
    private Directory directory;
    private IndexWriter writer;
    private IndexReader reader;

    @Before
    public void setUp() throws IOException {
      TemporaryFolder temporaryFolder = TestUtils.getTempFolder();
      this.directory = new MMapDirectory(temporaryFolder.getRoot().toPath());
      this.writer =
          new IndexWriter(
              this.directory, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE));
    }

    @After
    public void tearDown() throws IOException {
      this.reader.close();
      this.writer.close();
      this.directory.close();
    }

    @Test
    public void testSimpleConcurrentSearch() throws IOException {
      List<Document> docs =
          IntStream.range(0, 100_000)
              .mapToObj(
                  unused -> {
                    Document doc = new Document();
                    doc.add(new StringField("path", "foo", Field.Store.NO));
                    return doc;
                  })
              .toList();

      insertDocs(10_000, docs);

      var mockQueryProfiler = spy(new QueryProfiler());
      var searcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.reader,
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer(),
              Executors.newCachedThreadPool());

      try {
        searcher.search(new TermQuery(new Term("path", "foo")), 50_000);
      } catch (Exception e) {
        Assert.fail("Expected no exceptions, but got: " + Arrays.toString(e.getStackTrace()));
      }
    }

    @Test
    public void testSimpleConcurrentVectorSearch() throws IOException {
      List<Document> docs =
          IntStream.range(0, 100_000)
              .mapToObj(
                  unused -> {
                    Document doc = new Document();
                    doc.add(
                        new KnnFloatVectorField(
                            "path", new float[] {1F, 2F, 3F}, VectorSimilarityFunction.COSINE));
                    return doc;
                  })
              .toList();

      insertDocs(10_000, docs);

      var mockQueryProfiler = spy(new QueryProfiler());
      var searcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.reader,
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer(),
              Executors.newCachedThreadPool());

      try {
        searcher.search(
            new MongotKnnFloatQuery(metrics, "path", new float[] {1F, 2F, 3F}, 100), 50_000);
      } catch (Exception e) {
        Assert.fail("Expected no exceptions, but got: " + Arrays.toString(e.getStackTrace()));
      }
    }

    @Test
    public void testSimpleConcurrentExactVectorSearch() throws IOException {
      List<Document> docs =
          IntStream.range(0, 100_000)
              .mapToObj(
                  unused -> {
                    Document doc = new Document();
                    doc.add(
                        new KnnFloatVectorField(
                            "path", new float[] {1F, 2F, 3F}, VectorSimilarityFunction.COSINE));
                    return doc;
                  })
              .toList();

      insertDocs(10_000, docs);

      var mockQueryProfiler = spy(new QueryProfiler());
      var searcher =
          new ProfilingIndexSearcher(
              LuceneIndexSearcher.create(
                  this.reader,
                  new QueryCacheProvider.DefaultQueryCacheProvider(),
                  Optional.empty(),
                  Optional.empty(),
                  false,
                  false,
                  Optional.empty()),
              mockQueryProfiler,
              new CollectorTimingFeatureExplainer(),
              new MetadataFeatureExplainer(),
              Executors.newCachedThreadPool());

      try {
        searcher.search(
            new ExactVectorSearchQuery(
                "path",
                Vector.fromFloats(new float[] {1F, 2F, 3F}, NATIVE),
                VectorSimilarityFunction.COSINE,
                new MatchAllDocsQuery()),
            50_000);
      } catch (Exception e) {
        Assert.fail("Expected no exceptions, but got: " + Arrays.toString(e.getStackTrace()));
      }
    }

    private void insertDocs(int perSegDocLimit, List<Document> docs) throws IOException {
      for (int i = 0; i < docs.size(); i++) {
        this.writer.addDocument(docs.get(i));
        if (i > 0 && i % perSegDocLimit == 0) {
          this.writer.commit();
        }
      }

      this.writer.commit();
      this.reader = DirectoryReader.open(this.writer);
    }
  }
}
