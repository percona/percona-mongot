package com.xgen.mongot.metrics.micrometer;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.load;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.metrics.micrometer.PercentilesBuilder;
import com.xgen.testing.mongot.metrics.micrometer.SerializableDistributionSummaryBuilder;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      SerializableDistributionSummaryTest.DeserializationTest.class,
      SerializableDistributionSummaryTest.SerializationTest.class,
    })
public class SerializableDistributionSummaryTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "serializable-distribution-summary-deserialization";
    private static final BsonDeserializationTestSuite<SerializableDistributionSummary> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/metrics/micrometer",
            SUITE_NAME,
            SerializableDistributionSummary::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<SerializableDistributionSummary>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<SerializableDistributionSummary> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<SerializableDistributionSummary>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<SerializableDistributionSummary>
        simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          SerializableDistributionSummaryBuilder.builder()
              .count(2L)
              .total(2.0)
              .max(7.0)
              .mean(3.0)
              .percentiles(
                  PercentilesBuilder.builder()
                      .percentile50(2.0)
                      .percentile75(2.0)
                      .percentile90(2.0)
                      .percentile99(1.0)
                      .build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "serializable-distribution-summary-serialization";
    private static final BsonSerializationTestSuite<SerializableDistributionSummary> TEST_SUITE =
        load(
            "src/test/unit/resources/metrics/micrometer",
            SUITE_NAME,
            SerializableDistributionSummary::toBson);

    private final BsonSerializationTestSuite.TestSpec<SerializableDistributionSummary> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<SerializableDistributionSummary> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<SerializableDistributionSummary>>
        data() {
      return Collections.singletonList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<SerializableDistributionSummary> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          SerializableDistributionSummaryBuilder.builder()
              .count(2L)
              .total(2.0)
              .max(7.0)
              .mean(3.0)
              .percentiles(
                  PercentilesBuilder.builder()
                      .percentile50(2.0)
                      .percentile75(2.0)
                      .percentile90(2.0)
                      .percentile99(1.0)
                      .build())
              .build());
    }
  }
}
