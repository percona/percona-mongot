package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.CollectStatsBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.FacetStatsBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.SortStatsBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      CollectorExplainInformationTest.DeserializationTest.class,
      CollectorExplainInformationTest.SerializationTest.class,
    })
public class CollectorExplainInformationTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "collect-stats-deserialization";

    private static final BsonDeserializationTestSuite<CollectorExplainInformation> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            CollectorExplainInformation::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<CollectorExplainInformation>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<CollectorExplainInformation> testSpec) {
      this.testSpec = testSpec;
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<CollectorExplainInformation>>
        data() {
      return TEST_SUITE.withExamples(full());
    }

    private static BsonDeserializationTestSuite.ValidSpec<CollectorExplainInformation> full() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "full",
          CollectStatsBuilder.builder()
              .allCollectorStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
              .sortStats(
                  SortStatsBuilder.builder()
                      .profilingIteratorExecutionArea(
                          ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .sortExecutionArea(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .fieldInfos(
                          Map.of(
                              "foo", List.of(FieldName.TypeField.DATE, FieldName.TypeField.TOKEN)))
                      .build())
              .facetStats(
                  FacetStatsBuilder.builder()
                      .collectorStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .createFacetCountStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .stringFacetCardinalityInfo(
                          Map.of(
                              "foo",
                              new FacetStats.CardinalityInfo(5, 10),
                              "bar",
                              new FacetStats.CardinalityInfo(3, 6)))
                      .build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "collect-stats-serialization";

    private static final BsonSerializationTestSuite<CollectorExplainInformation> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<CollectorExplainInformation> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<CollectorExplainInformation> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<CollectorExplainInformation>>
        data() {
      return Arrays.asList(full());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<CollectorExplainInformation> full() {
      return BsonSerializationTestSuite.TestSpec.create(
          "full",
          CollectStatsBuilder.builder()
              .allCollectorStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
              .sortStats(
                  SortStatsBuilder.builder()
                      .profilingIteratorExecutionArea(
                          ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .sortExecutionArea(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .fieldInfos(
                          Map.of(
                              "foo", List.of(FieldName.TypeField.DATE, FieldName.TypeField.TOKEN)))
                      .build())
              .facetStats(
                  FacetStatsBuilder.builder()
                      .collectorStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .createFacetCountStats(ExplainInformationTestUtil.QUERY_EXECUTION_AREA)
                      .stringFacetCardinalityInfo(
                          Map.of(
                              "foo",
                              new FacetStats.CardinalityInfo(5, 10),
                              "bar",
                              new FacetStats.CardinalityInfo(3, 6)))
                      .build())
              .build());
    }
  }
}
