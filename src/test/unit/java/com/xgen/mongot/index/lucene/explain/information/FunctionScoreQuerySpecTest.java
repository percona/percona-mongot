package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.query.points.DoublePoint;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.FunctionScoreQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.LongDistanceFeatureQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.QueryExplainInformationBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      FunctionScoreQuerySpecTest.DeserializationTest.class,
      FunctionScoreQuerySpecTest.SerializationTest.class,
    })
public class FunctionScoreQuerySpecTest {
  private static final LongDistanceFeatureQuerySpec LONG_DIST_QUERY =
      LongDistanceFeatureQueryBuilder.builder()
          .origin(new DoublePoint(8.0))
          .pivotDistance(new DoublePoint(2.0))
          .representation(Representation.DOUBLE)
          .path("accommodates")
          .build();

  private static final QueryExplainInformation LONG_DIST_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .type(LuceneQuerySpecification.Type.LONG_DISTANCE_FEATURE_QUERY)
          .args(LONG_DIST_QUERY)
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "function-score-query-deserialization";
    private static final BsonDeserializationTestSuite<FunctionScoreQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            FunctionScoreQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<FunctionScoreQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<FunctionScoreQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<FunctionScoreQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScoreQuerySpec> simple() {

      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          FunctionScoreQueryBuilder.builder()
              .scoreFunction("expr(pivot / (pivot + abs(origin - value)))")
              .query(LONG_DIST_EXPLAIN_INFO)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "function-score-query-serialization";
    private static final BsonSerializationTestSuite<FunctionScoreQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<FunctionScoreQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<FunctionScoreQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<FunctionScoreQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<FunctionScoreQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          FunctionScoreQueryBuilder.builder()
              .scoreFunction("expr(pivot / (pivot + abs(origin - value)))")
              .query(LONG_DIST_EXPLAIN_INFO)
              .build());
    }
  }
}
