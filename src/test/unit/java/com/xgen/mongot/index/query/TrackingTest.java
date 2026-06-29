package com.xgen.mongot.index.query;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {TrackingTest.DeserializationTest.class, TrackingTest.SerializationTest.class})
public class TrackingTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "tracking-deserialization";
    private static final BsonDeserializationTestSuite<Tracking> TEST_SUITE =
        fromDocument("src/test/unit/resources/index/query/", SUITE_NAME, Tracking::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Tracking> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Tracking> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Tracking>> data() {
      return TEST_SUITE.withExamples(simple(), empty(), blank());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Tracking> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid("simple", new Tracking("simple"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<Tracking> empty() {
      return BsonDeserializationTestSuite.TestSpec.valid("empty string value", new Tracking(""));
    }

    private static BsonDeserializationTestSuite.ValidSpec<Tracking> blank() {
      return BsonDeserializationTestSuite.TestSpec.valid("blank string value", new Tracking("  "));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "tracking-serialization";
    private static final BsonSerializationTestSuite<Tracking> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/query/", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<Tracking> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Tracking> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Tracking>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Tracking> simple() {
      return BsonSerializationTestSuite.TestSpec.create("simple", new Tracking("abcd"));
    }
  }
}
