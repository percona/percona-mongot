package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.ConstantExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.Log1PExpressionBuilder;
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
      Log1PExpressionTest.DeserializationTest.class,
      Log1PExpressionTest.SerializationTest.class,
    })
public class Log1PExpressionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "log1p-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<Log1PExpression> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, Log1PExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Log1PExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<Log1PExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Log1PExpression>> data() {
      return TEST_SUITE.withExamples(
          log1PPathAsString(),
          log1PPathAsObjectDefaultUndefined(),
          log1PPathWithUndefined(),
          log1PLog1PPath(),
          logConstant(),
          logScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression> log1PPathAsString() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of path as string",
          Log1PExpressionBuilder.builder()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression>
        log1PPathAsObjectDefaultUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of path as object with default undefined",
          Log1PExpressionBuilder.builder()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression>
        log1PPathWithUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of path with undefined",
          Log1PExpressionBuilder.builder()
              .expression(
                  PathExpressionBuilder.builder().value("popularity").undefined(532.31).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression> log1PLog1PPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of log1p of path",
          Log1PExpressionBuilder.builder()
              .expression(
                  Log1PExpressionBuilder.builder()
                      .expression(
                          PathExpressionBuilder.builder()
                              .value("popularity")
                              .undefined(2905.33)
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression> logConstant() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of constant",
          Log1PExpressionBuilder.builder()
              .expression(ConstantExpressionBuilder.builder().constant(340).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Log1PExpression> logScore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of score",
          Log1PExpressionBuilder.builder()
              .expression(ScoreExpressionBuilder.builder().build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "log1p-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    public static final BsonSerializationTestSuite<Log1PExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<Log1PExpression> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Log1PExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Log1PExpression>> data() {
      return List.of(log1PPathWithUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Log1PExpression> log1PPathWithUndefined() {
      return BsonSerializationTestSuite.TestSpec.create(
          "log1p of path with undefined",
          Log1PExpressionBuilder.builder()
              .expression(
                  PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .build());
    }
  }
}
