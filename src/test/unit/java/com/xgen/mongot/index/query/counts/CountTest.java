package com.xgen.mongot.index.query.counts;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.counts.CountBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {CountTest.DeserializationTest.class, CountTest.SerializationTest.class})
public class CountTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "count-deserialization";
    private static final BsonDeserializationTestSuite<Count> TEST_SUITE =
        fromDocument("src/test/unit/resources/index/query/counts", SUITE_NAME, Count::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Count> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Count> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Count>> data() {
      return TEST_SUITE.withExamples(
          defaultCount(),
          explicitType(),
          explicitThreshold(),
          zeroThreshold(),
          explicitTypeAndThreshold());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Count> defaultCount() {
      return BsonDeserializationTestSuite.TestSpec.valid("default", Count.DEFAULT);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Count> explicitType() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit type",
          CountBuilder.builder()
              .type(Count.Type.TOTAL)
              .threshold(Count.DEFAULT.threshold())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Count> explicitThreshold() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit threshold",
          CountBuilder.builder().type(Count.DEFAULT.type()).threshold(5000).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Count> zeroThreshold() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "zero threshold", CountBuilder.builder().type(Count.DEFAULT.type()).threshold(0).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Count> explicitTypeAndThreshold() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit type and threshold",
          CountBuilder.builder().type(Count.Type.LOWER_BOUND).threshold(5000).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "count-serialization";
    private static final BsonSerializationTestSuite<Count> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/query/counts", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<Count> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Count> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Count>> data() {
      return List.of(lowerBound(), total());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Count> lowerBound() {
      return BsonSerializationTestSuite.TestSpec.create(
          "lowerBound count",
          CountBuilder.builder().type(Count.Type.LOWER_BOUND).threshold(5000).build());
    }

    private static BsonSerializationTestSuite.TestSpec<Count> total() {
      return BsonSerializationTestSuite.TestSpec.create(
          "total count", CountBuilder.builder().type(Count.Type.TOTAL).threshold(10).build());
    }
  }
}
