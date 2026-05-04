package com.xgen.mongot.index.query.scores.expressions;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.expressions.AddExpressionBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.ConstantExpressionBuilder;
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
      AddExpressionTest.DeserializationTest.class,
      AddExpressionTest.SerializationTest.class,
    })
public class AddExpressionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "add-multiply-expression-deserialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    private static final BsonDeserializationTestSuite<AddExpression> TEST_SUITE =
        fromValue(RESOURCES_PATH, SUITE_NAME, AddExpression::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<AddExpression> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<AddExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<AddExpression>> data() {
      return TEST_SUITE.withExamples(addPathScoreConstant());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<AddExpression> addPathScoreConstant() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path score constant",
          AddExpressionBuilder.builder()
              .arg(PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .arg(ConstantExpressionBuilder.builder().constant(8987.31).build())
              .arg(ScoreExpressionBuilder.builder().build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "add-expression-serialization";
    private static final String RESOURCES_PATH =
        "src/test/unit/resources/index/query/scores/expressions/";
    public static final BsonSerializationTestSuite<AddExpression> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<AddExpression> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<AddExpression> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<AddExpression>> data() {
      return List.of(addPathScoreConstant());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<AddExpression> addPathScoreConstant() {
      return BsonSerializationTestSuite.TestSpec.create(
          "add path score constant",
          AddExpressionBuilder.builder()
              .arg(PathExpressionBuilder.builder().value("rating").undefined(6043.28).build())
              .arg(ConstantExpressionBuilder.builder().constant(8987.31).build())
              .arg(ScoreExpressionBuilder.builder().build())
              .build());
    }
  }
}
