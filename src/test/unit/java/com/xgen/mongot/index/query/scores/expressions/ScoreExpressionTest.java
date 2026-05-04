package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.ScoreExpressionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ScoreExpressionTest.DeserializationTest.class,
      ScoreExpressionTest.SerializationTest.class,
    })
public class ScoreExpressionTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "score-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<ScoreExpression> TEST_SUITE =
        fromValue(RESOURCES_PATH, SUITE_NAME, ScoreExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<ScoreExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<ScoreExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<ScoreExpression>> data() {
      return TEST_SUITE.withExamples(relevanceScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<ScoreExpression> relevanceScore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "relevance", ScoreExpressionBuilder.builder().build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "score-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    public static final BsonSerializationTestSuite<ScoreExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ScoreExpression> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<ScoreExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ScoreExpression>> data() {
      return List.of(relevanceScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ScoreExpression> relevanceScore() {
      return BsonSerializationTestSuite.TestSpec.create(
          "relevance", ScoreExpressionBuilder.builder().build());
    }
  }
}
