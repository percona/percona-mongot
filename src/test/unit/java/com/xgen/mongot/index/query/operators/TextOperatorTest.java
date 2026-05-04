package com.xgen.mongot.index.query.operators;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.path.string.UnresolvedStringPathBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.index.query.operators.TextOperatorBuilder;
import com.xgen.testing.mongot.index.query.scores.ScoreBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      TextOperatorTest.DeserializationTest.class,
      TextOperatorTest.SerializationTest.class,
    })
public class TextOperatorTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "text-deserialization";
    private static final BsonDeserializationTestSuite<TextOperator> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, TextOperator::fromBson);

    private final TestSpecWrapper<TextOperator> testSpec;

    public DeserializationTest(TestSpecWrapper<TextOperator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<TextOperator>> data() {
      return TEST_SUITE.withExamples(
          simple(),
          matchCriteriaAny(),
          matchCriteriaAll(),
          multiPath(),
          withBoostScore(),
          withFunctionScore(),
          matchCriteriaWithScore(),
          defaultFuzzy(),
          explicitFuzzyOptions(),
          fuzzyNullIsOff(),
          matchCriteriaWithFuzzy(),
          synonyms(),
          matchCriteriaWithSynonyms());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<TextOperator> simple() {
      return TestSpec.valid(
          "simple", OperatorBuilder.text().path("title").query("godfather").build());
    }

    private static ValidSpec<TextOperator> matchCriteriaAny() {
      return TestSpec.valid(
          "matchCriteria any",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .matchCriteria(TextOperator.MatchCriteria.ANY)
              .build());
    }

    private static ValidSpec<TextOperator> matchCriteriaAll() {
      return TestSpec.valid(
          "matchCriteria all",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .matchCriteria(TextOperator.MatchCriteria.ALL)
              .build());
    }

    private static ValidSpec<TextOperator> multiPath() {
      return TestSpec.valid(
          "multi path",
          OperatorBuilder.text()
              .path(UnresolvedStringPathBuilder.withMulti("title", "my-multi"))
              .query("godfather")
              .build());
    }

    private static ValidSpec<TextOperator> withBoostScore() {
      return TestSpec.valid(
          "with boost score",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .score(ScoreBuilder.valueBoost().value(2).build())
              .build());
    }

    private static ValidSpec<TextOperator> withFunctionScore() {
      return TestSpec.valid(
          "with function score",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .score(
                  ScoreBuilder.function()
                      .expression(PathExpressionBuilder.builder().value("rating").build())
                      .build())
              .build());
    }

    private static ValidSpec<TextOperator> matchCriteriaWithScore() {
      return TestSpec.valid(
          "matchCriteria with score",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .score(ScoreBuilder.valueBoost().value(2).build())
              .matchCriteria(TextOperator.MatchCriteria.ALL)
              .build());
    }

    private static ValidSpec<TextOperator> defaultFuzzy() {
      return TestSpec.valid(
          "default fuzzy",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .fuzzy(TextOperatorBuilder.fuzzyBuilder().build())
              .build());
    }

    private static ValidSpec<TextOperator> explicitFuzzyOptions() {
      return TestSpec.valid(
          "explicit fuzzy options",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .fuzzy(
                  TextOperatorBuilder.fuzzyBuilder()
                      .maxEdits(1)
                      .maxExpansions(2)
                      .prefixLength(3)
                      .build())
              .build());
    }

    private static ValidSpec<TextOperator> matchCriteriaWithFuzzy() {
      return TestSpec.valid(
          "matchCriteria with fuzzy",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .fuzzy(
                  TextOperatorBuilder.fuzzyBuilder()
                      .maxEdits(1)
                      .maxExpansions(2)
                      .prefixLength(3)
                      .build())
              .matchCriteria(TextOperator.MatchCriteria.ALL)
              .build());
    }

    private static ValidSpec<TextOperator> fuzzyNullIsOff() {
      return TestSpec.valid(
          "fuzzy null is off", OperatorBuilder.text().path("title").query("godfather").build());
    }

    private static ValidSpec<TextOperator> synonyms() {
      return TestSpec.valid(
          "synonyms",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .synonyms("english_synonyms")
              .build());
    }

    private static ValidSpec<TextOperator> matchCriteriaWithSynonyms() {
      return TestSpec.valid(
          "matchCriteria with synonyms",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .synonyms("english_synonyms")
              .matchCriteria(TextOperator.MatchCriteria.ANY)
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "text-serialization";
    public static final BsonSerializationTestSuite<TextOperator> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(DefinitionTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<TextOperator> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<TextOperator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<TextOperator>> data() {
      return Arrays.asList(explicitFuzzy(), synonyms());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<TextOperator> explicitFuzzy() {
      return BsonSerializationTestSuite.TestSpec.create(
          "explicit fuzzy options",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .fuzzy(
                  TextOperatorBuilder.fuzzyBuilder()
                      .maxEdits(1)
                      .maxExpansions(2)
                      .prefixLength(3)
                      .build())
              .matchCriteria(TextOperator.MatchCriteria.ANY)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<TextOperator> synonyms() {
      return BsonSerializationTestSuite.TestSpec.create(
          "synonyms",
          OperatorBuilder.text()
              .path("title")
              .query("godfather")
              .synonyms("english_synonyms")
              .matchCriteria(TextOperator.MatchCriteria.ALL)
              .build());
    }
  }
}
