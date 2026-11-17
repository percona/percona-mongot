package com.xgen.mongot.index;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.FacetBucketBuilder;
import com.xgen.testing.mongot.index.FacetInfoBuilder;
import com.xgen.testing.mongot.index.MetaResultsBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.BsonDouble;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {MetaResultsTest.TestDeserialization.class, MetaResultsTest.TestSerialization.class})
public class MetaResultsTest {

  static final String RESOURCES_PATH = "src/test/unit/resources/index";

  @RunWith(Parameterized.class)
  public static class TestDeserialization {
    private static final String SUITE_NAME = "meta-results-deserialization";

    private static final BsonDeserializationTestSuite<MetaResults> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, MetaResults::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<MetaResults> testSpec;

    public TestDeserialization(BsonDeserializationTestSuite.TestSpecWrapper<MetaResults> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<MetaResults>> data() {
      return TEST_SUITE.withExamples(simple(), withFacet(), withMetrics());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    @Test
    public void testMergeTotalCount() {
      List<MetaResults> metaResultsList = new ArrayList<>();
      metaResultsList.add(new MetaResults(CountResult.totalCount(1)));
      metaResultsList.add(new MetaResults(CountResult.totalCount(2)));
      metaResultsList.add(new MetaResults(CountResult.totalCount(3)));

      var mergedMetaResults = MetaResults.mergeCountResult(metaResultsList);
      Assert.assertEquals(CountResult.totalCount(6), mergedMetaResults.count());
      Assert.assertTrue(mergedMetaResults.facet().isEmpty());
    }

    @Test
    public void testMergeLowerBound() {
      List<MetaResults> metaResultsList = new ArrayList<>();
      metaResultsList.add(new MetaResults(CountResult.lowerBoundCount(1)));
      metaResultsList.add(new MetaResults(CountResult.lowerBoundCount(2)));
      metaResultsList.add(new MetaResults(CountResult.lowerBoundCount(3)));

      var mergedMetaResults = MetaResults.mergeCountResult(metaResultsList);
      Assert.assertEquals(CountResult.lowerBoundCount(6), mergedMetaResults.count());
      Assert.assertTrue(mergedMetaResults.facet().isEmpty());
    }

    @Test
    public void testMergeMixedCount() {
      List<MetaResults> metaResultsList = new ArrayList<>();
      metaResultsList.add(new MetaResults(CountResult.totalCount(1)));
      metaResultsList.add(new MetaResults(CountResult.totalCount(2)));
      metaResultsList.add(new MetaResults(CountResult.lowerBoundCount(3)));

      var mergedMetaResults = MetaResults.mergeCountResult(metaResultsList);
      Assert.assertEquals(CountResult.lowerBoundCount(6), mergedMetaResults.count());
      Assert.assertTrue(mergedMetaResults.facet().isEmpty());
    }

    private static BsonDeserializationTestSuite.ValidSpec<MetaResults> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", MetaResultsBuilder.builder().count(CountResult.lowerBoundCount(1000)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<MetaResults> withFacet() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "withFacet",
          MetaResultsBuilder.builder()
              .count(CountResult.lowerBoundCount(1000))
              .facet(
                  Map.of(
                      "myFacet",
                      FacetInfoBuilder.builder()
                          .buckets(
                              List.of(
                                  FacetBucketBuilder.builder()
                                      .id(new BsonString("category"))
                                      .count(2L)
                                      .build()))
                          .build()))
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<MetaResults> withMetrics() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "withMetrics",
          MetaResultsBuilder.builder()
              .count(CountResult.lowerBoundCount(1000))
              .metrics(Map.of("avgRating", new BsonDouble(4.5), "maxPrice", BsonNull.VALUE))
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class TestSerialization {
    private static final String SUITE_NAME = "meta-results-serialization";
    private static final BsonSerializationTestSuite<MetaResults> TEST_SUITE =
        fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<MetaResults> testSpec;

    public TestSerialization(BsonSerializationTestSuite.TestSpec<MetaResults> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<MetaResults>> data() {
      return Arrays.asList(simple(), withFacet(), withMetrics());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<MetaResults> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          MetaResultsBuilder.builder().count(CountResult.lowerBoundCount(2147483648L)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<MetaResults> withFacet() {
      return BsonSerializationTestSuite.TestSpec.create(
          "withFacet",
          MetaResultsBuilder.builder()
              .count(CountResult.lowerBoundCount(2147483648L))
              .facet(
                  Map.of(
                      "myFacet",
                      FacetInfoBuilder.builder()
                          .buckets(
                              List.of(
                                  FacetBucketBuilder.builder()
                                      .id(new BsonString("category"))
                                      .count(2147483648L)
                                      .build()))
                          .build()))
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<MetaResults> withMetrics() {
      return BsonSerializationTestSuite.TestSpec.create(
          "withMetrics",
          MetaResultsBuilder.builder()
              .count(CountResult.lowerBoundCount(2147483648L))
              .metrics(Map.of("avgRating", new BsonDouble(4.5), "maxPrice", BsonNull.VALUE))
              .build());
    }
  }
}
