package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.ConstantExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.LogExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.ScoreExpressionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      LogExpressionTest.DeserializationTest.class,
      LogExpressionTest.SerializationTest.class,
    })
public class LogExpressionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "log-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<LogExpression> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, LogExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<LogExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<LogExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<LogExpression>> data() {
      return TEST_SUITE.withExamples(
          logPathAsString(),
          logPathAsObjectDefaultUndefined(),
          logPathWithUndefined(),
          logLogPath(),
          logConstant(),
          logScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression> logPathAsString() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of path as string",
          LogExpressionBuilder.builder()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression>
        logPathAsObjectDefaultUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of path as object with default undefined",
          LogExpressionBuilder.builder()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression> logPathWithUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of path with undefined",
          LogExpressionBuilder.builder()
              .expression(
                  PathExpressionBuilder.builder().value("popularity").undefined(532.31).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression> logLogPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of log of path",
          LogExpressionBuilder.builder()
              .expression(
                  LogExpressionBuilder.builder()
                      .expression(
                          PathExpressionBuilder.builder()
                              .value("popularity")
                              .undefined(2905.33)
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression> logConstant() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of constant",
          LogExpressionBuilder.builder()
              .expression(ConstantExpressionBuilder.builder().constant(340).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LogExpression> logScore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of score",
          LogExpressionBuilder.builder()
              .expression(ScoreExpressionBuilder.builder().build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "log-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    public static final BsonSerializationTestSuite<LogExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<LogExpression> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<LogExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<LogExpression>> data() {
      return List.of(logPathWithUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<LogExpression> logPathWithUndefined() {
      return BsonSerializationTestSuite.TestSpec.create(
          "log of path with undefined",
          LogExpressionBuilder.builder()
              .expression(
                  PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .build());
    }
  }
}
