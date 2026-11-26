package com.xgen.mongot.index.query.collectors;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.mongot.index.query.collectors.CollectorBuilder;
import com.xgen.testing.mongot.index.query.collectors.MetricDefinitionBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetricsCollectorTest {
  private static final String SUITE_NAME = "metrics";
  private static final BsonDeserializationTestSuite<MetricsCollector> TEST_SUITE =
      fromDocument(
          "src/test/unit/resources/index/query/collectors/",
          SUITE_NAME,
          MetricsCollector::fromBson);

  private final BsonDeserializationTestSuite.TestSpecWrapper<MetricsCollector> testSpec;

  public MetricsCollectorTest(
      BsonDeserializationTestSuite.TestSpecWrapper<MetricsCollector> testSpec) {
    this.testSpec = testSpec;
  }

  /** Test data. */
  @Parameterized.Parameters(name = "{0}")
  public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<MetricsCollector>> data() {
    return TEST_SUITE.withExamples(simpleAvgMetric(), allMetricTypes(), noOperator());
  }

  @Test
  public void runTest() throws Exception {
    TEST_SUITE.runTest(this.testSpec);
  }

  private static MetricDefinition metric(MetricDefinition.Type type, String path) {
    return MetricDefinitionBuilder.builder().type(type).path(path).build();
  }

  private static BsonDeserializationTestSuite.ValidSpec<MetricsCollector> simpleAvgMetric() {
    return BsonDeserializationTestSuite.TestSpec.valid(
        "simple avg metric",
        CollectorBuilder.metrics()
            .operator(OperatorBuilder.text().path("review").query("good").build())
            .metricDefinitions(Map.of("avgRating", metric(MetricDefinition.Type.AVG, "rating")))
            .build());
  }

  private static BsonDeserializationTestSuite.ValidSpec<MetricsCollector> allMetricTypes() {
    return BsonDeserializationTestSuite.TestSpec.valid(
        "all metric types",
        CollectorBuilder.metrics()
            .operator(OperatorBuilder.text().path("review").query("good").build())
            .metricDefinitions(
                Map.of(
                    "avgRating", metric(MetricDefinition.Type.AVG, "rating"),
                    "sumRating", metric(MetricDefinition.Type.SUM, "rating"),
                    "minRating", metric(MetricDefinition.Type.MIN, "rating"),
                    "maxPrice", metric(MetricDefinition.Type.MAX, "price")))
            .build());
  }

  private static BsonDeserializationTestSuite.ValidSpec<MetricsCollector> noOperator() {
    return BsonDeserializationTestSuite.TestSpec.valid(
        "no operator",
        CollectorBuilder.metrics()
            .metricDefinitions(Map.of("avgRating", metric(MetricDefinition.Type.AVG, "rating")))
            .build());
  }
}
