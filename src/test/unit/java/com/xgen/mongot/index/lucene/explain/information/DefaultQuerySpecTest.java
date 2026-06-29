package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.DefaultQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      DefaultQuerySpecTest.DeserializationTest.class,
      DefaultQuerySpecTest.SerializationTest.class,
    })
public class DefaultQuerySpecTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "default-query-deserialization";
    private static final BsonDeserializationTestSuite<DefaultQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, DefaultQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<DefaultQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<DefaultQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<DefaultQuerySpec>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<DefaultQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", DefaultQueryBuilder.builder().queryType("unknownQuery").build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "default-query-serialization";
    private static final BsonSerializationTestSuite<DefaultQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<DefaultQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<DefaultQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<DefaultQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<DefaultQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", DefaultQueryBuilder.builder().queryType("unknownQuery").build());
    }
  }
}
