package com.xgen.mongot.util.geo;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.google.common.collect.Lists;
import com.mongodb.client.model.geojson.Geometry;
import com.mongodb.client.model.geojson.LineString;
import com.mongodb.client.model.geojson.MultiPoint;
import com.mongodb.client.model.geojson.MultiPolygon;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Polygon;
import com.mongodb.client.model.geojson.Position;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.mongot.util.geo.MockGeometries;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      GeoJsonParserTest.ParsePointTest.class,
      GeoJsonParserTest.ParseGeometryTest.class,
    })
public class GeoJsonParserTest {
  public static class ParsePointTest {
    @Test
    public void testParsePointValid() throws BsonParseException {
      var doc =
          new BsonDocument()
              .append("type", new BsonString("Point"))
              .append(
                  "coordinates", new BsonArray(List.of(new BsonDouble(10), new BsonDouble(12))));
      var parser = BsonDocumentParser.fromRoot(doc).build();
      var actual = GeoJsonParser.parsePoint(parser);
      Assert.assertEquals("point:", MockGeometries.POINT_A, actual);
    }

    @Test
    public void testParsePointDoesNotAcceptOtherGeometries() {
      for (BsonDocument document :
          List.of(
              new BsonDocument(),
              new BsonDocument().append("type", new BsonString("not a point")),
              MockGeometries.BSON_LINE,
              MockGeometries.BSON_COLLECTION,
              MockGeometries.BSON_POLYGON)) {
        var parser = BsonDocumentParser.fromRoot(document).build();
        Assert.assertThrows(BsonParseException.class, () -> GeoJsonParser.parsePoint(parser));
      }
    }
  }

  @RunWith(Parameterized.class)
  public static class ParseGeometryTest {
    private static final String RESOURCES_PATH = "src/test/unit/resources/util/geo/";
    private static final String SUITE_NAME = "geo-json";
    private static final BsonDeserializationTestSuite<Geometry> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, GeoJsonParser::parseGeometry);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Geometry> testSpec;

    public ParseGeometryTest(BsonDeserializationTestSuite.TestSpecWrapper<Geometry> testSpec) {
      this.testSpec = testSpec;
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Geometry>> data() {
      return TEST_SUITE.withExamples(
          valid("point", MockGeometries.POINT_A),
          valid("point integral pos", MockGeometries.POINT_A),
          valid("point 3 positions", new Point(new Position(10.5, 12.5, 14.5))),
          valid("point out of bounds", new Point(new Position(1000, 1200))),
          valid("line", MockGeometries.LINE_AB),
          valid(
              "line extra coordinates",
              new LineString(
                  Lists.newArrayList(new Position(10, 12, 14), new Position(11, 13, 15)))),
          valid("multi point empty", new MultiPoint(Lists.newArrayList())),
          valid("multi point 1", MockGeometries.multiPoint(MockGeometries.POINT_A)),
          valid(
              "multi point 2",
              MockGeometries.multiPoint(MockGeometries.POINT_A, MockGeometries.POINT_B)),
          valid("multi line empty", MockGeometries.multiLine()),
          valid("multi line 1", MockGeometries.multiLine(MockGeometries.LINE_AB)),
          valid(
              "multi line 2",
              MockGeometries.multiLine(
                  new LineString(Lists.newArrayList(new Position(1, 2), new Position(3, 4))),
                  new LineString(Lists.newArrayList(new Position(5, 6), new Position(7, 8))))),
          valid(
              "polygon",
              new Polygon(
                  Lists.newArrayList(
                      new Position(1, 2),
                      new Position(3, 4),
                      new Position(5.5, 6.6),
                      new Position(1, 2)))),
          valid(
              "polygon w hole",
              new Polygon(
                  Lists.newArrayList(
                      new Position(1, 2),
                      new Position(3, 4),
                      new Position(5.5, 6.6),
                      new Position(1, 2)),
                  Lists.newArrayList(
                      new Position(0.1, 0.2),
                      new Position(0.3, 0.4),
                      new Position(0.5, 0.6),
                      new Position(0.1, 0.2)))),
          valid(
              "multi polygon",
              new MultiPolygon(
                  Lists.newArrayList(
                      new Polygon(
                              Lists.newArrayList(
                                  new Position(1, 2),
                                  new Position(3, 4),
                                  new Position(5.5, 6.6),
                                  new Position(1, 2)))
                          .getCoordinates()))),
          valid("multi polygon empty", new MultiPolygon(Lists.newArrayList())),
          valid("collection empty", MockGeometries.collection()),
          valid(
              "collection point line",
              MockGeometries.collection(MockGeometries.POINT_A, MockGeometries.LINE_AB)),
          valid(
              "collection, point with extra fields",
              MockGeometries.collection(MockGeometries.POINT_A)));
    }

    private static BsonDeserializationTestSuite.ValidSpec<Geometry> valid(
        String name, Geometry geometry) {
      return BsonDeserializationTestSuite.TestSpec.valid(name, geometry);
    }
  }
}
