package com.xgen.mongot.server.command.search;

import static com.xgen.mongot.util.Check.checkState;

import com.google.common.collect.ImmutableList;
import com.xgen.mongot.index.IntermediateMetricBucket.Component;
import com.xgen.mongot.index.query.CollectorQuery;
import com.xgen.mongot.index.query.OperatorQuery;
import com.xgen.mongot.index.query.SearchQuery;
import com.xgen.mongot.index.query.collectors.FacetCollector;
import com.xgen.mongot.index.query.collectors.FacetDefinition;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.index.query.collectors.MetricsCollector;
import com.xgen.mongot.index.query.counts.Count;
import com.xgen.mongot.index.query.sort.MongotSortField;
import com.xgen.mongot.index.query.sort.Sort;
import com.xgen.mongot.index.query.sort.SortBetaV1;
import com.xgen.mongot.index.query.sort.SortSpec;
import com.xgen.mongot.server.command.search.definition.PlanShardedSearchCommandResponseDefinition;
import com.xgen.mongot.server.command.search.definition.request.PlanShardedSearchCommandDefinition;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;

public class ShardedSearchPlanner {

  public static final String FACET_TYPE = "facet";

  private static final String METRIC_TYPE = "metric";

  // Names of the extra $group accumulators that merge the payloads of intermediate metric buckets.
  private static final String METRIC_SUM_ACCUMULATOR = "metricSum";
  private static final String METRIC_MIN_ACCUMULATOR = "metricMin";
  private static final String METRIC_MAX_ACCUMULATOR = "metricMax";

  /**
   * Accepts a deserialized query and the supported searchFeatures of a MongoDB deployment and
   * generates a ShardedSearchPlan.
   */
  static PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan planSearch(
      SearchQuery queryDefinition, PlanShardedSearchCommandDefinition.SearchFeatures searchFeatures)
      throws ShardedSearchPlannerException {
    BsonDocument sortSpec = validateQueryAndConstructSortSpec(searchFeatures, queryDefinition);
    List<BsonDocument> metaPipeline = getShardedSearchMetaPipeline(queryDefinition);
    return new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(metaPipeline, sortSpec);
  }

  /**
   * Validates that the query can be satisfied by cross-referencing its specifications with the
   * supported searchFeatures of a particular MongoDB deployment and generates a SortSpec.
   */
  private static BsonDocument validateQueryAndConstructSortSpec(
      PlanShardedSearchCommandDefinition.SearchFeatures searchFeatures, SearchQuery query)
      throws ShardedSearchPlannerException {
    Optional<SortSpec> sortSpec = query.sortSpec();
    if (sortSpec.isEmpty()) {
      return PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC;
    }

    return switch (sortSpec.get()) {
      case SortBetaV1 sortBetaV1 ->
          // Do not change sortBetaV1 behavior on sharded clusters; sharded merge behavior may be
          // incorrect. See https://jira.mongodb.org/browse/CLOUDP-184455 for more detail.
          PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC;
      case Sort sort -> {
        if (!searchFeatures.supportsShardedSort()) {
          throw new ShardedSearchPlannerException(
              "Sharded sort is not supported for this MongoDB deployment");
        }
        yield constructSortSpec(sort);
      }
    };
  }

  /**
   * Creates a pipeline that details how to merge metadata results against a sharded collection. See
   * <a href="https://github.com/10gen/mongot/blob/master/docs/protocol/search.md#sharded-search">*
   * this section</a> in the search docs for more details.
   *
   * @param query the $search query made against a sharded collection
   * @return the pipeline to execute over the sharded metadata results
   */
  private static List<BsonDocument> getShardedSearchMetaPipeline(SearchQuery query) {
    String countType = query.count().type() == Count.Type.LOWER_BOUND ? "lowerBound" : "total";

    // If the Query is a CollectorQuery, then get facet definitions from the FacetCollector and
    // build the facet results pipeline in the $facet stage and facet bucket formatting in the
    // $replaceWith stage.
    return switch (query) {
      case CollectorQuery collectorQuery -> {
        switch (collectorQuery.collector()) {
          case FacetCollector facetCollector -> {
            Map<String, FacetDefinition> facetDefinitions = facetCollector.facetDefinitions();
            yield buildMetaPipeline(countType, Optional.of(facetDefinitions));
          }
          case MetricsCollector metricsCollector -> {
            yield buildMetricsMetaPipeline(countType, metricsCollector.metricDefinitions());
          }
        }
      }
      case OperatorQuery operatorQuery -> buildMetaPipeline(countType, Optional.empty());
    };
  }

