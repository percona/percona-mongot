package com.xgen.mongot.index.query.scores;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.scores.ScoreBuilder;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      EmbeddedScoreTest.DeserializationTest.class,
      EmbeddedScoreTest.SerializationTest.class
    })
public class EmbeddedScoreTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "embedded-score-deserialization";
    private static final BsonDeserializationTestSuite<EmbeddedScore> TEST_SUITE =
        fromDocument(ScoreTests.RESOURCES_PATH, SUITE_NAME, EmbeddedScore::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<EmbeddedScore> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<EmbeddedScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<EmbeddedScore>> data() {
      return TEST_SUITE.withExamples(
          emptyDefault(), noAggregate(), noOuterScore(), minimumWithConstantOuterScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<EmbeddedScore> emptyDefault() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "empty default", ScoreBuilder.embedded().build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<EmbeddedScore> noAggregate() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "no aggregate constant score",
          ScoreBuilder.embedded().outerScore(ScoreBuilder.constant().value(1).build()).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<EmbeddedScore> noOuterScore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "no outerScore maximum aggregate",
          ScoreBuilder.embedded().aggregate(EmbeddedScore.Aggregate.MAXIMUM).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<EmbeddedScore>
        minimumWithConstantOuterScore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "minimum with constant outerScore",
          ScoreBuilder.embedded()
              .aggregate(EmbeddedScore.Aggregate.MINIMUM)
              .outerScore(ScoreBuilder.constant().value(1).build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "embedded-score-serialization";
    private static final BsonSerializationTestSuite<EmbeddedScore> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(ScoreTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<EmbeddedScore> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<EmbeddedScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<EmbeddedScore>> data() {
      return Collections.singletonList(minimumWithConstantOuterScore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<EmbeddedScore>
        minimumWithConstantOuterScore() {
      return BsonSerializationTestSuite.TestSpec.create(
          "minimum with constant outerScore",
          ScoreBuilder.embedded()
              .aggregate(EmbeddedScore.Aggregate.MINIMUM)
              .outerScore(ScoreBuilder.constant().value(1).build())
              .build());
    }
  }
}
