package com.xgen.mongot.server.command.search;

import com.xgen.mongot.index.query.OperatorQuery;
import com.xgen.mongot.index.query.SearchQuery;
import com.xgen.mongot.index.query.sort.UserFieldSortOptions;
import com.xgen.mongot.server.command.search.definition.PlanShardedSearchCommandResponseDefinition;
import com.xgen.mongot.server.command.search.definition.request.PlanShardedSearchCommandDefinition;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.query.OperatorQueryBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.index.query.sort.SortFieldBuilder;
import com.xgen.testing.mongot.index.query.sort.SortSpecBuilder;
import com.xgen.testing.mongot.server.command.search.definition.ShardedSearchPlanBuilder;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ShardedSearchPlannerTest {

  private static BsonDocument getOperatorQuery() {
    return new BsonDocument()
        .append(
            "text",
            new BsonDocument()
                .append("path", new BsonString("title"))
                .append("query", new BsonString("godfather")));
  }

  private static BsonDocument getOperatorQueryWithCount() {
    return new BsonDocument()
        .append(
            "text",
            new BsonDocument()
                .append("path", new BsonString("title"))
                .append("query", new BsonString("godfather")))
        .append(
            "count",
            new BsonDocument()
                .append("type", new BsonString("total"))
                .append("threshold", new BsonInt32(5000)));
  }

  private static BsonDocument getSimpleStringFacet() {
    return new BsonDocument(
        "directorFacet",
        new BsonDocument()
            .append("type", new BsonString("string"))
            .append("path", new BsonString("director")));
  }

  private static BsonDocument getStringFacetWithNumBuckets() {
    return new BsonDocument(
        "directorFacet",
        new BsonDocument()
            .append("type", new BsonString("string"))
            .append("path", new BsonString("director"))
            .append("numBuckets", new BsonInt32(1000)));
  }

  private static BsonDocument getSimpleNumericFacet() {
    return new BsonDocument(
        "ratingFacet",
        new BsonDocument()
            .append("type", new BsonString("number"))
            .append("path", new BsonString("rating"))
            .append("boundaries", new BsonArray(List.of(new BsonInt32(1), new BsonInt32(2)))));
  }

  private static BsonDocument getSimpleDateFacet() {
    return new BsonDocument(
        "eventFacet",
        new BsonDocument()
            .append("type", new BsonString("date"))
            .append("path", new BsonString("start"))
            .append(
                "boundaries",
                new BsonArray(List.of(new BsonDateTime(2021), new BsonDateTime(2022)))));
  }

  private static BsonDocument getMultipleFacets() {
    return new BsonDocument()
        .append("directorFacet", getSimpleStringFacet().getDocument("directorFacet"))
        .append("ratingFacet", getSimpleNumericFacet().getDocument("ratingFacet"))
        .append("eventFacet", getSimpleDateFacet().getDocument("eventFacet"));
  }

  private static BsonDocument getMultipleStringFacets() {
    return new BsonDocument()
        .append("directorFacet", getSimpleStringFacet().getDocument("directorFacet"))
        .append(
            "genreFacet",
            new BsonDocument()
                .append("type", new BsonString("string"))
                .append("path", new BsonString("genre"))
                .append("numBuckets", new BsonInt32(50)))
        .append(
            "languageFacet",
            new BsonDocument()
                .append("type", new BsonString("string"))
                .append("path", new BsonString("language")));
  }

  private static BsonDocument createFacetQuery(BsonDocument facets) {
    return new BsonDocument()
        .append(
            "facet",
            new BsonDocument().append("operator", getOperatorQuery()).append("facets", facets));
  }

  private static BsonDocument createStringFacetDoc(String facetName, int numBuckets) {
    return new BsonDocument(
        facetName,
        new BsonArray(
            List.of(
                new BsonDocument(
                    "$match",
                    new BsonDocument()
                        .append("_id.type", new BsonDocument("$eq", new BsonString("facet")))
                        .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
                new BsonDocument(
                    "$sort",
                    new BsonDocument("value", new BsonInt32(-1)).append("_id", new BsonInt32(1))),
                new BsonDocument("$limit", new BsonInt32(numBuckets)))));
  }

  private static BsonDocument createBucketDoc(String facetName) {
    return new BsonDocument(
        "buckets",
        new BsonDocument(
            "$map",
            new BsonDocument()
                .append("input", new BsonString("$" + facetName))
                .append("as", new BsonString("bucket"))
                .append(
                    "in",
                    new BsonDocument()
                        .append("_id", new BsonString("$$bucket._id.bucket"))
                        .append("count", new BsonString("$$bucket.value")))));
  }

  private static BsonDocument createNumericFacetDoc(String facetName) {
    return new BsonDocument(
        facetName,
        new BsonArray(
            List.of(
                new BsonDocument(
                    "$match",
                    new BsonDocument()
                        .append("_id.type", new BsonDocument("$eq", new BsonString("facet")))
                        .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
                new BsonDocument("$sort", new BsonDocument("_id.bucket", new BsonInt32(1))))));
  }

  private static BsonDocument createDateFacetDoc(String facetName) {
    return new BsonDocument(
        facetName,
        new BsonArray(
            List.of(
                new BsonDocument(
                    "$match",
                    new BsonDocument()
                        .append("_id.type", new BsonDocument("$eq", new BsonString("facet")))
                        .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
                new BsonDocument(
                    "$addFields",
                    new BsonDocument(
                        "sortField",
                        new BsonDocument(
                            "$convert",
                            new BsonDocument()
                                .append("input", new BsonString("$_id.bucket"))
                                .append("to", new BsonString("long"))
                                .append("onError", new BsonString("$_id.bucket"))))),
                new BsonDocument("$sort", new BsonDocument("sortField", new BsonInt32(1))))));
  }

  @Test
  public void testSortSpec() throws Exception {
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(getOperatorQuery()),
            new PlanShardedSearchCommandDefinition.SearchFeatures(0));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.operator()
            .countType("lowerBound")
            .sortSpec(
                PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC)
            .build();
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testSortInQueryWithSearchFeature() throws ShardedSearchPlannerException {
    OperatorQuery query =
        OperatorQueryBuilder.builder()
            .operator(OperatorBuilder.text().path("title").query("godfather").build())
            .returnStoredSource(false)
            .sort(
                SortSpecBuilder.builder()
                    .sortField(
                        SortFieldBuilder.builder()
                            .path("a")
                            .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                            .build())
                    .sortField(
                        SortFieldBuilder.builder()
                            .path("b")
                            .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                            .build())
                    .buildSort())
            .build();

    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            query, new PlanShardedSearchCommandDefinition.SearchFeatures(1));
    BsonDocument expected =
        new BsonDocument()
            .append("$searchSortValues._0", new BsonInt32(1))
            .append("$searchSortValues._1", new BsonInt32(-1));
    Assert.assertEquals(expected, result.sortSpec);
  }

  @Test
  public void testSortInQueryWithoutSearchFeature() {
    OperatorQuery query =
        OperatorQueryBuilder.builder()
            .operator(OperatorBuilder.text().path("title").query("godfather").build())
            .returnStoredSource(false)
            .sort(
                SortSpecBuilder.builder()
                    .sortField(
                        SortFieldBuilder.builder()
                            .path("a")
                            .sortOption(UserFieldSortOptions.DEFAULT_ASC)
                            .build())
                    .sortField(
                        SortFieldBuilder.builder()
                            .path("b")
                            .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                            .build())
                    .buildSort())
            .build();

    TestUtils.assertThrows(
        "Sharded sort is not supported for this MongoDB deployment",
        ShardedSearchPlannerException.class,
        () ->
            ShardedSearchPlanner.planSearch(
                query, new PlanShardedSearchCommandDefinition.SearchFeatures(0)));
  }

  @Test
  public void testSortBetaInQuery() throws ShardedSearchPlannerException {
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            OperatorQueryBuilder.builder()
                .operator(OperatorBuilder.text().path("title").query("godfather").build())
                .returnStoredSource(false)
                .sortBetaV1(
                    SortSpecBuilder.builder()
                        .sortField(
                            SortFieldBuilder.builder()
                                .path("title")
                                .sortOption(UserFieldSortOptions.DEFAULT_DESC)
                                .build())
                        .buildSortBetaV1())
                .build(),
            new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.operator()
            .countType("lowerBound")
            .sortSpec(
                PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC)
            .build();
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testOperator() throws Exception {
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(getOperatorQuery()),
            new PlanShardedSearchCommandDefinition.SearchFeatures(0));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.operator().countType("lowerBound").build();
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testOperatorWithCount() throws Exception {
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(getOperatorQueryWithCount()),
            new PlanShardedSearchCommandDefinition.SearchFeatures(0));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.operator().countType("total").build();
    Assert.assertEquals(expected, result);
  }

  @Test
  public void testStringFacet() throws Exception {
    var query = createFacetQuery(getSimpleStringFacet());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs = List.of(createStringFacetDoc("directorFacet", 10));
    var bucketDoc = new BsonDocument("directorFacet", createBucketDoc("directorFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  @Test
  public void testStringFacetWithNumBuckets() throws Exception {
    var query = createFacetQuery(getStringFacetWithNumBuckets());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs = List.of(createStringFacetDoc("directorFacet", 1000));
    var bucketDoc = new BsonDocument("directorFacet", createBucketDoc("directorFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  private static BsonDocument createMetricFacetDoc(String metricName, String component) {
    return new BsonDocument(
        metricName + "_" + component,
        new BsonArray(
            List.of(
                new BsonDocument(
                    "$match",
                    new BsonDocument()
                        .append("_id.type", new BsonDocument("$eq", new BsonString("metric")))
                        .append("_id.tag", new BsonDocument("$eq", new BsonString(metricName)))
                        .append(
                            "_id.bucket", new BsonDocument("$eq", new BsonString(component)))))));
  }

  private static BsonDocument first(String metricName, String component, String accumulator) {
    return new BsonDocument(
        "$first", new BsonString("$" + metricName + "_" + component + "." + accumulator));
  }

  private static BsonDocument nullIfMissing(BsonDocument expression) {
    return new BsonDocument("$ifNull", new BsonArray(List.of(expression, BsonNull.VALUE)));
  }

  @Test
  public void testMetrics() throws Exception {
    var query =
        new BsonDocument(
            "metrics",
            new BsonDocument()
                .append("operator", getOperatorQuery())
                .append(
                    "metrics",
                    new BsonDocument()
                        .append(
                            "maxPrice",
                            new BsonDocument()
                                .append("type", new BsonString("max"))
                                .append("path", new BsonString("price")))
                        .append(
                            "avgRating",
                            new BsonDocument()
                                .append("type", new BsonString("avg"))
                                .append("path", new BsonString("rating")))
                        .append(
                            "minPrice",
                            new BsonDocument()
                                .append("type", new BsonString("min"))
                                .append("path", new BsonString("price")))));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    // Metrics are emitted in name order: avgRating (sum, count), maxPrice (max), minPrice (min).
    var facetDocs =
        List.of(
            createMetricFacetDoc("avgRating", "sum"),
            createMetricFacetDoc("avgRating", "count"),
            createMetricFacetDoc("maxPrice", "max"),
            createMetricFacetDoc("minPrice", "min"));
    var metricsDoc =
        new BsonDocument()
            .append(
                "avgRating",
                new BsonDocument(
                    "$cond",
                    new BsonDocument()
                        .append(
                            "if",
                            new BsonDocument(
                                "$gt",
                                new BsonArray(
                                    List.of(
                                        first("avgRating", "count", "value"),
                                        new BsonInt32(0)))))
                        .append(
                            "then",
                            new BsonDocument(
                                "$divide",
                                new BsonArray(
                                    List.of(
                                        first("avgRating", "sum", "metricSum"),
                                        first("avgRating", "count", "value")))))
                        .append("else", BsonNull.VALUE)))
            // min/max buckets are absent when no shard saw a value; $ifNull keeps the field as null.
            .append("maxPrice", nullIfMissing(first("maxPrice", "max", "metricMax")))
            .append("minPrice", nullIfMissing(first("minPrice", "min", "metricMin")));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.metrics()
            .facetDocs(facetDocs)
            .metricsDoc(metricsDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  @Test
  public void testNumericFacet() throws Exception {
    var query = createFacetQuery(getSimpleNumericFacet());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs = List.of(createNumericFacetDoc("ratingFacet"));
    var bucketDoc = new BsonDocument("ratingFacet", createBucketDoc("ratingFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  @Test
  public void testDateFacet() throws Exception {
    var query = createFacetQuery(getSimpleDateFacet());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs = List.of(createDateFacetDoc("eventFacet"));
    var bucketDoc = new BsonDocument("eventFacet", createBucketDoc("eventFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  @Test
  public void testMultipleFacets() throws Exception {
    var query = createFacetQuery(getMultipleFacets());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs =
        List.of(
            createStringFacetDoc("directorFacet", 10),
            createNumericFacetDoc("ratingFacet"),
            createDateFacetDoc("eventFacet"));
    var bucketDoc =
        new BsonDocument()
            .append("directorFacet", createBucketDoc("directorFacet"))
            .append("ratingFacet", createBucketDoc("ratingFacet"))
            .append("eventFacet", createBucketDoc("eventFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }

  @Test
  public void testMultipleStringFacets() throws Exception {
    var query = createFacetQuery(getMultipleStringFacets());
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan result =
        ShardedSearchPlanner.planSearch(
            SearchQuery.fromBson(query), new PlanShardedSearchCommandDefinition.SearchFeatures(0));

    var facetDocs =
        List.of(
            createStringFacetDoc("directorFacet", 10),
            createStringFacetDoc("genreFacet", 50),
            createStringFacetDoc("languageFacet", 10));
    var bucketDoc =
        new BsonDocument()
            .append("directorFacet", createBucketDoc("directorFacet"))
            .append("genreFacet", createBucketDoc("genreFacet"))
            .append("languageFacet", createBucketDoc("languageFacet"));
    PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan expected =
        ShardedSearchPlanBuilder.collector()
            .facetDocs(facetDocs)
            .bucketDoc(bucketDoc)
            .countType("lowerBound")
            .build();

    Assert.assertEquals(expected, result);
  }
}