  private static BsonDocument constructSortSpec(Sort sort) {
    checkState(!sort.getSortFields().isEmpty(), "There must be 1 or more SortFields");
    BsonDocument doc = new BsonDocument();
    String sortSpecKeyFormat = "$searchSortValues._%s";
    ImmutableList<MongotSortField> sortFields = sort.getSortFields();
    for (int i = 0; i < sortFields.size(); i++) {
      doc.put(
          String.format(sortSpecKeyFormat, i),
          new BsonInt32(sortFields.get(i).options().order().intValue));
    }

    return doc;
  }

  private static List<BsonDocument> buildMetaPipeline(
      String countType, Optional<Map<String, FacetDefinition>> facetDefinitions) {
    return List.of(
        getGroupStage(),
        getFacetStage(facetDefinitions),
        getReplaceWithStage(countType, facetDefinitions));
  }

  /**
   * Builds the pipeline merging intermediate metric buckets (see {@link IntermediateMetricBucket})
   * from every shard: the $group stage totals each metric component, the $facet stage selects each
   * component a metric needs, and the $replaceWith stage combines them into the final value.
   */
  private static List<BsonDocument> buildMetricsMetaPipeline(
      String countType, Map<String, MetricDefinition> metricDefinitions) {
    // Sort by metric name so the generated pipeline is deterministic.
    Map<String, MetricDefinition> sortedDefinitions = new TreeMap<>(metricDefinitions);
    return List.of(
        getMetricsGroupStage(),
        getMetricsFacetStage(sortedDefinitions),
        getMetricsReplaceWithStage(countType, sortedDefinitions));
  }

  /** The metrics $group stage additionally sums / mins / maxes each bucket's double payload. */
  private static BsonDocument getMetricsGroupStage() {
    BsonDocument stage = getGroupStage();
    BsonString valueRef = new BsonString("$value");
    stage
        .getDocument("$group")
        .append(METRIC_SUM_ACCUMULATOR, new BsonDocument("$sum", valueRef))
        .append(METRIC_MIN_ACCUMULATOR, new BsonDocument("$min", valueRef))
        .append(METRIC_MAX_ACCUMULATOR, new BsonDocument("$max", valueRef));
    return stage;
  }

  /** The metrics $facet stage isolates every (metric, component) group the metric needs. */
  private static BsonDocument getMetricsFacetStage(Map<String, MetricDefinition> definitions) {
    BsonDocument stage = getFacetStage(Optional.empty());
    BsonDocument facets = stage.getDocument("$facet");
    definitions.forEach(
        (name, definition) -> {
          for (Component component : componentsFor(definition.type())) {
            facets.append(
                metricFacetKey(name, component),
                new BsonArray(
                    List.of(
                        new BsonDocument(
                            "$match",
                            new BsonDocument()
                                .append(
                                    "_id.type",
                                    new BsonDocument("$eq", new BsonString(METRIC_TYPE)))
                                .append("_id.tag", new BsonDocument("$eq", new BsonString(name)))
                                .append(
                                    "_id.bucket",
                                    new BsonDocument(
                                        "$eq", new BsonString(component.getName())))))));
          }
        });
    return stage;
  }

