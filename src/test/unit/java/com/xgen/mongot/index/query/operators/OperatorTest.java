package com.xgen.mongot.index.query.operators;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.query.points.DoublePoint;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.index.query.operators.SpanOperatorBuilder;
import com.xgen.testing.mongot.index.query.shapes.ShapeBuilder;
import com.xgen.testing.mongot.util.geo.MockGeometries;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {OperatorTest.DeserializationTest.class, OperatorTest.SerializationTest.class})
public class OperatorTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {

    private static final String SUITE_NAME = "operator-deserialization";
    private static final BsonDeserializationTestSuite<Operator> TEST_SUITE =
        fromDocument(DefinitionTests.RESOURCES_PATH, SUITE_NAME, Operator::exactlyOneFromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<Operator> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<Operator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<Operator>> data() {
      return TEST_SUITE.withExamples(
          compound(),
          exists(),
          geoShape(),
          geoWithin(),
          near(),
          phrase(),
          queryString(),
          range(),
          regex(),
          search(),
          span(),
          term(),
          text(),
          wildcard());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> compound() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "compound",
          OperatorBuilder.compound()
              .filter(OperatorBuilder.text().path("title").query("godfather").build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> exists() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "exists", OperatorBuilder.exists().path("title").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> geoShape() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "geoShape",
          OperatorBuilder.geoShape()
              .path("location")
              .geometry(ShapeBuilder.geometry(MockGeometries.POINT_A))
              .relation(GeoShapeOperator.Relation.CONTAINS)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> geoWithin() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "geoWithin",
          OperatorBuilder.geoWithin().path("location").shape(ShapeBuilder.circle(1, 2, 3)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> near() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "near",
          OperatorBuilder.near().path("point").origin(new DoublePoint(13d)).pivot(26).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> phrase() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "phrase", OperatorBuilder.phrase().path("title").query("godfather").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> queryString() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "queryString",
          OperatorBuilder.queryString().defaultPath("title").query("foo AND bar").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> range() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "range",
          OperatorBuilder.range()
              .path("point")
              .numericBounds(Optional.empty(), Optional.of(new DoublePoint(13d)), false, false)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> regex() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "regex", OperatorBuilder.regex().path("title").query("godfather").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> search() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "search", OperatorBuilder.search().path("title").query("godfather").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> span() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "span",
          SpanOperatorBuilder.first()
              .operator(
                  SpanOperatorBuilder.term()
                      .term(OperatorBuilder.term().path("title").query("godfather").build())
                      .build())
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> term() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "term", OperatorBuilder.term().path("title").query("godfather").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> text() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "text", OperatorBuilder.text().path("title").query("godfather").build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Operator> wildcard() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "wildcard", OperatorBuilder.wildcard().path("title").query("godfather").build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "operator-serialization";
    private static final BsonSerializationTestSuite<Operator> TEST_SUITE =
        fromEncodable(DefinitionTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<Operator> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<Operator> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<Operator>> data() {
      return Arrays.asList(
          compound(),
          equals(),
          exists(),
          geoShape(),
          geoWithin(),
          near(),
          phrase(),
          queryString(),
          range(),
          regex(),
          search(),
          span(),
          term(),
          text(),
          wildcard());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> compound() {
      return BsonSerializationTestSuite.TestSpec.create(
          "compound",
          OperatorBuilder.compound()
              .filter(OperatorBuilder.text().path("title").query("filter").build())
              .must(OperatorBuilder.text().path("title").query("must").build())
              .mustNot(OperatorBuilder.text().path("title").query("mustNot").build())
              .should(OperatorBuilder.text().path("title").query("should").build())
              .minimumShouldMatch(13)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> equals() {
      return BsonSerializationTestSuite.TestSpec.create(
          "equals", OperatorBuilder.equals().path("hasPaid").value(true).build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> exists() {
      return BsonSerializationTestSuite.TestSpec.create(
          "exists", OperatorBuilder.exists().path("title").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> geoShape() {
      return BsonSerializationTestSuite.TestSpec.create(
          "geoShape",
          OperatorBuilder.geoShape()
              .path("location")
              .geometry(ShapeBuilder.geometry(MockGeometries.POINT_A))
              .relation(GeoShapeOperator.Relation.CONTAINS)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> geoWithin() {
      return BsonSerializationTestSuite.TestSpec.create(
          "geoWithin",
          OperatorBuilder.geoWithin().path("location").shape(ShapeBuilder.circle(1, 2, 3)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> near() {
      return BsonSerializationTestSuite.TestSpec.create(
          "near",
          OperatorBuilder.near().path("point").origin(new DoublePoint(13d)).pivot(26).build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> phrase() {
      return BsonSerializationTestSuite.TestSpec.create(
          "phrase", OperatorBuilder.phrase().path("title").query("godfather").slop(13).build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> queryString() {
      return BsonSerializationTestSuite.TestSpec.create(
          "queryString",
          OperatorBuilder.queryString().defaultPath("title").query("foo AND bar").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> range() {
      return BsonSerializationTestSuite.TestSpec.create(
          "range",
          OperatorBuilder.range()
              .path("point")
              .numericBounds(Optional.empty(), Optional.of(new DoublePoint(13d)), false, false)
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> regex() {
      return BsonSerializationTestSuite.TestSpec.create(
          "regex", OperatorBuilder.regex().path("title").query("godfather").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> search() {
      return BsonSerializationTestSuite.TestSpec.create(
          "search", OperatorBuilder.search().path("title").query("godfather").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> span() {
      return BsonSerializationTestSuite.TestSpec.create(
          "span",
          SpanOperatorBuilder.first()
              .operator(
                  SpanOperatorBuilder.term()
                      .term(OperatorBuilder.term().path("title").query("godfather").build())
                      .build())
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> term() {
      return BsonSerializationTestSuite.TestSpec.create(
          "term", OperatorBuilder.term().path("title").query("godfather").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> text() {
      return BsonSerializationTestSuite.TestSpec.create(
          "text", OperatorBuilder.text().path("title").query("godfather").build());
    }

    private static BsonSerializationTestSuite.TestSpec<Operator> wildcard() {
      return BsonSerializationTestSuite.TestSpec.create(
          "wildcard", OperatorBuilder.wildcard().path("title").query("godfather").build());
    }
  }
}
