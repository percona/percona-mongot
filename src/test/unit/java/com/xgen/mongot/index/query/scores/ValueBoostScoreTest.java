package com.xgen.mongot.index.query.scores;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.ScoreBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ValueBoostScoreTest.DeserializationTest.class,
      ValueBoostScoreTest.SerializationTest.class,
    })
public class ValueBoostScoreTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "value-boost-score-deserialization";
    private static final BsonDeserializationTestSuite<Score> TEST_SUITE =
        fromDocument(ScoreTests.RESOURCES_PATH, SUITE_NAME, Score::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Score> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Score> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<ValueBoostScore>> data() {
      return TEST_SUITE.withExamples(positiveValue());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<ValueBoostScore> positiveValue() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "positive value", ScoreBuilder.valueBoost().value(13f).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "value-boost-score-serialization";
    public static final BsonSerializationTestSuite<ValueBoostScore> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(ScoreTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ValueBoostScore> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<ValueBoostScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ValueBoostScore>> data() {
      return Arrays.asList(value());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ValueBoostScore> value() {
      return BsonSerializationTestSuite.TestSpec.create(
          "value", ScoreBuilder.valueBoost().value(56f).build());
    }
  }
}
