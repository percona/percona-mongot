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
      PathBoostScoreTest.DeserializationTest.class,
      PathBoostScoreTest.SerializationTest.class,
    })
public class PathBoostScoreTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "path-boost-score-deserialization";
    private static final BsonDeserializationTestSuite<Score> TEST_SUITE =
        fromDocument(ScoreTests.RESOURCES_PATH, SUITE_NAME, Score::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Score> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Score> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<PathBoostScore>> data() {
      return TEST_SUITE.withExamples(pathNoUndefined(), pathWithUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathBoostScore> pathNoUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path no undefined", ScoreBuilder.pathBoost().path("popularity").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<PathBoostScore> pathWithUndefined() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "path with undefined",
          ScoreBuilder.pathBoost().path("popularity").undefined(2365.93).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "path-boost-score-serialization";
    public static final BsonSerializationTestSuite<PathBoostScore> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(ScoreTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<PathBoostScore> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<PathBoostScore> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<PathBoostScore>> data() {
      return Arrays.asList(pathWithUndefined());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<PathBoostScore> pathWithUndefined() {
      return BsonSerializationTestSuite.TestSpec.create(
          "path with undefined",
          ScoreBuilder.pathBoost().path("popularity").undefined(8891.43).build());
    }
  }
}
