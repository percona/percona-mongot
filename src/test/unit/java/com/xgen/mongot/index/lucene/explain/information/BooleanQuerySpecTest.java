package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.BooleanQueryBuilder;
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
      BooleanQuerySpecTest.DeserializationTest.class,
      BooleanQuerySpecTest.SerializationTest.class,
    })
public class BooleanQuerySpecTest {
  private static final QueryExplainInformation TEXT_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .path("must[0]")
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(TermQueryBuilder.builder().path("q").value("whopper").build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "bool-query-deserialization";
    private static final BsonDeserializationTestSuite<BooleanQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, BooleanQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<BooleanQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<BooleanQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<BooleanQuerySpec>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<BooleanQuerySpec> simple() {
      System.err.println(
          BooleanQueryBuilder.builder()
              .must(List.of(TEXT_EXPLAIN_INFO))
              .minimumShouldMatch(1)
              .build()
              .toBson()
              .toJson());
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          BooleanQueryBuilder.builder()
              .must(List.of(TEXT_EXPLAIN_INFO))
              .minimumShouldMatch(1)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "bool-query-serialization";
    private static final BsonSerializationTestSuite<BooleanQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<BooleanQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<BooleanQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<BooleanQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<BooleanQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          BooleanQueryBuilder.builder()
              .must(List.of(TEXT_EXPLAIN_INFO))
              .minimumShouldMatch(1)
              .build());
    }
  }
}
