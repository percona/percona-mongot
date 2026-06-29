package com.xgen.mongot.index.query.operators;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      KnnBetaOperatorTest.DeserializationTest.class,
      KnnBetaOperatorTest.SerializationTest.class,
    })
public class KnnBetaOperatorTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "knnBeta-deserialization";
    private static final BsonDeserializationTestSuite<KnnBetaOperator> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, KnnBetaOperator::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<KnnBetaOperator> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<KnnBetaOperator> testSpec) {
      this.testSpec = testSpec;
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<KnnBetaOperator>> data() {
      return TEST_SUITE.withExamples(simple(), filter(), multiplePaths());
    }

    private static BsonDeserializationTestSuite.ValidSpec<KnnBetaOperator> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          OperatorBuilder.knnBeta()
              .path("description")
              .vector(List.of(new BsonInt32(2), new BsonInt32(2), new BsonInt32(2)))
              .k(10)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<KnnBetaOperator> filter() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "filter",
          OperatorBuilder.knnBeta()
              .path("description")
              .vector(List.of(new BsonInt32(5), new BsonInt32(5), new BsonInt32(5)))
              .filter(OperatorBuilder.equals().path("flag").value(true).build())
              .k(10)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<KnnBetaOperator> multiplePaths() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "multiple paths",
          OperatorBuilder.knnBeta()
              .path("description")
              .path("title")
              .vector(List.of(new BsonInt32(15), new BsonInt32(15)))
              .k(10)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "knnBeta-serialization";
    public static final BsonSerializationTestSuite<KnnBetaOperator> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(DefinitionTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<KnnBetaOperator> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<KnnBetaOperator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<KnnBetaOperator>> data() {
      return List.of(simple(), filter(), multiplePaths());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<KnnBetaOperator> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          OperatorBuilder.knnBeta()
              .path("description")
              .vector(List.of(new BsonInt32(2), new BsonInt32(2), new BsonInt32(2)))
              .k(10)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<KnnBetaOperator> filter() {
      return BsonSerializationTestSuite.TestSpec.create(
          "filter",
          OperatorBuilder.knnBeta()
              .path("description")
              .vector(List.of(new BsonInt32(5), new BsonInt32(5), new BsonInt32(5)))
              .filter(OperatorBuilder.equals().path("flag").value(true).build())
              .k(10)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<KnnBetaOperator> multiplePaths() {
      return BsonSerializationTestSuite.TestSpec.create(
          "multiple paths",
          OperatorBuilder.knnBeta()
              .path("description")
              .path("title")
              .vector(List.of(new BsonInt32(15), new BsonInt32(15)))
              .k(10)
              .build());
    }
  }
}