  /** The metrics $replaceWith stage computes each metric's final value from its components. */
  private static BsonDocument getMetricsReplaceWithStage(
      String countType, Map<String, MetricDefinition> definitions) {
    BsonDocument stage = getReplaceWithStage(countType, Optional.empty());
    BsonDocument metrics = new BsonDocument();
    definitions.forEach(
        (name, definition) -> metrics.append(name, metricExpression(name, definition.type())));
    stage.getDocument("$replaceWith").append("metrics", metrics);
    return stage;
  }

  private static List<Component> componentsFor(MetricDefinition.Type type) {
    return switch (type) {
      case AVG -> List.of(Component.SUM, Component.COUNT);
      case SUM -> List.of(Component.SUM);
      case MIN -> List.of(Component.MIN);
      case MAX -> List.of(Component.MAX);
    };
  }

  /**
   * avg/min/max resolve to null when no shard saw a value (mirroring MQL); sum resolves to 0.0
   * because every shard always emits a sum bucket.
   */
  private static BsonValue metricExpression(String name, MetricDefinition.Type type) {
    return switch (type) {
      case SUM -> firstOf(name, Component.SUM, METRIC_SUM_ACCUMULATOR);
      case MIN -> nullIfMissing(firstOf(name, Component.MIN, METRIC_MIN_ACCUMULATOR));
      case MAX -> nullIfMissing(firstOf(name, Component.MAX, METRIC_MAX_ACCUMULATOR));
      case AVG -> {
        BsonDocument count = firstOf(name, Component.COUNT, "value");
        BsonDocument sum = firstOf(name, Component.SUM, METRIC_SUM_ACCUMULATOR);
        yield new BsonDocument(
            "$cond",
            new BsonDocument()
                .append(
                    "if",
                    new BsonDocument("$gt", new BsonArray(List.of(count, new BsonInt32(0)))))
                .append("then", new BsonDocument("$divide", new BsonArray(List.of(sum, count))))
                .append("else", BsonNull.VALUE));
      }
    };
  }

  /**
   * Shards emit min/max buckets only when they saw a value, so with no values anywhere the $facet
   * branch is empty and $first yields missing, which $replaceWith drops from the output. This
   * turns missing into an explicit null, matching the unsharded result.
   */
  private static BsonDocument nullIfMissing(BsonDocument expression) {
    return new BsonDocument("$ifNull", new BsonArray(List.of(expression, BsonNull.VALUE)));
  }

  private static BsonDocument firstOf(String name, Component component, String accumulator) {
    return new BsonDocument(
        "$first", new BsonString("$" + metricFacetKey(name, component) + "." + accumulator));
  }

  private static String metricFacetKey(String name, Component component) {
    return name + "_" + component.getName();
  }

  /** The $group stage aggregates counts for each (type, path, bucket) combination. */
  private static BsonDocument getGroupStage() {
    BsonDocument idDoc =
        new BsonDocument()
            .append("type", new BsonString("$type"))
            .append("tag", new BsonString("$tag"))
            .append("bucket", new BsonString("$bucket"));
    BsonDocument valueDoc = new BsonDocument("$sum", new BsonString("$count"));
    return new BsonDocument(
        "$group", new BsonDocument().append("_id", idDoc).append("value", valueDoc));
  }

  /** The $facet stage matches the grouped buckets and computes the merged counts and facets. */
  private static BsonDocument getFacetStage(
      Optional<Map<String, FacetDefinition>> facetDefinitions) {
    BsonDocument matchDoc =
        new BsonDocument(
            "$match",
            new BsonDocument("_id.type", new BsonDocument("$eq", new BsonString("count"))));
    BsonElement countDoc = new BsonElement("count", new BsonArray(List.of(matchDoc)));
    List<BsonElement> facets =
        facetDefinitions.map(ShardedSearchPlanner::getFacets).orElseGet(Collections::emptyList);
    // The count pipeline goes before the facet results pipeline(s).
    List<BsonElement> facetDoc =
        Stream.of(List.of(countDoc), facets)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    return new BsonDocument("$facet", new BsonDocument(facetDoc));
  }

