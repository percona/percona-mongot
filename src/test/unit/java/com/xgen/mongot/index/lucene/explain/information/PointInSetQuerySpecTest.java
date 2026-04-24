package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.PointInSetQueryBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      PointInSetQuerySpecTest.SerializationTest.class,
      PointInSetQuerySpecTest.DeserializationTest.class
    })
public class PointInSetQuerySpecTest {

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "point-in-set-query-serialization";
    private static final BsonSerializationTestSuite<PhraseQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<PhraseQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<PhraseQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<PointInSetQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<PointInSetQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          PointInSetQueryBuilder.builder()
              .path("a")
              .points(List.of(9223372036854775801L, 9223372036854775802L, 9223372036854775803L))
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "point-in-set-query-deserialization";
    private static final BsonDeserializationTestSuite<PointInSetQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, PointInSetQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<PointInSetQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<PointInSetQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<PointInSetQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<PointInSetQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          PointInSetQueryBuilder.builder()
              .path("a")
              .points(List.of(9223372036854775801L, 9223372036854775802L, 9223372036854775803L))
              .build());
    }
  }
}
