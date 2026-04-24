package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.DisjunctionMaxQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
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
      DisjunctionMaxQuerySpecTest.DeserializationTest.class,
      DisjunctionMaxQuerySpecTest.SerializationTest.class,
    })
public class DisjunctionMaxQuerySpecTest {
  private static final QueryExplainInformation TERM_EXPLAIN_INFO_1 =
      QueryExplainInformationBuilder.builder()
          .path("disjuncts[0]")
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(TermQueryBuilder.builder().path("field1").value("value1").build())
          .build();

  private static final QueryExplainInformation TERM_EXPLAIN_INFO_2 =
      QueryExplainInformationBuilder.builder()
          .path("disjuncts[1]")
          .type(LuceneQuerySpecification.Type.TERM_QUERY)
          .args(TermQueryBuilder.builder().path("field2").value("value2").build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "dismax-query-deserialization";
    private static final BsonDeserializationTestSuite<DisjunctionMaxQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            DisjunctionMaxQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<DisjunctionMaxQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<DisjunctionMaxQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<DisjunctionMaxQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple(), withTieBreakerAndMultipleDisjuncts());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<DisjunctionMaxQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          DisjunctionMaxQueryBuilder.builder()
              .disjuncts(List.of(TERM_EXPLAIN_INFO_1))
              .tieBreaker(0.5f)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<DisjunctionMaxQuerySpec>
        withTieBreakerAndMultipleDisjuncts() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "with-multiple-disjuncts",
          DisjunctionMaxQueryBuilder.builder()
              .disjuncts(List.of(TERM_EXPLAIN_INFO_1, TERM_EXPLAIN_INFO_2))
              .tieBreaker(0.5f)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "dismax-query-serialization";
    private static final BsonSerializationTestSuite<DisjunctionMaxQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<DisjunctionMaxQuerySpec> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<DisjunctionMaxQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<DisjunctionMaxQuerySpec>> data() {
      return Arrays.asList(simple(), withTieBreakerAndMultipleDisjuncts());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<DisjunctionMaxQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          DisjunctionMaxQueryBuilder.builder()
              .disjuncts(List.of(TERM_EXPLAIN_INFO_1))
              .tieBreaker(0.5f)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<DisjunctionMaxQuerySpec>
        withTieBreakerAndMultipleDisjuncts() {
      return BsonSerializationTestSuite.TestSpec.create(
          "with-multiple-disjuncts",
          DisjunctionMaxQueryBuilder.builder()
              .disjuncts(List.of(TERM_EXPLAIN_INFO_1, TERM_EXPLAIN_INFO_2))
              .tieBreaker(0.5f)
              .build());
    }
  }
}