  /** The $replaceWith stage formats the output document. */
  private static BsonDocument getReplaceWithStage(
      String countType, Optional<Map<String, FacetDefinition>> facetDefinitions) {
    BsonDocument countDoc =
        new BsonDocument(countType, new BsonDocument("$first", new BsonString("$count.value")));
    List<BsonElement> buckets =
        facetDefinitions.map(ShardedSearchPlanner::getBuckets).orElseGet(Collections::emptyList);
    BsonDocument replaceWithDoc =
        buckets.isEmpty()
            ? new BsonDocument().append("count", countDoc)
            : new BsonDocument()
                .append("count", countDoc)
                .append("facet", new BsonDocument(buckets));

    return new BsonDocument("$replaceWith", replaceWithDoc);
  }

  private static List<BsonElement> getBuckets(Map<String, FacetDefinition> facetDefinitions) {

    // Map the grouped buckets from the $group stage into buckets that only contain the _id and
    // count.
    return facetDefinitions.keySet().stream()
        .map(
            name ->
                new BsonElement(
                    name,
                    new BsonDocument(
                        "buckets",
                        new BsonDocument(
                            "$map",
                            new BsonDocument()
                                .append("input", new BsonString("$" + name))
                                .append("as", new BsonString("bucket"))
                                .append(
                                    "in",
                                    new BsonDocument()
                                        .append("_id", new BsonString("$$bucket._id.bucket"))
                                        .append("count", new BsonString("$$bucket.value")))))))
        .collect(Collectors.toList());
  }

  /** Get facet results pipeline(s) for the $facet stage. */
  private static List<BsonElement> getFacets(Map<String, FacetDefinition> facetDefinitions) {
    return facetDefinitions.entrySet().stream()
        .map(
            definition -> {
              var name = definition.getKey();
              var facet = getFacetBuckets(definition.getValue(), definition.getKey());
              return new BsonElement(name, facet);
            })
        .collect(Collectors.toList());
  }

  private static BsonArray getFacetBuckets(FacetDefinition facet, String facetName) {
    return switch (facet) {
      case FacetDefinition.StringFacetDefinition stringFacetDefinition ->
          buildStringFacetBucket(stringFacetDefinition, facetName);
      case FacetDefinition.DateFacetDefinition dateFacetDefinition ->
          buildDateFacetBucket(facetName);
      case FacetDefinition.NumericFacetDefinition numericFacetDefinition ->
          buildNumericFacetBucket(facetName);
    };
  }

  private static BsonArray buildStringFacetBucket(
      FacetDefinition.StringFacetDefinition facet, String facetName) {
    return new BsonArray(
        List.of(
            new BsonDocument(
                "$match",
                new BsonDocument()
                    .append("_id.type", new BsonDocument("$eq", new BsonString(FACET_TYPE)))
                    .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
            new BsonDocument(
                "$sort",
                new BsonDocument("value", new BsonInt32(-1)).append("_id", new BsonInt32(1))),
            new BsonDocument("$limit", new BsonInt32(facet.numBuckets()))));
  }

  private static BsonArray buildNumericFacetBucket(String facetName) {
    return new BsonArray(
        List.of(
            new BsonDocument(
                "$match",
                new BsonDocument()
                    .append("_id.type", new BsonDocument("$eq", new BsonString(FACET_TYPE)))
                    .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
            new BsonDocument("$sort", new BsonDocument("_id.bucket", new BsonInt32(1)))));
  }

  private static BsonArray buildDateFacetBucket(String facetName) {
    return new BsonArray(
        List.of(
            new BsonDocument(
                "$match",
                new BsonDocument()
                    .append("_id.type", new BsonDocument("$eq", new BsonString(FACET_TYPE)))
                    .append("_id.tag", new BsonDocument("$eq", new BsonString(facetName)))),
            // Convert to number in order to get the same sorting order as number facets, such that
            // the default bucket is always last.
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
            new BsonDocument("$sort", new BsonDocument("sortField", new BsonInt32(1)))));
  }
}
