package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.BoostQuerySpecBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.QueryExplainInformationBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.TermQueryBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      BoostQuerySpecTest.DeserializationTest.class,
      BoostQuerySpecTest.SerializationTest.class,
    })
public class BoostQuerySpecTest {
  private static final QueryExplainInformation BOOSTED_QUERY =
      QueryExplainInformationBuilder.builder()
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(TermQueryBuilder.builder().path("q").value("whopper").build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "boost-query-deserialization";
    private static final BsonDeserializationTestSuite<BoostQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, BoostQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<BoostQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<BoostQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<BoostQuerySpec>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<BoostQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", BoostQuerySpecBuilder.builder().query(BOOSTED_QUERY).boost(2.5f).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "boost-query-serialization";
    private static final BsonSerializationTestSuite<BoostQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<BoostQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<BoostQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<BoostQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<BoostQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", BoostQuerySpecBuilder.builder().query(BOOSTED_QUERY).boost(2.5f).build());
    }
  }
}
