package com.xgen.mongot.index.lucene.query.custom;

import static com.xgen.mongot.index.lucene.query.util.BooleanComposer.filterClause;
import static com.xgen.mongot.index.lucene.query.util.BooleanComposer.mustClause;
import static com.xgen.mongot.index.lucene.query.util.BooleanComposer.shouldClause;

import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.TestUtils;
import java.io.IOException;
import java.util.Optional;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WrappedQueryTest {

  private static Directory directory;
  private static IndexWriter writer;
  private static IndexReader reader;

  public static WrappedQuery createWrappedQuery(Query query, Optional<FieldPath> operatorPath) {
    return new WrappedQuery(query, operatorPath);
  }

  /** Resources required to run several of these tests. */
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
  public void testRewriteReturnsSameWrappedQuery() throws IOException {
    Query termQuery = termQuery("hello");
    WrappedQuery wrappedQuery = new WrappedQuery(termQuery);

    Query rewritten = wrappedQuery.rewrite(new IndexSearcher(reader));
    Assert.assertSame("wrapped TermQuery should not be rewritten", wrappedQuery, rewritten);
  }

  @Test
  public void testRewriteReturnsExpectedWrappedQuery() throws IOException {
    PrefixQuery prefixQuery = new PrefixQuery(new Term("a", "hello"));
    WrappedQuery wrappedQuery = new WrappedQuery(prefixQuery);

    var searcher = new IndexSearcher(reader);
    Query result = wrappedQuery.rewrite(searcher);
    Assert.assertNotEquals(
        "wrapped PrefixQuery should be rewritten into a different query", wrappedQuery, result);

    // Default rewrite method used in MultiTermQuery is CONSTANT_SCORE_BLENDED_REWRITE
    WrappedQuery expected =
        new WrappedQuery(
            MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE.rewrite(
                searcher, new PrefixQuery(new Term("a", "hello"))));

    Assert.assertEquals(
        "wrapped PrefixQuery has been rewritten into the expected query", expected, result);
  }

  @Test
  public void testRewriteComplex() throws IOException {
    WrappedQuery innerQuery =
        new WrappedQuery(
            new BooleanQuery.Builder()
                .add(shouldClause(termQuery("b")))
                .add(filterClause(termQuery("b")))
                .build());

    Query outerQuery =
        new WrappedQuery(new BooleanQuery.Builder().add(filterClause(innerQuery)).build());

    Query firstRewrite = outerQuery.rewrite(new IndexSearcher(reader));
    Query firstRewriteExpected =
        new WrappedQuery(new BoostQuery(new ConstantScoreQuery(innerQuery), 0.0f));
    Assert.assertEquals(
        "outerQuery should be rewritten to BoostQuery with score of 0.0",
        firstRewriteExpected,
        firstRewrite);

    Query secondRewrite = firstRewrite.rewrite(new IndexSearcher(reader));
    BooleanQuery must = new BooleanQuery.Builder().add(mustClause(termQuery("b"))).build();
    Query secondRewriteExpected =
        new WrappedQuery(new BoostQuery(new ConstantScoreQuery(new WrappedQuery(must)), 0.0f));
    Assert.assertEquals(
        "rewrite should turn duplicate Filter & Should clause into a single Must",
        secondRewriteExpected,
        secondRewrite);

    Query thirdRewrite = secondRewrite.rewrite(new IndexSearcher(reader));
    Query expected =
        new WrappedQuery(
            new BoostQuery(new ConstantScoreQuery(new WrappedQuery(termQuery("b"))), 0.0f));
    Assert.assertEquals(
        "should rewrite single Must clause into the query inside the clause",
        expected,
        thirdRewrite);

    Query result = thirdRewrite.rewrite(new IndexSearcher(reader));
    Assert.assertSame("query should not be rewritten anymore", thirdRewrite, result);
  }

  @Test
  public void testExhaustRewrite() throws IOException {
    MultiTermQuery prefixQuery = new PrefixQuery(new Term("a", "hello"));
    WrappedQuery wrappedQuery = new WrappedQuery(prefixQuery);

    Query firstRewrite = wrappedQuery.rewrite(new IndexSearcher(reader));
    // should not change with the following rewrite
    Query secondRewrite = firstRewrite.rewrite(new IndexSearcher(reader));

    Assert.assertSame(
        "wrapped PrefixQuery should be done rewriting itself", firstRewrite, secondRewrite);
  }

  @Test
  public void testEquals() {
    TestUtils.assertEqualityGroups(
        () -> new WrappedQuery(termQuery("hello")), () -> new WrappedQuery(termQuery("world")));
  }

  private static Query termQuery(String value) {
    return new TermQuery(new Term("path", value));
  }
}
