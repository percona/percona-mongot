package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.CoordinateBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.LatLonShapeQueryBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      LatLonShapeQuerySpecTest.DeserializationTest.class,
      LatLonShapeQuerySpecTest.SerializationTest.class,
    })
public class LatLonShapeQuerySpecTest {
  private static final List<List<Coordinate>> COORDS =
      List.of(
          List.of(
              CoordinateBuilder.builder().lon(2.0).lat(1.0).build(),
              CoordinateBuilder.builder().lon(6.0).lat(5.0).build(),
              CoordinateBuilder.builder().lon(11.0).lat(10.0).build()));

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "lat-lon-shape-query-deserialization";
    private static final BsonDeserializationTestSuite<LatLonShapeQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME, LatLonShapeQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<LatLonShapeQuerySpec> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<LatLonShapeQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<LatLonShapeQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple(), missingPath(), missingCoords());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonShapeQuerySpec> simple() {
      LatLonShapeQuerySpec query =
          LatLonShapeQueryBuilder.builder().path("a").coords(COORDS).build();
      return BsonDeserializationTestSuite.TestSpec.valid("simple", query);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonShapeQuerySpec> missingPath() {
      LatLonShapeQuerySpec query = LatLonShapeQueryBuilder.builder().coords(COORDS).build();
      return BsonDeserializationTestSuite.TestSpec.valid("missing-path", query);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonShapeQuerySpec> missingCoords() {
      LatLonShapeQuerySpec query = LatLonShapeQueryBuilder.builder().path("a").build();
      return BsonDeserializationTestSuite.TestSpec.valid("missing-coordinates", query);
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "lat-lon-shape-query-serialization";
    private static final BsonSerializationTestSuite<LatLonShapeQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<LatLonShapeQuerySpec> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<LatLonShapeQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<LatLonShapeQuerySpec>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<LatLonShapeQuerySpec> simple() {
      LatLonShapeQuerySpec query =
          LatLonShapeQueryBuilder.builder().path("a").coords(COORDS).build();
      return BsonSerializationTestSuite.TestSpec.create("simple", query);
    }
  }
}
