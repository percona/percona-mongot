package com.xgen.mongot.index.query.operators;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.mongot.index.path.string.StringFieldPath;
import com.xgen.mongot.index.path.string.StringMultiFieldPath;
import com.xgen.mongot.index.path.string.StringPath;
import com.xgen.mongot.index.path.string.UnresolvedStringFieldPath;
import com.xgen.mongot.index.path.string.UnresolvedStringMultiFieldPath;
import com.xgen.mongot.index.path.string.UnresolvedStringWildcardPath;
import com.xgen.mongot.index.query.scores.Score;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.mongot.index.path.string.StringPathBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.index.query.scores.ScoreBuilder;
import com.xgen.testing.mongot.index.query.scores.expressions.PathExpressionBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      OperatorsTest.FieldPathTest.class,
      OperatorsTest.StringPathTest.class,
      OperatorsTest.QueryTest.class,
      OperatorsTest.ScoreTest.class,
    })
public class OperatorsTest {

  @RunWith(Parameterized.class)
  public static class FieldPathTest {

    private static final String SUITE_NAME = "operators-field-path";
    private static final BsonDeserializationTestSuite<List<FieldPath>> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, Operators::parseFieldPath);

    private final TestSpecWrapper<List<FieldPath>> testSpec;

    public FieldPathTest(TestSpecWrapper<List<FieldPath>> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<List<FieldPath>>> data() {
      return TEST_SUITE.withExamples(singleValue(), arrayOfValues());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<List<FieldPath>> singleValue() {
      return TestSpec.valid("single value", Collections.singletonList(FieldPath.parse("my-path")));
    }

    private static ValidSpec<List<FieldPath>> arrayOfValues() {
      return TestSpec.valid(
          "array of values",
          Arrays.asList(FieldPath.parse("my-path"), FieldPath.parse("my-other-path")));
    }

    // TODO(CLOUDP-327217): Remove this.
    @Test
    public void testGetAdjacentChildPaths() {
      Operator operator =
          OperatorBuilder.compound()
              .should(
                  OperatorBuilder.text()
                      .path(new UnresolvedStringFieldPath(FieldPath.parse("a.b")))
                      .query("text")
                      .build())
              .should(
                  OperatorBuilder.text()
                      .path(new UnresolvedStringMultiFieldPath(FieldPath.parse("a.c"), "multi"))
                      .query("text")
                      .build())
              .should(
                  OperatorBuilder.text()
                      .path(new UnresolvedStringWildcardPath("a.*"))
                      .query("text")
                      .build())
              .should(
                  OperatorBuilder.search()
                      .path(new StringFieldPath(FieldPath.parse("a.d")))
                      .query("text")
                      .build())
              .should(
                  OperatorBuilder.search()
                      .path(new StringMultiFieldPath(FieldPath.parse("a.e"), "multi"))
                      .query("text")
                      .build())
              .should(OperatorBuilder.autocomplete().path("a.f").query("text").build())
              .should(OperatorBuilder.exists().path("a.g").build())
              .should(
                  OperatorBuilder.embeddedDocument()
                      .path("a.h")
                      .operator(OperatorBuilder.text().path("a.h.i").query("othertext").build())
                      .build())
              .build();
      List<FieldPath> expected =
          List.of(
              FieldPath.parse("a.b"),
              FieldPath.parse("a.c"),
              FieldPath.parse("a.*"),
              FieldPath.parse("a.d"),
              FieldPath.parse("a.e"),
              FieldPath.parse("a.f"),
              FieldPath.parse("a.g"),
              // Embedded document operator will not include the child path "a.h.i".
              FieldPath.parse("a.h"));
      Assert.assertEquals(expected, Operators.getAdjacentChildPaths(operator));
    }

    @Test
    public void testGetAllChildPathsUsesScorePaths() {
      Operator operator =
          OperatorBuilder.compound()
              .should(
                  OperatorBuilder.text()
                      .path(new UnresolvedStringFieldPath(FieldPath.parse("a")))
                      .query("text")
                      .score(ScoreBuilder.constant().value(2.0f).build())
                      .build())
              .should(
                  OperatorBuilder.text()
                      .path(new UnresolvedStringMultiFieldPath(FieldPath.parse("a.b"), "multi"))
                      .query("text")
                      .score(
                          ScoreBuilder.function()
                              .expression(PathExpressionBuilder.builder().value("a.c").build())
                              .build())
                      .build())
              .build();
      List<FieldPath> expected =
          List.of(FieldPath.parse("a"), FieldPath.parse("a.b"), FieldPath.parse("a.c"));
      Assert.assertEquals(expected, Operators.getAdjacentChildPaths(operator));
    }
  }

  @RunWith(Parameterized.class)
  public static class StringPathTest {

    private static final String SUITE_NAME = "operators-string-path";
    private static final BsonDeserializationTestSuite<List<StringPath>> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, Operators::parseStringPath);

    private final TestSpecWrapper<List<StringPath>> testSpec;

    public StringPathTest(TestSpecWrapper<List<StringPath>> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<List<StringPath>>> data() {
      return TEST_SUITE.withExamples(singleValue(), multi(), arrayOfValues());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<List<StringPath>> singleValue() {
      return TestSpec.valid(
          "single value", Collections.singletonList(StringPathBuilder.fieldPath("my-path")));
    }

    private static ValidSpec<List<StringPath>> multi() {
      return TestSpec.valid(
          "multi", Collections.singletonList(StringPathBuilder.withMulti("my-path", "my-multi")));
    }

    private static ValidSpec<List<StringPath>> arrayOfValues() {
      return TestSpec.valid(
          "array of values",
          Arrays.asList(
              StringPathBuilder.fieldPath("my-path"),
              StringPathBuilder.withMulti("my-path", "my-multi")));
    }
  }

  @RunWith(Parameterized.class)
  public static class QueryTest {

    private static final String SUITE_NAME = "operators-query";
    private static final BsonDeserializationTestSuite<List<String>> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, Operators::parseQuery);

    private final TestSpecWrapper<List<String>> testSpec;

    public QueryTest(TestSpecWrapper<List<String>> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<List<String>>> data() {
      return TEST_SUITE.withExamples(singleValue(), arrayOfValues());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<List<String>> singleValue() {
      return TestSpec.valid("single value", Collections.singletonList("my query"));
    }

    private static ValidSpec<List<String>> arrayOfValues() {
      return TestSpec.valid("array of values", Arrays.asList("my first query", "my second query"));
    }
  }

  @RunWith(Parameterized.class)
  public static class ScoreTest {

    private static final String SUITE_NAME = "operators-score";
    private static final BsonDeserializationTestSuite<Score> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, Operators::parseScore);

    private final TestSpecWrapper<Score> testSpec;

    public ScoreTest(TestSpecWrapper<Score> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<Score>> data() {
      return TEST_SUITE.withExamples(explicitScore(), arrayOfValues());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<Score> explicitScore() {
      return TestSpec.valid("explicit score", ScoreBuilder.valueBoost().value(2).build());
    }

    private static ValidSpec<Score> arrayOfValues() {
      return TestSpec.valid("default score", Score.defaultScore());
    }
  }
}
