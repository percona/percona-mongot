package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ConstantScoreQueryBuilder;
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
      ConstantScoreQuerySpecTest.DeserializationTest.class,
      ConstantScoreQuerySpecTest.SerializationTest.class,
    })
public class ConstantScoreQuerySpecTest {
  private static final QueryExplainInformation TERM_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(
              TermQueryBuilder.builder().path("host.host_identity_verified").value("true").build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "constant-score-query-deserialization";
    private static final BsonDeserializationTestSuite<ConstantScoreQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            ConstantScoreQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<ConstantScoreQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<ConstantScoreQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<ConstantScoreQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<ConstantScoreQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", ConstantScoreQueryBuilder.builder().query(TERM_EXPLAIN_INFO).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "constant-score-query-serialization";
    private static final BsonSerializationTestSuite<ConstantScoreQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ConstantScoreQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<ConstantScoreQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ConstantScoreQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ConstantScoreQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", ConstantScoreQueryBuilder.builder().query(TERM_EXPLAIN_INFO).build());
    }
  }
}
