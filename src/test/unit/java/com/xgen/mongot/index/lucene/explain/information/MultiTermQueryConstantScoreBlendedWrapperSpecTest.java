package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.BooleanQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.MultiTermQueryConstantScoreWrapperBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.QueryExplainInformationBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.TermQueryBuilder;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      MultiTermQueryConstantScoreBlendedWrapperSpecTest.DeserializationTest.class,
      MultiTermQueryConstantScoreBlendedWrapperSpecTest.SerializationTest.class,
    })
public class MultiTermQueryConstantScoreBlendedWrapperSpecTest {
  private static final QueryExplainInformation TERM_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(TermQueryBuilder.builder().path("access").value("full").build())
          .build();

  private static final QueryExplainInformation BOOL_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .type(LuceneQuerySpecification.Type.BOOLEAN_QUERY)
          .args(
              BooleanQueryBuilder.builder()
                  .should(List.of(TERM_EXPLAIN_INFO, TERM_EXPLAIN_INFO))
                  .build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "multi-term-query-deserialization";
    private static final BsonDeserializationTestSuite<MultiTermQueryConstantScoreBlendedWrapperSpec>
        TEST_SUITE =
            fromDocument(
                ExplainInformationTestUtil.RESOURCES_PATH,
                SUITE_NAME,
                MultiTermQueryConstantScoreBlendedWrapperSpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<
            MultiTermQueryConstantScoreBlendedWrapperSpec>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<MultiTermQueryConstantScoreBlendedWrapperSpec>
            testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<
                MultiTermQueryConstantScoreBlendedWrapperSpec>>
        data() {
      return TEST_SUITE.withExamples(simple(), missingPath());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<
            MultiTermQueryConstantScoreBlendedWrapperSpec>
        simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          MultiTermQueryConstantScoreWrapperBuilder.builder()
              .path("access")
              .queries(List.of(BOOL_EXPLAIN_INFO))
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<
            MultiTermQueryConstantScoreBlendedWrapperSpec>
        missingPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-path",
          MultiTermQueryConstantScoreWrapperBuilder.builder()
              .queries(List.of(BOOL_EXPLAIN_INFO))
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "multi-term-query-serialization";
    private static final BsonSerializationTestSuite<MultiTermQueryConstantScoreBlendedWrapperSpec>
        TEST_SUITE = fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<MultiTermQueryConstantScoreBlendedWrapperSpec>
        testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<MultiTermQueryConstantScoreBlendedWrapperSpec>
            testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonSerializationTestSuite.TestSpec<MultiTermQueryConstantScoreBlendedWrapperSpec>>
        data() {
      return Arrays.asList(simple(), missingPath());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<
            MultiTermQueryConstantScoreBlendedWrapperSpec>
        simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          MultiTermQueryConstantScoreWrapperBuilder.builder()
              .path("access")
              .queries(List.of(BOOL_EXPLAIN_INFO))
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<
            MultiTermQueryConstantScoreBlendedWrapperSpec>
        missingPath() {
      return BsonSerializationTestSuite.TestSpec.create(
          "missing-path",
          MultiTermQueryConstantScoreWrapperBuilder.builder()
              .queries(List.of(BOOL_EXPLAIN_INFO))
              .build());
    }
  }
}
