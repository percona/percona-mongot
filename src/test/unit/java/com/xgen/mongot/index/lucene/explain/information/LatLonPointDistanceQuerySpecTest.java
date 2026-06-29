package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.CoordinateBuilder;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.LatLonPointDistanceQueryBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      LatLonPointDistanceQuerySpecTest.DeserializationTest.class,
      LatLonPointDistanceQuerySpecTest.SerializationTest.class,
    })
public class LatLonPointDistanceQuerySpecTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "lat-lon-point-dist-query-deserialization";
    private static final BsonDeserializationTestSuite<LatLonPointDistanceQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            LatLonPointDistanceQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<LatLonPointDistanceQuerySpec>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<LatLonPointDistanceQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<LatLonPointDistanceQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(simple(), missingPath(), missingCenter(), missingRadius());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonPointDistanceQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          LatLonPointDistanceQueryBuilder.builder()
              .path("a")
              .center(CoordinateBuilder.builder().lon(2.0).lat(1.0).build())
              .radius(3.0)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonPointDistanceQuerySpec>
        missingPath() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-path",
          LatLonPointDistanceQueryBuilder.builder()
              .center(CoordinateBuilder.builder().lon(2.0).lat(1.0).build())
              .radius(3.0)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonPointDistanceQuerySpec>
        missingCenter() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-center",
          LatLonPointDistanceQueryBuilder.builder().path("a").radius(3.0).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LatLonPointDistanceQuerySpec>
        missingRadius() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-radius",
          LatLonPointDistanceQueryBuilder.builder()
              .path("a")
              .center(CoordinateBuilder.builder().lon(2.0).lat(1.0).build())
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "lat-lon-point-dist-query-serialization";
    private static final BsonSerializationTestSuite<LatLonPointDistanceQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<LatLonPointDistanceQuerySpec> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<LatLonPointDistanceQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<LatLonPointDistanceQuerySpec>>
        data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<LatLonPointDistanceQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          LatLonPointDistanceQueryBuilder.builder()
              .path("a")
              .center(CoordinateBuilder.builder().lon(2.5).lat(1.5).build())
              .radius(3.5)
              .build());
    }
  }
}
