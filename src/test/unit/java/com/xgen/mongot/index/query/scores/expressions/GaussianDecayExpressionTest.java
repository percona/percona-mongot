package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.GaussianDecayExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      GaussianDecayExpressionTest.DeserializationTest.class,
      GaussianDecayExpressionTest.SerializationTest.class,
    })
public class GaussianDecayExpressionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "gaussian-decay-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<GaussianDecayExpression> TEST_SUITE =
        fromValue(RESOURCES_PATH, SUITE_NAME, GaussianDecayExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<GaussianDecayExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<GaussianDecayExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<GaussianDecayExpression>>
        data() {
      return TEST_SUITE.withExamples(pathOriginScaleOffsetDecay(), pathOriginScale());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<GaussianDecayExpression>
        pathOriginScale() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path origin scale",
          GaussianDecayExpressionBuilder.builder()
              .path(PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .origin(100d)
              .scale(5d)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<GaussianDecayExpression>
        pathOriginScaleOffsetDecay() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path origin scale offset decay",
          GaussianDecayExpressionBuilder.builder()
              .path(PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .origin(100d)
              .scale(5d)
              .offset(0d)
              .decay(0.5d)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "gaussian-decay-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonSerializationTestSuite<GaussianDecayExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<GaussianDecayExpression> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<GaussianDecayExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<GaussianDecayExpression>> data() {
      return List.of(gaussianPath());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<GaussianDecayExpression> gaussianPath() {
      return BsonSerializationTestSuite.TestSpec.create(
          "gaussian path",
          GaussianDecayExpressionBuilder.builder()
              .path(PathExpressionBuilder.builder().value("rating").undefined(2132d).build())
              .origin(100d)
              .scale(5d)
              .build());
    }
  }
}
