package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.PhraseQueryBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      PhraseQuerySpecTest.DeserializationTest.class,
      PhraseQuerySpecTest.SerializationTest.class,
    })
public class PhraseQuerySpecTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "phrase-query-deserialization";
    private static final BsonDeserializationTestSuite<PhraseQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, PhraseQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<PhraseQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<PhraseQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<PhraseQuerySpec>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<PhraseQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", PhraseQueryBuilder.builder().path("a").query("hello").slop(1).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "phrase-query-serialization";
    private static final BsonSerializationTestSuite<PhraseQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<PhraseQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<PhraseQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<PhraseQuerySpec>> data() {
      return Arrays.asList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<PhraseQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", PhraseQueryBuilder.builder().path("a").query("hello").slop(1).build());
    }
  }
}
