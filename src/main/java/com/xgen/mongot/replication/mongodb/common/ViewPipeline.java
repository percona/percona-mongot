package com.xgen.mongot.replication.mongodb.common;

import static com.xgen.mongot.util.Check.checkArg;

import com.google.common.collect.ImmutableList;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.xgen.mongot.index.definition.ViewDefinition;
import com.xgen.mongot.index.definition.ViewDefinition.SupportedStage;
import com.xgen.mongot.util.Check;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * This class extracts pipeline from {@link ViewDefinition} and performs necessary transformations
 * to perform replication queries in regular and change stream modes.
 */
public class ViewPipeline {

  private static final String FULL_DOCUMENT_PREFIX = "fullDocument.";

  private ViewPipeline() {}

  /**
   * Returns true if the view's effective pipeline contains any {@code $match} stage. A {@code
   * $match} before {@code $sample} prevents the fast pseudo-random sampling path and can force a
   * full collection scan.
   */
  public static boolean hasMatchStage(ViewDefinition definition) {
    return Check.isPresent(definition.getEffectivePipeline(), "effectivePipeline").stream()
        .anyMatch(stage -> stage.containsKey(SupportedStage.MATCH.stageName));
  }

  /** Creates aggregation pipeline from a view definition. */
  public static ImmutableList<Bson> forRegularQuery(ViewDefinition definition) {

    checkArg(
        definition.exists(),
        "cannot create aggregation pipeline, view %s does not exist",
        definition.getName());

    var configuredPipeline =
        Check.isPresent(definition.getEffectivePipeline(), "effectivePipeline");
    var queryPipeline = ImmutableList.<Bson>builderWithExpectedSize(configuredPipeline.size());

    for (var stage : configuredPipeline) {
      String stageName = Check.hasSingleElement(stage.keySet(), "stage");
      SupportedStage.byStageName(stageName).orElseThrow(IllegalArgumentException::new);
      // for collection scan query, user-provided stages can be used as is without any changes
      queryPipeline.add(stage);
    }

    return queryPipeline.build();
  }

  /** Creates change stream aggregation pipeline from a view definition. */
  public static ImmutableList<Bson> forChangeStream(ViewDefinition definition, ObjectId indexId) {

    checkArg(
        definition.exists(),
        "cannot create aggregation pipeline, view %s does not exist",
        definition.getName());

    var configuredPipeline =
        Check.isPresent(definition.getEffectivePipeline(), "effectivePipeline");
    var queryPipeline = ImmutableList.<Bson>builderWithExpectedSize(configuredPipeline.size());

    for (var stage : configuredPipeline) {

      String stageName = Check.hasSingleElement(stage.keySet(), "stage");
      SupportedStage type =
          SupportedStage.byStageName(stageName).orElseThrow(IllegalArgumentException::new);

      switch (type) {
        case ADD_FIELDS, SET -> {
          BsonDocument addFieldsStage = stage.get(stageName).asDocument();
          // Single $addFields might contain multiple fields. We keep the same grouping as
          // flattening them affects the behavior
          List<Field<?>> fields =
              addFieldsStage.entrySet().stream()
                  .map(
                      field ->
                          new Field<>(
                              FULL_DOCUMENT_PREFIX + field.getKey(),
                              // To avoid prefixing each user field with fullDocument, we use
                              // pipeline variables to apply expressions in scope of the
                              // fullDocument field without modifying the view-configured
                              // $addFields. In conf call validation, we prohibit users to override
                              // variable CURRENT in their view pipeline to avoid unexpected
                              // results. See docs for more details:
                              // https://www.mongodb.com/docs/manual/reference/aggregation-variables/#mongodb-variable-variable.CURRENT
                              // https://www.mongodb.com/docs/manual/reference/operator/aggregation/let/
                              new BsonDocument(
                                  "$let",
                                  new BsonDocument()
                                      .append(
                                          "vars",
                                          new BsonDocument(
                                              "CURRENT", new BsonString("$fullDocument")))
                                      .append("in", field.getValue()))))
                  .collect(Collectors.toList());

          queryPipeline.add(Aggregates.addFields(fields).toBsonDocument());
        }
        case MATCH -> {
          BsonDocument matchStage = stage.get(stageName).asDocument();

          if (matchStage.isEmpty()) {
            // $match is allowed to be empty, but we don't need to process it
            continue;
          }

          String deletedFlagField = String.format("fullDocument.%s.deleted", indexId);

          // extract the user-provided expression
          BsonDocument expr = Check.isNotNull(matchStage.get("$expr"), "expr").asDocument();

          // wrap the expression with $let, so it's executed in scope of the fullDocument field
          BsonDocument let =
              new BsonDocument(
                  "$let",
                  new BsonDocument()
                      .append("vars", new BsonDocument("CURRENT", new BsonString("$fullDocument")))
                      .append("in", expr));

          // wrap the expression with $cond to avoid un-deleting the document if a previous stage
          // already set the flag to true
          BsonDocument cond =
              new BsonDocument(
                  "$cond",
                  new BsonArray(
                      List.of(
                          // evaluate the deleted flag first, so it's possible to short-circuit and
                          // avoid evaluating the potentially costly user expression
                          new BsonString("$" + deletedFlagField),
                          BsonBoolean.TRUE,
                          // inverse the result of the $match expression to produce the deleted flag
                          new BsonDocument("$not", let))));

          // add deleted flag under metadata namespace
          BsonDocument addFields =
              new BsonDocument("$addFields", new BsonDocument(deletedFlagField, cond));

          queryPipeline.add(addFields);
        }
      }
    }

    return queryPipeline.build();
  }
}
