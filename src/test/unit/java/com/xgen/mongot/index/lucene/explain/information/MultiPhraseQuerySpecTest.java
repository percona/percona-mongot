package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      MultiPhraseQuerySpecTest.DeserializationTest.class,
      MultiPhraseQuerySpecTest.SerializationTest.class,
    })
public class MultiPhraseQuerySpecTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "multi-phrase-query-deserialization";
    private static final BsonDeserializationTestSuite<MultiPhraseQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, MultiPhraseQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<MultiPhraseQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<MultiPhraseQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<MultiPhraseQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<MultiPhraseQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", new MultiPhraseQuerySpec(FieldPath.parse("a"), "hello", 1));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "multi-phrase-query-serialization";
    private static final BsonSerializationTestSuite<MultiPhraseQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<MultiPhraseQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<MultiPhraseQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<MultiPhraseQuerySpec>> data() {
      return Arrays.asList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<MultiPhraseQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", new MultiPhraseQuerySpec(FieldPath.parse("a"), "hello", 1));
    }
  }
}
