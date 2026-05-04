package com.xgen.mongot.index.query.operators;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.mongot.index.query.scores.Score;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import java.util.List;
import org.bson.BsonDocument;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      MoreLikeThisOperatorTest.DeserializationTest.class,
      MoreLikeThisOperatorTest.SerializationTest.class,
    })
public class MoreLikeThisOperatorTest {

  private static BsonDocument docA() {
    return BsonDocument.parse("{title:\"C++\", author: \"Bjarne Stroustrup\"}");
  }

  private static BsonDocument docB() {
    return BsonDocument.parse("{title:\"Java\", author: \"James Gosling\"}");
  }

  private static BsonDocument docC() {
    return BsonDocument.parse("{fruit:[\"Apple\", \"Pear\"]}");
  }

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "more-like-this-deserialization";
    private static final BsonDeserializationTestSuite<MoreLikeThisOperator> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, MoreLikeThisOperator::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<MoreLikeThisOperator> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<MoreLikeThisOperator> testSpec) {
      this.testSpec = testSpec;
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<MoreLikeThisOperator>>
        data() {
      return TEST_SUITE.withExamples(oneDoc(), twoDocs(), docWithArray());
    }

    private static BsonDeserializationTestSuite.ValidSpec<MoreLikeThisOperator> oneDoc() {
      var operator = new MoreLikeThisOperator(List.of(docA()), Score.defaultScore());
      return BsonDeserializationTestSuite.TestSpec.valid("oneDoc", operator);
    }

    private static BsonDeserializationTestSuite.ValidSpec<MoreLikeThisOperator> docWithArray() {
      var operator = new MoreLikeThisOperator(List.of(docC()), Score.defaultScore());
      return BsonDeserializationTestSuite.TestSpec.valid("docWithArray", operator);
    }

    private static BsonDeserializationTestSuite.ValidSpec<MoreLikeThisOperator> twoDocs() {
      var operator = new MoreLikeThisOperator(List.of(docA(), docB()), Score.defaultScore());
      return BsonDeserializationTestSuite.TestSpec.valid("twoDocs", operator);
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    public static final String SUITE_NAME = "more-like-this-serialization";
    public static final BsonSerializationTestSuite<MoreLikeThisOperator> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(DefinitionTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<MoreLikeThisOperator> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<MoreLikeThisOperator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<MoreLikeThisOperator>> data() {
      return List.of(oneDoc(), twoDocs());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<MoreLikeThisOperator> oneDoc() {
      return BsonSerializationTestSuite.TestSpec.create(
          "one doc", new MoreLikeThisOperator(List.of(docA()), Score.defaultScore()));
    }

    private static BsonSerializationTestSuite.TestSpec<MoreLikeThisOperator> twoDocs() {
      return BsonSerializationTestSuite.TestSpec.create(
          "two docs", new MoreLikeThisOperator(List.of(docA(), docB()), Score.defaultScore()));
    }
  }
}
