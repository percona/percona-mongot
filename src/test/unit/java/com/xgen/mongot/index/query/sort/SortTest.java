package com.xgen.mongot.index.query.sort;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.sort.SortFieldBuilder;
import com.xgen.testing.mongot.index.query.sort.SortOptionsBuilder;
import com.xgen.testing.mongot.index.query.sort.SortSpecBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(value = {SortTest.DeserializationTest.class, SortTest.SerializationTest.class})
public class SortTest {

  public static class ClassTest {

    @Test
    public void testInvert() {
      var fields =
          ImmutableList.of(
              new MongotSortField(FieldPath.newRoot("a"), UserFieldSortOptions.DEFAULT_ASC),
              new MongotSortField(FieldPath.newRoot("b"), UserFieldSortOptions.DEFAULT_DESC));
      Sort original = new Sort(fields);

      Sort inverted = original.invert();

      assertEquals(fields.get(0).field(), inverted.getSortFields().get(0).field());
      assertEquals(fields.get(0).invert().options(), inverted.getSortFields().get(0).options());
      assertEquals(fields.get(1).field(), inverted.getSortFields().get(1).field());
      assertEquals(fields.get(1).invert().options(), inverted.getSortFields().get(1).options());
    }
  }

  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "sort-deserialization";
    private static final BsonDeserializationTestSuite<SortSpec> TEST_SUITE =
        fromValue("src/test/unit/resources/index/query/sort", SUITE_NAME, Sort::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<SortSpec> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<SortSpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<SortSpec>> data() {
      return TEST_SUITE.withExamples(simple(), mixedNumericalValues(), score());
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
              .buildSort());
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
              .buildSort());
    }
  }

  private static BsonDeserializationTestSuite.ValidSpec<SortSpec> score() {
    return BsonDeserializationTestSuite.TestSpec.valid(
        "score",
        SortSpecBuilder.builder()
            .sortField(
                SortFieldBuilder.builder()
                    .path("unused")
                    .sortOption(
                        SortOptionsBuilder.meta()
                            .meta(MetaSortField.SEARCH_SCORE)
                            .sortOrder(SortOrder.DESC)
                            .build())
                    .build())
            .buildSort());
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "sort-serialization";
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
              .buildSort());
    }
  }
}
