package com.xgen.mongot.index.query;

import static com.xgen.testing.BsonDeserializationTestSuite.fromRootDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.query.counts.Count;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.OperatorQueryBuilder;
import com.xgen.testing.mongot.index.query.counts.CountBuilder;
import com.xgen.testing.mongot.index.query.highlights.UnresolvedHighlightBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      OperatorQueryTest.DeserializationTest.class,
      OperatorQueryTest.SerializationTest.class
    })
public class OperatorQueryTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "operator-query-deserialization";
    private static final BsonDeserializationTestSuite<Query> TEST_SUITE =
        fromRootDocument("src/test/unit/resources/index/query/", SUITE_NAME, SearchQuery::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Query> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Query> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Query>> data() {
      return TEST_SUITE.withExamples(
          simple(),
          explicitIndex(),
          blankIndex(),
          withHighlights(),
          withCount(),
          withReturnStoredSource(),
          withScoreDetails(),
          withTracking(),
          withReturnScope(),
          withSearchNodePreference());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .returnStoredSource(false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> explicitIndex() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit index",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .index("my-index")
              .returnStoredSource(false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> blankIndex() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "blank index",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .index(" ")
              .returnStoredSource(false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withHighlights() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with highlights",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .highlight(UnresolvedHighlightBuilder.builder().path("title").build())
              .returnStoredSource(false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withCount() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with count",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .count(CountBuilder.builder().type(Count.Type.TOTAL).threshold(5000).build())
              .returnStoredSource(false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withReturnStoredSource() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with explicit returnStoredSource",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .returnStoredSource(true)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withReturnScope() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with returnScope and explicit true returnStoredSource",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("movies.title").query("godfather").build())
              .returnStoredSource(true)
              .returnScope(new ReturnScope(FieldPath.parse("movies")))
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withScoreDetails() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with explicit scoreDetails",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .returnStoredSource(false)
              .scoreDetails(true)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withTracking() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with tracking",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .returnStoredSource(false)
              .tracking(new Tracking("foo"))
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Query> withSearchNodePreference() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with searchNodePreference",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .returnStoredSource(false)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "operator-query-serialization";
    private static final BsonSerializationTestSuite<Query> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/query/", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<Query> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Query> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Query>> data() {
      return List.of(operatorQuery());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Query> operatorQuery() {
      return BsonSerializationTestSuite.TestSpec.create(
          "operator-query",
          OperatorQueryBuilder.builder()
              .operator(OperatorBuilder.text().path("title").query("godfather").build())
              .index("my-index")
              .count(CountBuilder.builder().type(Count.Type.TOTAL).build())
              .highlight(UnresolvedHighlightBuilder.builder().path("my-path").build())
              .returnStoredSource(true)
              .concurrent(true)
              .scoreDetails(true)
              .tracking(new Tracking("bar"))
              .returnScope(new ReturnScope(FieldPath.parse("my-scope")))
              .build());
    }
  }
}
