package com.xgen.mongot.index.query.sort;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.sort.SortFieldBuilder;
import com.xgen.testing.mongot.index.query.sort.SortSpecBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      SortBetaV1Test.DeserializationTest.class,
      SortBetaV1Test.SerializationTest.class,
      SortBetaV1Test.ClassTest.class
    })
public class SortBetaV1Test {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "sort-beta-v1-deserialization";
    private static final BsonDeserializationTestSuite<SortSpec> TEST_SUITE =
        fromValue("src/test/unit/resources/index/query/sort", SUITE_NAME, SortBetaV1::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<SortSpec> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<SortSpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<SortSpec>> data() {
      return TEST_SUITE.withExamples(simple(), mixedNumericalValues());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<SortSpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          SortSpecBuilder.builder()
              .sortField(
                  SortFieldBuilder.builder()
                      .path("a")
                      .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                      .build())
              .sortField(
                  SortFieldBuilder.builder()
                      .path("b")
                      .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                      .build())
              .buildSortBetaV1());
    }

    private static BsonDeserializationTestSuite.ValidSpec<SortSpec> mixedNumericalValues() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "mixed numerical values",
          SortSpecBuilder.builder()
              .sortField(
                  SortFieldBuilder.builder()
                      .path("a")
                      .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                      .build())
              .sortField(
                  SortFieldBuilder.builder()
                      .path("b")
                      .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                      .build())
              .sortField(
                  SortFieldBuilder.builder()
                      .path("c")
                      .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                      .build())
              .buildSortBetaV1());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "sort-beta-v1-serialization";
    private static final BsonSerializationTestSuite<SortSpec> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/query/sort", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<SortSpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<SortSpec> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<SortSpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<SortSpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          SortSpecBuilder.builder()
              .sortField(
                  SortFieldBuilder.builder()
                      .path("a")
                      .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                      .build())
              .sortField(
                  SortFieldBuilder.builder()
                      .path("b")
                      .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                      .build())
              .buildSortBetaV1());
    }
  }

  public static class ClassTest {

    @Test
    public void testFieldOrder() throws BsonParseException {
      BsonDocument sortDoc =
          new BsonDocument().append("a", new BsonInt32(1)).append("b", new BsonInt32(-1));
      try (var parser = BsonDocumentParser.fromRoot(sortDoc).allowUnknownFields(true).build()) {
        SortSpec sortBetaV1 = SortBetaV1.fromBson(parser.getContext(), sortDoc);
        Assert.assertEquals(
            "must be in the same order",
            List.of("a", "b"),
            sortBetaV1.getSortFields().stream()
                .map(fields -> fields.field().toString())
                .collect(Collectors.toList()));
      }
    }
  }
}
