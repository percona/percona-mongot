package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      PathExpressionTest.DeserializationTest.class,
      PathExpressionTest.SerializationTest.class,
    })
public class PathExpressionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "path-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<PathExpression> TEST_SUITE =
        fromValue(RESOURCES_PATH, SUITE_NAME, PathExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<PathExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<PathExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<PathExpression>> data() {
      return TEST_SUITE.withExamples(
          pathAsString(),
          pathAsObjectDefaultUndefined(),
          pathAsObjectWithPositiveUndefined(),
          pathAsObjectWithZeroUndefined(),
          pathAsObjectWithNegativeUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathExpression> pathAsString() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as string", PathExpressionBuilder.builder().value("popularity").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathExpression>
        pathAsObjectDefaultUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as object with default undefined",
          PathExpressionBuilder.builder().value("popularity").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathExpression>
        pathAsObjectWithPositiveUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as object with positive undefined",
          PathExpressionBuilder.builder().value("popularity").undefined(5.431d).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathExpression>
        pathAsObjectWithZeroUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as object with zero undefined",
          PathExpressionBuilder.builder().value("popularity").undefined(0.0d).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathExpression>
        pathAsObjectWithNegativeUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path as object with negative undefined",
          PathExpressionBuilder.builder().value("popularity").undefined(-7644.813d).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "path-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    public static final BsonSerializationTestSuite<PathExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<PathExpression> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<PathExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<PathExpression>> data() {
      return List.of(pathAsObjectWithUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<PathExpression> pathAsObjectWithUndefined() {
      return BsonSerializationTestSuite.TestSpec.create(
          "as-object-with-undefined",
          PathExpressionBuilder.builder().value("rating").undefined(6043.28).build());
    }
  }
}
