package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.BooleanQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExactVectorSearchQueryBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.QueryExplainInformationBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.TermQueryBuilder;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ExactVectorSearchQuerySpecTest.DeserializationTest.class,
      ExactVectorSearchQuerySpecTest.SerializationTest.class,
    })
public class ExactVectorSearchQuerySpecTest {

  private static final QueryExplainInformation FILTER_EXPLAIN_INFO =
      QueryExplainInformationBuilder.builder()
          .path("a")
          .type(LuceneQuerySpecification.Type.BOOLEAN_QUERY)
          .args(
              BooleanQueryBuilder.builder()
                  .must(
                      List.of(
                          QueryExplainInformationBuilder.builder()
                              .path("must[0]")
                              .type(LuceneQuerySpecification.Type.TERM_QUERY)
                              .args(TermQueryBuilder.builder().path("q").value("whopper").build())
                              .build()))
                  .build())
          .build();

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "exact-vector-search-query-deserialization";
    private static final BsonDeserializationTestSuite<ExactVectorSearchQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            ExactVectorSearchQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<ExactVectorSearchQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<ExactVectorSearchQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<ExactVectorSearchQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple(), filtered());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<ExactVectorSearchQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          ExactVectorSearchQueryBuilder.builder()
              .path("a")
              .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ExactVectorSearchQuerySpec> filtered() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "filtered",
          ExactVectorSearchQueryBuilder.builder()
              .path("a")
              .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
              .filter(FILTER_EXPLAIN_INFO)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "exact-vector-search-query-serialization";
    private static final BsonSerializationTestSuite<ExactVectorSearchQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ExactVectorSearchQuerySpec> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<ExactVectorSearchQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ExactVectorSearchQuerySpec>> data() {
      return Arrays.asList(simple(), filtered());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ExactVectorSearchQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          ExactVectorSearchQueryBuilder.builder()
              .path("a")
              .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<ExactVectorSearchQuerySpec> filtered() {
      return BsonSerializationTestSuite.TestSpec.create(
          "filtered",
          ExactVectorSearchQueryBuilder.builder()
              .path("a")
              .similarityFunction(VectorSimilarityFunction.EUCLIDEAN)
              .filter(FILTER_EXPLAIN_INFO)
              .build());
    }
  }
}
