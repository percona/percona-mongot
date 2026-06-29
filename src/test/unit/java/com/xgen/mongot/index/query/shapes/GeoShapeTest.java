package com.xgen.mongot.index.query.shapes;

import static com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.mongot.index.query.shapes.ShapeBuilder;
import com.xgen.testing.mongot.util.geo.MockGeometries;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      GeoShapeTest.CircleTest.class,
      GeoShapeTest.BoxTest.class,
      GeoShapeTest.GeometryShapeTest.class
    })
public class GeoShapeTest {
  private static final String RESOURCES_PATH = "src/test/unit/resources/index/query/shapes/";

  @RunWith(Parameterized.class)
  public static class CircleTest {

    private static final String SUITE_NAME = "geo-shape-circle";
    private static final BsonDeserializationTestSuite<Circle> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, Circle::fromBson);

    private final TestSpecWrapper<Circle> testSpec;

    public CircleTest(TestSpecWrapper<Circle> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<Circle>> data() {
      return TEST_SUITE.withExamples(simple(), integralPositionCenter(), radiusZero());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<Circle> simple() {
      return TestSpec.valid("simple", ShapeBuilder.circle(1, 2, 3));
    }

    private static ValidSpec<Circle> integralPositionCenter() {
      return TestSpec.valid("integral position center", ShapeBuilder.circle(1, 2, 3));
    }

    private static ValidSpec<Circle> radiusZero() {
      return TestSpec.valid("radius zero", ShapeBuilder.circle(1, 2, 0));
    }
  }

  @RunWith(Parameterized.class)
  public static class BoxTest {

    private static final String SUITE_NAME = "geo-shape-box";
    private static final BsonDeserializationTestSuite<Box> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, Box::fromBson);

    private final TestSpecWrapper<Box> testSpec;

    public BoxTest(TestSpecWrapper<Box> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<Box>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<Box> simple() {
      return TestSpec.valid("simple", ShapeBuilder.box(1, 2, 3, 4));
    }
  }

  @RunWith(Parameterized.class)
  public static class GeometryShapeTest {

    private static final String SUITE_NAME = "geo-shape-geometry";
    private static final BsonDeserializationTestSuite<GeometryShape> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, GeometryShape::fromBson);

    private final TestSpecWrapper<GeometryShape> testSpec;

    public GeometryShapeTest(TestSpecWrapper<GeometryShape> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<GeometryShape>> data() {
      return TEST_SUITE.withExamples(point(), line());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<GeometryShape> point() {
      return TestSpec.valid("point", ShapeBuilder.geometry(MockGeometries.POINT_A));
    }

    private static ValidSpec<GeometryShape> line() {
      return TestSpec.valid("line", ShapeBuilder.geometry(MockGeometries.LINE_AB));
    }
  }
}
