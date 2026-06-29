package com.xgen.mongot.metrics.micrometer;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.load;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.metrics.micrometer.PercentilesBuilder;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      PercentilesTest.DeserializationTest.class,
      PercentilesTest.SerializationTest.class,
    })
public class PercentilesTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "percentiles-deserialization";
    private static final BsonDeserializationTestSuite<Percentiles> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/metrics/micrometer", SUITE_NAME, Percentiles::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Percentiles> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Percentiles> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Percentiles>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Percentiles> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          PercentilesBuilder.builder()
              .percentile50(2.0)
              .percentile75(2.0)
              .percentile90(2.0)
              .percentile99(1.0)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "percentiles-serialization";
    private static final BsonSerializationTestSuite<Percentiles> TEST_SUITE =
        load("src/test/unit/resources/metrics/micrometer", SUITE_NAME, Percentiles::toBson);

    private final BsonSerializationTestSuite.TestSpec<Percentiles> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Percentiles> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Percentiles>> data() {
      return Collections.singletonList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Percentiles> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          PercentilesBuilder.builder()
              .percentile50(2.0)
              .percentile75(2.0)
              .percentile90(2.0)
              .percentile99(1.0)
              .build());
    }
  }
}
