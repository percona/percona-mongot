package com.xgen.mongot.index.lucene.explain.information;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.query.points.DoublePoint;
import com.xgen.mongot.index.query.points.LongPoint;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.lucene.explain.information.ExplainInformationTestUtil;
import com.xgen.testing.mongot.index.lucene.explain.information.LongDistanceFeatureQueryBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      LongDistanceFeatureQuerySpecTest.DeserializationTest.class,
      LongDistanceFeatureQuerySpecTest.SerializationTest.class,
    })
public class LongDistanceFeatureQuerySpecTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "long-dist-feature-query-deserialization";
    private static final BsonDeserializationTestSuite<LongDistanceFeatureQuerySpec> TEST_SUITE =
        fromDocument(
            ExplainInformationTestUtil.RESOURCES_PATH,
            SUITE_NAME,
            LongDistanceFeatureQuerySpec::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<LongDistanceFeatureQuerySpec>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<LongDistanceFeatureQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<LongDistanceFeatureQuerySpec>>
        data() {
      return TEST_SUITE.withExamples(
          simple(), missingOrigin(), missingPivotDistance(), missingRepresentation());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<LongDistanceFeatureQuerySpec> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          LongDistanceFeatureQueryBuilder.builder()
              .path("a")
              .origin(new DoublePoint(1.0))
              .pivotDistance(new DoublePoint(2.0))
              .representation(Representation.DOUBLE)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LongDistanceFeatureQuerySpec>
        missingOrigin() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-origin",
          LongDistanceFeatureQueryBuilder.builder()
              .path("a")
              .pivotDistance(new DoublePoint(2.0))
              .representation(Representation.DOUBLE)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LongDistanceFeatureQuerySpec>
        missingPivotDistance() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-pivotDistance",
          LongDistanceFeatureQueryBuilder.builder()
              .path("a")
              .origin(new DoublePoint(1.0))
              .representation(Representation.DOUBLE)
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<LongDistanceFeatureQuerySpec>
        missingRepresentation() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-representation",
          LongDistanceFeatureQueryBuilder.builder()
              .path("a")
              .origin(new DoublePoint(1.0))
              .pivotDistance(new DoublePoint(2.0))
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "long-dist-feature-query-serialization";
    private static final BsonSerializationTestSuite<LongDistanceFeatureQuerySpec> TEST_SUITE =
        fromEncodable(ExplainInformationTestUtil.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<LongDistanceFeatureQuerySpec> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<LongDistanceFeatureQuerySpec> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<LongDistanceFeatureQuerySpec>>
        data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<LongDistanceFeatureQuerySpec> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          LongDistanceFeatureQueryBuilder.builder()
              .path("a")
              .origin(new LongPoint(1L))
              .pivotDistance(new LongPoint(2L))
              .representation(Representation.INT_64)
              .build());
    }
  }
}
