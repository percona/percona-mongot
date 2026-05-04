package com.xgen.mongot.index.query.scores;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.ScoreBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.AddExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.ConstantExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.GaussianDecayExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.Log1PExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.LogExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.MultiplyExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.ScoreExpressionBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      FunctionScoreTest.DeserializationTest.class,
      FunctionScoreTest.SerializationTest.class,
    })
public class FunctionScoreTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "function-score-deserialization";
    private static final BsonDeserializationTestSuite<FunctionScore> TEST_SUITE =
        fromDocument(ScoreTests.RESOURCES_PATH, SUITE_NAME, FunctionScore::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<FunctionScore> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<FunctionScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<FunctionScore>> data() {
      return TEST_SUITE.withExamples(
          pathShorthand(),
          pathAsObjectNoUndefined(),
          pathWithUndefined(),
          negativeConstant(),
          positiveConstant(),
          scoreExpression(),
          logPathWithUndefined(),
          log1PLogPath(),
          addConstantScorePath(),
          multiplyConstantScorePath(),
          gaussianDecayPath(),
          allExpressionsWithNesting());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> pathShorthand() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path shorthand",
          ScoreBuilder.function()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> pathAsObjectNoUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as object no undefined",
          ScoreBuilder.function()
              .expression(PathExpressionBuilder.builder().value("popularity").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> pathWithUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path with undefined",
          ScoreBuilder.function()
              .expression(
                  PathExpressionBuilder.builder().value("popularity").undefined(3.5d).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> negativeConstant() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "negative constant expression",
          ScoreBuilder.function()
              .expression(ConstantExpressionBuilder.builder().constant(-7857.43).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> positiveConstant() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "positive constant expression",
          ScoreBuilder.function()
              .expression(ConstantExpressionBuilder.builder().constant(2389.473).build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> scoreExpression() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "score expression",
          ScoreBuilder.function().expression(ScoreExpressionBuilder.builder().build()).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> logPathWithUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log of path with undefined",
          ScoreBuilder.function()
              .expression(
                  LogExpressionBuilder.builder()
                      .expression(
                          PathExpressionBuilder.builder()
                              .value("popularity")
                              .undefined(532.31)
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> log1PLogPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "log1p of log of path",
          ScoreBuilder.function()
              .expression(
                  Log1PExpressionBuilder.builder()
                      .expression(
                          LogExpressionBuilder.builder()
                              .expression(
                                  PathExpressionBuilder.builder()
                                      .value("popularity")
                                      .undefined(532.31)
                                      .build())
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> addConstantScorePath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "add constant, score, and path",
          ScoreBuilder.function()
              .expression(
                  AddExpressionBuilder.builder()
                      .arg(ConstantExpressionBuilder.builder().constant(349587.43).build())
                      .arg(ScoreExpressionBuilder.builder().build())
                      .arg(
                          PathExpressionBuilder.builder()
                              .value("nominations")
                              .undefined(23d)
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore>
        multiplyConstantScorePath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "multiply constant, score, and path",
          ScoreBuilder.function()
              .expression(
                  MultiplyExpressionBuilder.builder()
                      .arg(ConstantExpressionBuilder.builder().constant(349587.43).build())
                      .arg(ScoreExpressionBuilder.builder().build())
                      .arg(
                          PathExpressionBuilder.builder()
                              .value("nominations")
                              .undefined(23d)
                              .build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore> gaussianDecayPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "gaussian decay expression",
          ScoreBuilder.function()
              .expression(
                  GaussianDecayExpressionBuilder.builder()
                      .path(PathExpressionBuilder.builder().value("rating").undefined(0d).build())
                      .origin(100d)
                      .scale(5d)
                      .offset(0d)
                      .decay(0.5d)
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<FunctionScore>
        allExpressionsWithNesting() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "all expressions with nesting",
          ScoreBuilder.function()
              .expression(
                  AddExpressionBuilder.builder()
                      .arg(
                          MultiplyExpressionBuilder.builder()
                              .arg(ConstantExpressionBuilder.builder().constant(349587.43).build())
                              .arg(ScoreExpressionBuilder.builder().build())
                              .build())
                      .arg(
                          LogExpressionBuilder.builder()
                              .expression(
                                  AddExpressionBuilder.builder()
                                      .arg(
                                          PathExpressionBuilder.builder()
                                              .value("nominations")
                                              .undefined(23d)
                                              .build())
                                      .arg(
                                          Log1PExpressionBuilder.builder()
                                              .expression(
                                                  PathExpressionBuilder.builder()
                                                      .value("rating")
                                                      .build())
                                              .build())
                                      .arg(
                                          ConstantExpressionBuilder.builder()
                                              .constant(-439.8)
                                              .build())
                                      .arg(
                                          GaussianDecayExpressionBuilder.builder()
                                              .path(
                                                  PathExpressionBuilder.builder()
                                                      .value("rating")
                                                      .build())
                                              .origin(100d)
                                              .scale(5d)
                                              .offset(0d)
                                              .decay(0.5d)
                                              .build())
                                      .build())
                              .build())
                      .build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "function-score-serialization";
    private static final String RESOURCES_PATH = "src/test/unit/resources/index/query/scores/";
    public static final BsonSerializationTestSuite<FunctionScore> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<FunctionScore> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<FunctionScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<FunctionScore>> data() {
      return Arrays.asList(allExpressionsWithNesting());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<FunctionScore> allExpressionsWithNesting() {
      return BsonSerializationTestSuite.TestSpec.create(
          "all expressions with nesting",
          ScoreBuilder.function()
              .expression(
                  AddExpressionBuilder.builder()
                      .arg(
                          MultiplyExpressionBuilder.builder()
                              .arg(ConstantExpressionBuilder.builder().constant(349587.43).build())
                              .arg(ScoreExpressionBuilder.builder().build())
                              .build())
                      .arg(
                          LogExpressionBuilder.builder()
                              .expression(
                                  AddExpressionBuilder.builder()
                                      .arg(
                                          PathExpressionBuilder.builder()
                                              .value("nominations")
                                              .undefined(23d)
                                              .build())
                                      .arg(
                                          Log1PExpressionBuilder.builder()
                                              .expression(
                                                  PathExpressionBuilder.builder()
                                                      .value("rating")
                                                      .build())
                                              .build())
                                      .arg(
                                          ConstantExpressionBuilder.builder()
                                              .constant(-439.8)
                                              .build())
                                      .arg(
                                          GaussianDecayExpressionBuilder.builder()
                                              .path(
                                                  PathExpressionBuilder.builder()
                                                      .value("rating")
                                                      .build())
                                              .origin(100d)
                                              .scale(5d)
                                              .offset(0d)
                                              .decay(0.5d)
                                              .build())
                                      .build())
                              .build())
                      .build())
              .build());
    }
  }
}
