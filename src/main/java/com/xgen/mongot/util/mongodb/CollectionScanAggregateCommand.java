package com.xgen.mongot.util.mongodb;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.ReadConcern;
import com.mongodb.client.model.Aggregates;
import com.xgen.mongot.util.mongodb.serialization.AggregateCommandProxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.TestOnly;

/**
 * See mongodb aggregation command docs for details:
 * https://docs.mongodb.com/manual/reference/command/aggregate
 */
public class CollectionScanAggregateCommand {

  private final String collectionName;
  private final Optional<Bson> sort;
  private final Optional<Bson> hint;
  private final Optional<BsonValue> lastScannedId;
  private final Optional<BsonDocument> readConcernDocument;

  /** $project that filters our fields that are not used for indexing. */
  private final Optional<Bson> indexedFieldsProjectionStage;

  /**
   * $addFields that adds metadata fields like _id and deleted flag under a separate namespace under
   * indexId field. Required to avoid collision with user-defined fields.
   */
  private final Optional<Bson> metadataAddFieldsStage;

  /**
   * Aggregation stages defined in the view. Empty if the index is created on a collection or the
   * view has an empty pipeline.
   */
  private final Optional<List<Bson>> viewDefinedStages;

  private final Optional<BsonBoolean> requestResumeToken;
  private final Optional<BsonDocument> startAt;

  /**
   * The batch size for the cursor. If empty, the <a
   * href="https://www.mongodb.com/docs/manual/reference/method/cursor.batchSize/">default</a> batch
   * size will be used (101 documents for the initial batch and 16MiB for subsequent getMore
   * batches).
   */
  private final OptionalLong batchSize;

  private CollectionScanAggregateCommand(
      String collectionName,
      Optional<Bson> sort,
      Optional<Bson> hint,
      Optional<Bson> indexedFieldsProjectionStage,
      Optional<Bson> metadataAddFieldsStage,
      Optional<BsonValue> lastScannedId,
      Optional<BsonDocument> readConcernDocument,
      Optional<List<Bson>> viewDefinedStages,
      Optional<BsonBoolean> requestResumeToken,
      Optional<BsonDocument> startAt,
      OptionalLong batchSize) {

    this.collectionName = collectionName;
    this.metadataAddFieldsStage = metadataAddFieldsStage;
    this.sort = sort;
    this.hint = hint;
    this.indexedFieldsProjectionStage = indexedFieldsProjectionStage;
    this.lastScannedId = lastScannedId;
    this.readConcernDocument = readConcernDocument;
    this.viewDefinedStages = viewDefinedStages;
    this.requestResumeToken = requestResumeToken;
    this.startAt = startAt;
    this.batchSize = batchSize;
  }

  public static class Builder {
    private static final String AFTER_CLUSTER_TIME_FIELD = "afterClusterTime";

    private final String collectionName;
    private Optional<Bson> sort;
    private Optional<Bson> hint;
    private Optional<Bson> indexedFieldsProjectionStage;
    private Optional<Bson> metadataAddFieldsStage;
    private Optional<List<Bson>> viewDefinedStages;
    private Optional<BsonValue> lastScannedId;
    private Optional<BsonDocument> readConcernDocument;

    private Optional<BsonBoolean> requestResumeToken;
    private Optional<BsonDocument> startAt;
    private OptionalLong batchSize;

    public Builder(String collectionName) {
      this.collectionName = collectionName;
      this.metadataAddFieldsStage = Optional.empty();
      this.sort = Optional.empty();
      this.hint = Optional.empty();
      this.indexedFieldsProjectionStage = Optional.empty();
      this.viewDefinedStages = Optional.empty();
      this.lastScannedId = Optional.empty();
      this.readConcernDocument = Optional.empty();
      this.requestResumeToken = Optional.empty();
      this.startAt = Optional.empty();
      this.batchSize = OptionalLong.empty();
    }

