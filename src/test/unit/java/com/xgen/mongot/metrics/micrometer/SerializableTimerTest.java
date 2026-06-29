package com.xgen.mongot.metrics.micrometer;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.load;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.metrics.micrometer.PercentilesBuilder;
import com.xgen.testing.mongot.metrics.micrometer.SerializableTimerBuilder;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      SerializableTimerTest.DeserializationTest.class,
      SerializableTimerTest.SerializationTest.class,
    })
public class SerializableTimerTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "serializable-timer-deserialization";
    private static final BsonDeserializationTestSuite<SerializableTimer> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/metrics/micrometer", SUITE_NAME, SerializableTimer::fromBson);

    private final TestSpecWrapper<SerializableTimer> testSpec;

    public DeserializationTest(TestSpecWrapper<SerializableTimer> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<SerializableTimer>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<SerializableTimer> simple() {
      return TestSpec.valid(
          "simple",
          SerializableTimerBuilder.builder()
              .timeUnit(TimeUnit.SECONDS)
              .count(2)
              .totalTime(10.0)
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

    private static final String SUITE_NAME = "serializable-timer-serialization";
    private static final BsonSerializationTestSuite<SerializableTimer> TEST_SUITE =
        load("src/test/unit/resources/metrics/micrometer", SUITE_NAME, SerializableTimer::toBson);

    private final BsonSerializationTestSuite.TestSpec<SerializableTimer> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<SerializableTimer> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<SerializableTimer>> data() {
      return Collections.singletonList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<SerializableTimer> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          SerializableTimerBuilder.builder()
              .timeUnit(TimeUnit.SECONDS)
              .count(2)
              .totalTime(10.0)
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
