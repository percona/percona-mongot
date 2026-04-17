package com.xgen.mongot.index.lucene.query;

import static org.mockito.Mockito.mock;

import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.analyzer.AnalyzerRegistry;
import com.xgen.mongot.index.lucene.util.LuceneDoubleConversionUtils;
import com.xgen.mongot.index.query.QueryOptimizationFlags;
import com.xgen.mongot.index.query.operators.NearOperator;
import com.xgen.mongot.index.query.points.DoublePoint;
import com.xgen.mongot.index.query.points.LongPoint;
import com.xgen.mongot.index.synonym.SynonymRegistry;
import com.xgen.mongot.index.version.IndexFormatVersion;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NumericNearQueryFactoriesTest {

  private static Directory directory;
  private static IndexWriter writer;

  /** set up an index. */
  @Before
  public void setUp() throws IOException {
    TemporaryFolder temporaryFolder = TestUtils.getTempFolder();
    directory = new MMapDirectory(temporaryFolder.getRoot().toPath());
    writer = new IndexWriter(directory, new IndexWriterConfig());
    writer.commit();
  }

  @After
  public void tearDown() throws IOException {
    writer.close();
    directory.close();
  }

  @Test
  public void testBasicNearLongOrigin() throws Exception {
    testLongOrigin("start", 0L, 1.0);
    testLongOrigin("start", -100L, 10.0);
    testLongOrigin("start", 100L, 0.5);
    testLongOrigin("start", 1234L, 1234567890123.0);
    testLongOrigin("start", 999999999999L, 1.0);
  }

  @Test
  public void testBasicNearDoubleOrigin() throws Exception {
    testDoubleOrigin("start", 0.0, 1.0);
    testDoubleOrigin("start", -100.0, 10.0);
    testDoubleOrigin("start", 100.0, 0.5);
    testDoubleOrigin("start", 1234.0, 1234567890123.0);
    testDoubleOrigin("start", 999999999999.0, 1.0);
  }

  @Test
  public void testOriginPivotHighMagnitudeNegativeLimits() throws Exception {
    testLongOrigin("start", Long.MIN_VALUE, 1.0);
    testLongOrigin("start", Long.MIN_VALUE + 1L, 1.0);
    testLongOrigin("start", Math.round(0.9 * Long.MIN_VALUE), 1.0);

    testDoubleOrigin("start", -1.0 * Double.MAX_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextUp(-1.0 * Double.MAX_VALUE), 1.0);
    testDoubleOrigin("start", 1.1 * Long.MIN_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextDown(Double.valueOf(Long.MIN_VALUE)), 1.0);
    testDoubleOrigin("start", Long.MIN_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextUp(Double.valueOf(Long.MIN_VALUE)), 1.0);
    testDoubleOrigin("start", 0.9 * Long.MIN_VALUE, 1.0);
  }

  @Test
  public void testNearOriginHighMagnitudePositiveLimits() throws Exception {
    testLongOrigin("start", Math.round(0.9 * Long.MAX_VALUE), 1.0);
    testLongOrigin("start", Long.MAX_VALUE - 1L, 1.0);
    testLongOrigin("start", Long.MAX_VALUE, 1.0);

    testDoubleOrigin("start", 0.9 * Long.MAX_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextDown(Double.valueOf(Long.MAX_VALUE)), 1.0);
    testDoubleOrigin("start", Long.MAX_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextUp(Double.valueOf(Long.MAX_VALUE)), 1.0);
    testDoubleOrigin("start", 1.1 * Long.MAX_VALUE, 1.0);
    testDoubleOrigin("start", Math.nextDown(Double.MAX_VALUE), 1.0);
    testDoubleOrigin("start", Double.MAX_VALUE, 1.0);
  }

  @Test
  public void testNearOriginLowMagnitudeLimits() throws Exception {
    testLongOrigin("start", 2, 1.0);
    testLongOrigin("start", 1, 1.0);
    testLongOrigin("start", 0, 1.0);
    testLongOrigin("start", -1, 1.0);
    testLongOrigin("start", -2, 1.0);

    testDoubleOrigin("start", 1.5, 1.0);
    testDoubleOrigin("start", 1.0, 1.0);
    testDoubleOrigin("start", 0.5, 1.0);
    testDoubleOrigin("start", 0.49999, 1.0);
    testDoubleOrigin("start", 1E-10, 1.0);
    testDoubleOrigin("start", Math.nextUp(Double.MIN_VALUE), 1.0);
    testDoubleOrigin("start", Double.MIN_VALUE, 1.0);
  }

  @Test
  public void testNearPivotHighMagnitudePositiveLimits() throws Exception {
    testLongOrigin("start", 0L, 0.9 * Long.MAX_VALUE);
    testLongOrigin("start", 0L, Math.nextDown(Double.valueOf(Long.MAX_VALUE)));
    testLongOrigin("start", 0L, Long.MAX_VALUE);
    testLongOrigin("start", 0L, Math.nextUp(Double.valueOf(Long.MAX_VALUE)));
    testLongOrigin("start", 0L, 1.1 * Long.MAX_VALUE);
    testLongOrigin("start", 0L, Math.nextDown(Double.MAX_VALUE));
    testLongOrigin("start", 0L, Double.MAX_VALUE);

    testDoubleOrigin("start", 0.0, 0.9 * Long.MAX_VALUE);
    testDoubleOrigin("start", 0.0, Math.nextDown(Double.valueOf(Long.MAX_VALUE)));
    testDoubleOrigin("start", 0.0, Long.MAX_VALUE);
    testDoubleOrigin("start", 0.0, Math.nextUp(Double.valueOf(Long.MAX_VALUE)));
    testDoubleOrigin("start", 0.0, 1.1 * Long.MAX_VALUE);
    testDoubleOrigin("start", 0.0, Math.nextDown(Double.MAX_VALUE));
    testDoubleOrigin("start", 0.0, Double.MAX_VALUE);
  }

  @Test
  public void testNearPivotLowMagnitudeLimits() throws Exception {
    testLongOrigin("start", 0L, 1.5);
    testLongOrigin("start", 0L, 1.0);
    testLongOrigin("start", 0L, 0.5);
    testLongOrigin("start", 0L, 0.49999);
    testLongOrigin("start", 0L, 1E-10);
    testLongOrigin("start", 0L, Math.nextUp(Double.MIN_VALUE));
    testLongOrigin("start", 0L, Double.MIN_VALUE);

    testDoubleOrigin("start", 0.0, 1.5);
    testDoubleOrigin("start", 0.0, 1.0);
    testDoubleOrigin("start", 0.0, 0.5);
    testDoubleOrigin("start", 0.0, 0.49999);
    testDoubleOrigin("start", 0.0, 1E-10);
    testDoubleOrigin("start", 0.0, Math.nextUp(Double.MIN_VALUE));
    testDoubleOrigin("start", 0.0, Double.MIN_VALUE);
  }

  private static void testDoubleOrigin(String path, double origin, double pivot) throws Exception {
    testLongOrigin(path, LuceneDoubleConversionUtils.toLong(origin), pivot);

    NearOperator definition =
        OperatorBuilder.near().path(path).origin(new DoublePoint(origin)).pivot(pivot).build();

    BooleanQuery expected = expectedForDoubleOrigin(path, origin, pivot);
    buildResultAndRunTest(expected, definition);
  }

  private static void testLongOrigin(String path, long origin, double pivot) throws Exception {
    NearOperator definition =
        OperatorBuilder.near().path(path).origin(new LongPoint(origin)).pivot(pivot).build();

    BooleanQuery expected = expectedForLongOrigin(path, origin, pivot);
    buildResultAndRunTest(expected, definition);
  }

  private static void buildResultAndRunTest(BooleanQuery expected, NearOperator definition)
      throws Exception {
    LuceneSearchQueryFactoryDistributor factory =
        LuceneSearchQueryFactoryDistributor.create(
            SearchIndexDefinitionBuilder.VALID_INDEX,
            IndexFormatVersion.CURRENT,
            mock(AnalyzerRegistry.class),
            mock(SynonymRegistry.class),
            new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory()),
            false,
            FeatureFlags.getDefault());

    Query result =
        factory.createQuery(
            definition, DirectoryReader.open(directory), QueryOptimizationFlags.DEFAULT_OPTIONS);
    Assert.assertEquals(BooleanQuery.class, result.getClass());
    assertExpectedEqualsResult(expected, (BooleanQuery) result);
  }

  private static void assertExpectedEqualsResult(BooleanQuery expected, BooleanQuery result) {
    Assert.assertEquals(
        "$type:long field has expected clause", expected.clauses().get(0), result.clauses().get(0));

    Assert.assertEquals(
        "$type:double field is correct boolean clause type",
        expected.clauses().get(1).occur(),
        result.clauses().get(1).occur());

    BooleanQuery expectedDoubleQuery = (BooleanQuery) getClause(expected, 1).query();
    BooleanQuery resultDoubleQuery = (BooleanQuery) getClause(result, 1).query();

    Assert.assertEquals(
        "$type:double lower half has expected filter clause",
        expectedDoubleQuery.clauses().get(0).occur(),
        resultDoubleQuery.clauses().get(0).occur());
    Assert.assertEquals(
        "$type:double field upper half has correct boolean clause type",
        expectedDoubleQuery.clauses().get(1).occur(),
        resultDoubleQuery.clauses().get(1).occur());

    BooleanQuery expectedDoubleLowerHalfQuery =
        (BooleanQuery) getClause(expectedDoubleQuery, 0).query();
    BooleanQuery expectedDoubleUpperHalfQuery =
        (BooleanQuery) getClause(expectedDoubleQuery, 1).query();

    BooleanQuery resultDoubleLowerHalfQuery =
        (BooleanQuery) getClause(resultDoubleQuery, 0).query();
    BooleanQuery resultDoubleUpperHalfQuery =
        (BooleanQuery) getClause(resultDoubleQuery, 1).query();

    Assert.assertEquals(
        "$type:double field lower half has correct filter clause",
        getClause(expectedDoubleLowerHalfQuery, 0),
        getClause(resultDoubleLowerHalfQuery, 0));
    Assert.assertEquals(
        "$type:double field upper half has correct filter clause",
        getClause(expectedDoubleUpperHalfQuery, 0),
        getClause(resultDoubleUpperHalfQuery, 0));

    Assert.assertEquals(
        "$type:double field lower half scoring clause has correct boolean type",
        expectedDoubleLowerHalfQuery.clauses().get(1).occur(),
        resultDoubleLowerHalfQuery.clauses().get(1).occur());
    Assert.assertEquals(
        "$type:double field upper half scoring clause has correct boolean type",
        expectedDoubleUpperHalfQuery.clauses().get(1).occur(),
        resultDoubleUpperHalfQuery.clauses().get(1).occur());

    FunctionScoreQuery expectedLowerScoredQuery =
        (FunctionScoreQuery) getClause(expectedDoubleLowerHalfQuery, 1).query();
    FunctionScoreQuery expectedUpperScoredQuery =
        (FunctionScoreQuery) getClause(expectedDoubleUpperHalfQuery, 1).query();
    FunctionScoreQuery resultLowerScoredQuery =
        (FunctionScoreQuery) getClause(resultDoubleLowerHalfQuery, 1).query();
    FunctionScoreQuery resultUpperScoredQuery =
        (FunctionScoreQuery) getClause(resultDoubleUpperHalfQuery, 1).query();

    Assert.assertEquals(
        "$type:double field lower half scoring function has correct query",
        expectedLowerScoredQuery.getWrappedQuery(),
        resultLowerScoredQuery.getWrappedQuery());
    Assert.assertEquals(
        "$type:double field upper half scoring function has correct query",
        expectedUpperScoredQuery.getWrappedQuery(),
        resultUpperScoredQuery.getWrappedQuery());

    Assert.assertEquals(
        "$type:double field lower half scoring function has correct values sources",
        expectedLowerScoredQuery.getSource().toString(),
        resultLowerScoredQuery.getSource().toString());

    Assert.assertEquals(
        "$type:double field upper half scoring function has correct values sources",
        expectedUpperScoredQuery.getSource().toString(),
        resultUpperScoredQuery.getSource().toString());
  }

  private static BooleanClause getClause(Query query, int idx) {
    return ((BooleanQuery) query).clauses().get(idx);
  }

  private static BooleanQuery expectedForLongOrigin(String path, long origin, double pivot)
      throws Exception {
    Query expectedLongQuery =
        (Math.abs(pivot) > Long.MAX_VALUE)
            ? new MatchNoDocsQuery(
                "pivot outside representable range for int64-indexed values: "
                    + "values of magnitude greater than MAX_LONG cannot be represented")
            : (Math.abs(pivot) < 1)
                ? new MatchNoDocsQuery(
                    "pivot outside representable range for int64-indexed values:"
                        + " values of magnitude less than one cannot be represented")
                : org.apache.lucene.document.LongField.newDistanceFeatureQuery(
                    String.format("$type:int64/%s", path),
                    1f,
                    origin,
                    Double.valueOf(pivot).longValue());

    Query expectedDoubleQuery =
        doubleQueryForTranslatedOrigin(
            reScoreForDoubleQuery(path, origin, pivot),
            path,
            LuceneDoubleConversionUtils.toIndexedLong(origin),
            LuceneDoubleConversionUtils.toLong(pivot));

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(expectedLongQuery, BooleanClause.Occur.SHOULD);
    builder.add(expectedDoubleQuery, BooleanClause.Occur.SHOULD);

    return builder.build();
  }

  private static BooleanQuery expectedForDoubleOrigin(String path, double origin, double pivot)
      throws Exception {
    Query expectedLongQuery =
        (Math.abs(pivot) > Long.MAX_VALUE)
            ? new MatchNoDocsQuery(
                "pivot outside representable range for int64-indexed values: "
                    + "values of magnitude greater than MAX_LONG cannot be represented")
            : (Math.abs(origin) > Long.MAX_VALUE)
                ? new MatchNoDocsQuery(
                    "origin outside representable range for int64-indexed values: "
                        + "values of magnitude greater than MAX_LONG cannot be represented")
                : (Math.abs(pivot) < 1)
                    ? new MatchNoDocsQuery(
                        "pivot outside representable range for int64-indexed values: "
                            + "values of magnitude less than one cannot be represented")
                    : org.apache.lucene.document.LongField.newDistanceFeatureQuery(
                        String.format("$type:int64/%s", path),
                        1f,
                        Double.valueOf(origin).longValue(),
                        Double.valueOf(pivot).longValue());

    Query expectedDoubleQuery =
        doubleQueryForTranslatedOrigin(
            reScoreForDoubleQuery(path, origin, pivot),
            path,
            LuceneDoubleConversionUtils.toLong(origin),
            LuceneDoubleConversionUtils.toLong(pivot));

    BooleanQuery.Builder builder = new BooleanQuery.Builder();
    builder.add(expectedLongQuery, BooleanClause.Occur.SHOULD);
    builder.add(expectedDoubleQuery, BooleanClause.Occur.SHOULD);

    return builder.build();
  }

  private static BooleanQuery doubleQueryForTranslatedOrigin(
      Function<Query, Query> rescoringFunction, String path, long origin, long pivot) {
    Query expectedDoubleNearQuery =
        rescoringFunction.apply(
            org.apache.lucene.document.LongField.newDistanceFeatureQuery(
                String.format("$type:double/%s", path), 1f, origin, pivot));
    Query expectedDoubleLowerHalfFilter =
        org.apache.lucene.document.LongPoint.newRangeQuery(
            String.format("$type:double/%s", path), Long.MIN_VALUE, origin);
    Query expectedDoubleUpperHalfFilter =
        org.apache.lucene.document.LongPoint.newRangeQuery(
            String.format("$type:double/%s", path), origin + 1L, Long.MAX_VALUE);

    return new BooleanQuery.Builder()
        .add(
            new BooleanQuery.Builder()
                .add(expectedDoubleLowerHalfFilter, BooleanClause.Occur.FILTER)
                .add(expectedDoubleNearQuery, BooleanClause.Occur.SHOULD)
                .build(),
            BooleanClause.Occur.SHOULD)
        .add(
            new BooleanQuery.Builder()
                .add(expectedDoubleUpperHalfFilter, BooleanClause.Occur.FILTER)
                .add(expectedDoubleNearQuery, BooleanClause.Occur.SHOULD)
                .build(),
            BooleanClause.Occur.SHOULD)
        .build();
  }

  private static Function<Query, Query> reScoreForDoubleQuery(
      String luceneFieldPath, double origin, double pivot) throws Exception {
    Expression expression = JavascriptCompiler.compile("pivot / (pivot + abs(origin - value))");

    Map<String, DoubleValuesSource> variables =
        Map.of(
            "value",
            DoubleValuesSource.fromField(luceneFieldPath, LuceneDoubleConversionUtils::fromLong),
            "pivot",
            DoubleValuesSource.constant(pivot),
            "origin",
            DoubleValuesSource.constant(origin));

    SimpleBindings bindings = new SimpleBindings();
    variables.forEach(bindings::add);
    return (Query query) ->
        new FunctionScoreQuery(query, expression.getDoubleValuesSource(bindings));
  }
}
