package com.xgen.mongot.index.query.sort;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.xgen.mongot.util.bson.parser.BsonParseContext;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.TestUtils;
import java.util.List;
import java.util.UUID;
import org.apache.commons.math3.util.Pair;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.ScoreDoc;
import org.bson.BsonBinary;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      SequenceTokenTest.ClassTest.class,
      SequenceTokenTest.SerializationTest.class,
      SequenceTokenTest.DeserializationTest.class
    })
public class SequenceTokenTest {

  @RunWith(Theories.class)
  public static class ClassTest {

    @Test
    public void invalidToken() {
      TestUtils.assertThrows(
          "Invalid format for token value",
          BsonParseException.class,
          () -> SequenceToken.fromBson(BsonParseContext.root(), new BsonString("!@#$%^&*")));
      TestUtils.assertThrows(
          "Invalid token component",
          BsonParseException.class,
          () ->
              SequenceToken.fromBson(
                  BsonParseContext.root(), new BsonString("CP///////////wEVAAAAAA==")));
    }

    @DataPoints
    public static ImmutableList<Pair<ScoreDoc, FieldDoc>> fieldDocs() {
      // Map original FieldDoc to expected decoding. Values allowed to be trivially different.
      return ImmutableList.of(
          new Pair<>(new ScoreDoc(0, 0f), new FieldDoc(0, 0f, new Object[] {0f})),
          new Pair<>(new ScoreDoc(0, 2f), new FieldDoc(0, 2f, new Object[] {2f})),
          new Pair<>(new FieldDoc(0, 2f), new FieldDoc(0, 2f, new Object[] {2f})),
          new Pair<>(
              new FieldDoc(Integer.MAX_VALUE, Float.NaN),
              new FieldDoc(Integer.MAX_VALUE, Float.NaN, new Object[] {Float.NaN})),
          new Pair<>(new FieldDoc(1, 1f, null), new FieldDoc(1, 1f, new Object[] {1f})),
          new Pair<>(new FieldDoc(1, 1f, new Object[0]), new FieldDoc(1, 1f, new Object[] {1f})),
          // score is inferred from sort values
          new Pair<>(
              new FieldDoc(
                  1, 0, new Object[] {new BsonString("t"), new BsonInt64(1), 1f, BsonNull.VALUE}),
              new FieldDoc(
                  1, 1f, new Object[] {new BsonString("t"), new BsonInt64(1), 1f, BsonNull.VALUE})),
          // score is discarded because sort doesn't need it
          new Pair<>(
              new FieldDoc(
                  1,
                  1f,
                  new Object[] {
                    new BsonString("t"),
                    new BsonInt64(1),
                    BsonNull.VALUE,
                    new BsonObjectId(new ObjectId("F".repeat(24))),
                    new BsonBinary(new UUID(1, 2))
                  }),
              new FieldDoc(
                  1,
                  0f,
                  new Object[] {
                    new BsonString("t"),
                    new BsonInt64(1),
                    BsonNull.VALUE,
                    new BsonObjectId(new ObjectId("F".repeat(24))),
                    new BsonBinary(new UUID(1, 2))
                  })));
    }

    @Theory
    public void decodeRecoversOriginalFieldDoc(Pair<ScoreDoc, FieldDoc> pair)
        throws BsonParseException {
      ScoreDoc input = pair.getKey();
      FieldDoc expected = pair.getValue();
      BsonString id = new BsonString("test");
      SequenceToken token = SequenceToken.of(id, input);

      BsonValue encoded = token.toBson();
      SequenceToken recovered = SequenceToken.fromBson(BsonParseContext.root(), encoded);
      FieldDoc result = recovered.fieldDoc();

      assertEquals(expected.score, result.score, TestUtils.EPSILON);
      assertEquals(expected.doc, result.doc);
      assertEquals(id, recovered.id().get());
      assertArrayEquals(expected.fields, result.fields);
    }

    @Test
    public void decodeRecoversOriginalScoreDoc() throws BsonParseException {
      ScoreDoc input = new ScoreDoc(0, Float.NaN);
      BsonString id = new BsonString("test");
      SequenceToken token = SequenceToken.of(id, input);

      BsonValue value = token.toBson();
      SequenceToken recovered = SequenceToken.fromBson(BsonParseContext.root(), value);
      FieldDoc result = recovered.fieldDoc();

      assertEquals(input.score, result.score, TestUtils.EPSILON);
      assertEquals(input.doc, result.doc);
      assertEquals(id, recovered.id().get());
      assertArrayEquals(result.fields, new Object[] {input.score});
    }
  }

  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "sequence-token-deserialization";
    private static final BsonDeserializationTestSuite<SequenceToken> TEST_SUITE =
        fromValue("src/test/unit/resources/index/query/sort", SUITE_NAME, SequenceToken::fromBson);

    private final TestSpecWrapper<SequenceToken> testSpec;

    public DeserializationTest(TestSpecWrapper<SequenceToken> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<SequenceToken>> data() {
      return TEST_SUITE.withExamples(
          emptyString(),
          scoreDoc(),
          fieldDoc(),
          searchBefore(),
          scoreDocWithId(),
          fieldDocWithId(),
          searchBeforeWithId());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> emptyString() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "empty String", new SequenceToken(new FieldDoc(0, 0f, new Object[] {0f})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> scoreDoc() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "scoreDoc", new SequenceToken(new FieldDoc(1, 2f, new Object[] {2f})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> fieldDoc() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "fieldDoc",
          new SequenceToken(new FieldDoc(1, 1f, new Object[] {1f, new BsonString("test")})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> searchBefore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "searchBefore",
          new SequenceToken(new FieldDoc(1, 2f, new Object[] {2f, new BsonString("test")})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> scoreDocWithId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "scoreDoc-with-id",
          new SequenceToken(new BsonInt32(1), new FieldDoc(1, 2f, new Object[] {2f})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> fieldDocWithId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "fieldDoc-with-id",
          new SequenceToken(
              new BsonInt32(1), new FieldDoc(1, 1f, new Object[] {1f, new BsonString("test")})));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SequenceToken> searchBeforeWithId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "searchBefore-with-id",
          new SequenceToken(
              new BsonInt32(1), new FieldDoc(1, 2f, new Object[] {2f, new BsonString("test")})));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "sequence-token-serialization";
    private static final BsonSerializationTestSuite<SequenceToken> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/query/sort", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<SequenceToken> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<SequenceToken> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<SequenceToken>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<SequenceToken> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", new SequenceToken(new BsonInt32(5), new FieldDoc(1, 2f)));
    }
  }
}