    public CollectionScanAggregateCommand build() {
      return new CollectionScanAggregateCommand(
          this.collectionName,
          this.sort,
          this.hint,
          this.indexedFieldsProjectionStage,
          this.metadataAddFieldsStage,
          this.lastScannedId,
          this.readConcernDocument,
          this.viewDefinedStages,
          this.requestResumeToken,
          this.startAt,
          this.batchSize);
    }

    public Builder metadataAddFieldsStage(Bson metadataAddFieldsStage) {
      this.metadataAddFieldsStage = Optional.of(metadataAddFieldsStage);
      return this;
    }

    public Builder sort(Bson sort) {
      this.sort = Optional.of(Aggregates.sort(sort));
      return this;
    }

    public Builder hint(Bson hint) {
      this.hint = Optional.of(hint);
      return this;
    }

    public Builder indexedFieldsProjectionStage(Bson indexedFieldsProjectionStage) {
      this.indexedFieldsProjectionStage =
          Optional.of(Aggregates.project(indexedFieldsProjectionStage));
      return this;
    }

    public Builder viewDefinedStages(List<Bson> viewDefinedStages) {
      this.viewDefinedStages = Optional.of(viewDefinedStages);
      return this;
    }

    /** Sets the inclusive index lower bound. */
    public Builder lastScannedId(BsonValue lastScannedId) {
      this.lastScannedId = Optional.of(lastScannedId);
      return this;
    }

    public Builder readConcern(ReadConcern readConcern) {
      this.readConcernDocument = Optional.of(readConcern.asDocument());
      return this;
    }

    public Builder readConcern(ReadConcern readConcern, BsonTimestamp afterClusterTime) {
      this.readConcernDocument = Optional.of(readConcern.asDocument());
      this.readConcernDocument.get().append(AFTER_CLUSTER_TIME_FIELD, afterClusterTime);
      return this;
    }

    public Builder requestResumeToken(BsonBoolean value) {
      this.requestResumeToken = Optional.of(value);
      return this;
    }

    public Builder startAt(BsonDocument postBatchResumeToken) {
      this.startAt = Optional.of(postBatchResumeToken);
      return this;
    }

    public Builder batchSize(long batchSize) {
      this.batchSize = OptionalLong.of(batchSize);
      return this;
    }
  }

  public Optional<BsonValue> getLastScannedId() {
    return this.lastScannedId;
  }

  @TestOnly
  @VisibleForTesting
  public Optional<BsonDocument> getStartAt() {
    return this.startAt;
  }

  public Optional<BsonBoolean> getRequestResumeToken() {
    return this.requestResumeToken;
  }

  public Optional<BsonDocument> getReadConcernDocument() {
    return this.readConcernDocument;
  }

  /** Creates the aggregate command */
  public AggregateCommandProxy toProxy() {
    BsonValue aggregate = new BsonString(this.collectionName);

    /*
     * Unlike {@link CollectionScanFindCommand}, the aggregation query does not have the "min"
     * option. Instead, we use $gte on the _id field, wrapped with $expr in order to perform query
     * without type bracketing. See docs for details -
     * https://www.mongodb.com/docs/manual/reference/method/db.collection.find/#std-label-type-bracketing
     * https://www.mongodb.com/docs/manual/reference/operator/query/expr/
     */
    Optional<Bson> match =
        this.lastScannedId.map(
            id ->
                Aggregates.match(
                    new BsonDocument(
                        "$expr",
                        new BsonDocument(
                            "$gte", new BsonArray(List.of(new BsonString("$_id"), id))))));

    List<Bson> pipeline =
        new AggregationPipelineBuilder()
            .addStage(this.sort)
            .addStage(match)
            .addStage(this.metadataAddFieldsStage)
            .addMultipleStages(this.viewDefinedStages)
            // projection of the fields used in the index appears as the last stage as view-defined
            // stages might need access to the full document
            .addStage(this.indexedFieldsProjectionStage)
            .build();

    return new AggregateCommandProxy(
        aggregate,
        pipeline,
        new AggregateCommandProxy.CursorProxy(this.batchSize),
        this.hint,
        this.readConcernDocument,
        this.requestResumeToken,
        this.startAt,
        Optional.empty());
  }
}
