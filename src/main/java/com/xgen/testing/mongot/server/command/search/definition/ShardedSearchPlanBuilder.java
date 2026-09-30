package com.xgen.testing.mongot.server.command.search.definition;

import com.xgen.mongot.server.command.search.definition.PlanShardedSearchCommandResponseDefinition;
import com.xgen.mongot.util.Check;
import java.util.List;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;

public abstract class ShardedSearchPlanBuilder {
  protected Optional<String> countType = Optional.empty();

  protected BsonDocument sortSpec =
      PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC;

  public ShardedSearchPlanBuilder countType(String countType) {
    this.countType = Optional.of(countType);
    return this;
  }

  public ShardedSearchPlanBuilder sortSpec(BsonDocument sortSpec) {
    this.sortSpec = sortSpec;
    return this;
  }

  public abstract PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan build();

  public static OperatorMetaPipelineBuilder operator() {
    return new OperatorMetaPipelineBuilder();
  }

  public static CollectorMetaPipelineBuilder collector() {
    return new CollectorMetaPipelineBuilder();
  }

  public static MetricsMetaPipelineBuilder metrics() {
    return new MetricsMetaPipelineBuilder();
  }

  public static class OperatorMetaPipelineBuilder extends ShardedSearchPlanBuilder {

    @Override
    public PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan build() {
      Check.isPresent(this.countType, "countType");

      List<BsonDocument> metaPipeline =
          List.of(
              new BsonDocument(
                  "$group",
                  new BsonDocument()
                      .append(
                          "_id",
                          new BsonDocument()
                              .append("type", new BsonString("$type"))
                              .append("tag", new BsonString("$tag"))
                              .append("bucket", new BsonString("$bucket")))
                      .append("value", new BsonDocument("$sum", new BsonString("$count")))),
              new BsonDocument(
                  "$facet",
                  new BsonDocument(
                      "count",
                      new BsonArray(
                          List.of(
                              new BsonDocument(
                                  "$match",
                                  new BsonDocument(
                                      "_id.type",
                                      new BsonDocument("$eq", new BsonString("count")))))))),
              new BsonDocument(
                  "$replaceWith",
                  new BsonDocument()
                      .append(
                          "count",
                          new BsonDocument(
                              this.countType.get(),
                              new BsonDocument("$first", new BsonString("$count.value"))))));

      return new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(
          metaPipeline, this.sortSpec);
    }
  }

  public static class CollectorMetaPipelineBuilder extends ShardedSearchPlanBuilder {

    private Optional<List<BsonDocument>> facetDocs = Optional.empty();
    private Optional<BsonDocument> bucketDoc = Optional.empty();

    public CollectorMetaPipelineBuilder facetDocs(List<BsonDocument> facetDocs) {
      this.facetDocs = Optional.of(facetDocs);
      return this;
    }

    public CollectorMetaPipelineBuilder bucketDoc(BsonDocument bucketDoc) {
      this.bucketDoc = Optional.of(bucketDoc);
      return this;
    }

    @Override
    public PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan build() {
      Check.isPresent(this.countType, "countType");
      Check.isPresent(this.facetDocs, "facetDocs");
      Check.isPresent(this.bucketDoc, "bucketDoc");

      BsonDocument facetStageDoc =
          new BsonDocument(
              "count",
              new BsonArray(
                  List.of(
                      new BsonDocument(
                          "$match",
                          new BsonDocument(
                              "_id.type", new BsonDocument("$eq", new BsonString("count")))))));

      this.facetDocs
          .get()
          .forEach(
              facetDoc -> {
                String facetName = facetDoc.getFirstKey();
                facetStageDoc.append(facetName, facetDoc.getArray(facetName));
              });

      List<BsonDocument> metaPipeline =
          List.of(
              new BsonDocument(
                  "$group",
                  new BsonDocument()
                      .append(
                          "_id",
                          new BsonDocument()
                              .append("type", new BsonString("$type"))
                              .append("tag", new BsonString("$tag"))
                              .append("bucket", new BsonString("$bucket")))
                      .append("value", new BsonDocument("$sum", new BsonString("$count")))),
              new BsonDocument("$facet", facetStageDoc),
              new BsonDocument(
                  "$replaceWith",
                  new BsonDocument()
                      .append(
                          "count",
                          new BsonDocument(
                              this.countType.get(),
                              new BsonDocument("$first", new BsonString("$count.value"))))
                      .append("facet", this.bucketDoc.get())));

      return new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(
          metaPipeline, this.sortSpec);
    }
  }

  public static class MetricsMetaPipelineBuilder extends ShardedSearchPlanBuilder {

    private Optional<List<BsonDocument>> facetDocs = Optional.empty();
    private Optional<BsonDocument> metricsDoc = Optional.empty();

    /** Each doc is a single-key {@code {<metric>_<component>: [pipeline]}} $facet entry. */
    public MetricsMetaPipelineBuilder facetDocs(List<BsonDocument> facetDocs) {
      this.facetDocs = Optional.of(facetDocs);
      return this;
    }

    /** The {@code metrics} document of the $replaceWith stage. */
    public MetricsMetaPipelineBuilder metricsDoc(BsonDocument metricsDoc) {
      this.metricsDoc = Optional.of(metricsDoc);
      return this;
    }

    @Override
    public PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan build() {
      Check.isPresent(this.countType, "countType");
      Check.isPresent(this.facetDocs, "facetDocs");
      Check.isPresent(this.metricsDoc, "metricsDoc");

      BsonDocument facetStageDoc =
          new BsonDocument(
              "count",
              new BsonArray(
                  List.of(
                      new BsonDocument(
                          "$match",
                          new BsonDocument(
                              "_id.type", new BsonDocument("$eq", new BsonString("count")))))));
      this.facetDocs
          .get()
          .forEach(
              facetDoc -> {
                String key = facetDoc.getFirstKey();
                facetStageDoc.append(key, facetDoc.getArray(key));
              });

      List<BsonDocument> metaPipeline =
          List.of(
              new BsonDocument(
                  "$group",
                  new BsonDocument()
                      .append(
                          "_id",
                          new BsonDocument()
                              .append("type", new BsonString("$type"))
                              .append("tag", new BsonString("$tag"))
                              .append("bucket", new BsonString("$bucket")))
                      .append("value", new BsonDocument("$sum", new BsonString("$count")))
                      .append("metricSum", new BsonDocument("$sum", new BsonString("$value")))
                      .append("metricMin", new BsonDocument("$min", new BsonString("$value")))
                      .append("metricMax", new BsonDocument("$max", new BsonString("$value")))),
              new BsonDocument("$facet", facetStageDoc),
              new BsonDocument(
                  "$replaceWith",
                  new BsonDocument()
                      .append(
                          "count",
                          new BsonDocument(
                              this.countType.get(),
                              new BsonDocument("$first", new BsonString("$count.value"))))
                      .append("metrics", this.metricsDoc.get())));

      return new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(
          metaPipeline, this.sortSpec);
    }
  }
}
