package com.xgen.mongot.replication.mongodb.common;

import static com.xgen.mongot.replication.mongodb.common.ChangeStreamModeSelector.ChangeStreamMode;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.MongoNamespace;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.util.mongodb.ChangeStreamAggregateCommand;
import java.util.List;
import org.bson.BsonTimestamp;
import org.bson.types.ObjectId;

public class ChangeStreamAggregateCommandFactory {

  private final IndexDefinition indexDefinition;
  private final MongoNamespace namespace;
  private final List<String> excludedFields;
  private final boolean matchCollectionUuidForUpdateLookup;
  private final boolean splitLargeChangeStreamEvents;

  public ChangeStreamAggregateCommandFactory(
      IndexDefinition indexDefinition,
      MongoNamespace namespace,
      List<String> excludedFields,
      boolean matchCollectionUuidForUpdateLookup,
      boolean splitLargeChangeStreamEvents) {
    this.indexDefinition = indexDefinition;
    this.namespace = namespace;
    this.excludedFields = excludedFields;
    this.matchCollectionUuidForUpdateLookup = matchCollectionUuidForUpdateLookup;
    this.splitLargeChangeStreamEvents = splitLargeChangeStreamEvents;
  }

  public ChangeStreamAggregateCommandFactory(
      IndexDefinition indexDefinition, MongoNamespace namespace) {
    this(indexDefinition, namespace, List.of(), false, false);
  }

  /**
   * Note that we request that a cursor returned from the aggregate command have a batch size of 0.
   *
   * <p>If you don't specify a batch size of 0, the mongod will scan the oplog until either it finds
   * a matching event, or reaches the end of the oplog. This can be problematic if you are resuming
   * a change stream at a point far back in the oplog, with many entries not pertaining to this
   * collection between the resume point and either the next entry pertaining to this collection or
   * the end of the oplog, as it would block for an unpredictably long time.
   *
   * <p>Additionally, using maxTimeMS does not limit the amount of time spent on this initial scan,
   * but instead makes the cursor fail if it exceeds the allotted time (see
   * https://jira.mongodb.org/browse/SERVER-44110).
   *
   * <p>So to make the behavior predictable, we run the initial aggregation with a batch size of 0
   * to quickly establish the cursors, then call getMores on the cursor, which are subject to a
   * maxTimeMS.
   */
  @VisibleForTesting
  public ChangeStreamAggregateCommand.Builder builder(ObjectId indexId, ChangeStreamMode mode) {

    ChangeStreamAggregateCommand.Builder builder =
        new ChangeStreamAggregateCommand.Builder()
            .collection(this.namespace.getCollectionName())
            .batchSize(0)
            .fullDocument(ChangeStreamAggregateCommand.FullDocument.UPDATE_LOOKUP)
            .metadataAddFieldsStage(MetadataNamespace.forChangeStream(indexId))
            .showMigrationEvents(true)
            .matchCollectionUuidForUpdateLookup(this.matchCollectionUuidForUpdateLookup)
            .splitLargeEvent(this.splitLargeChangeStreamEvents);

    if (mode == ChangeStreamMode.INDEXED_FIELDS) {
      Projection.forChangeStream(this.indexDefinition)
          .ifPresent(
              projectStage ->
                  builder.indexedFieldsProjectionStage(
                      Aggregates.project(projectStage).toBsonDocument()));
    }

    this.indexDefinition
        .getView()
        .ifPresent(
            view ->
                builder.viewDefinedStages(
                    ViewPipeline.forChangeStream(view, this.indexDefinition.getIndexId())));

    if (!this.excludedFields.isEmpty()) {
      builder.changeStreamMetadataExclusionProjectionStage(
          Aggregates.project(
                  Projections.fields(
                      this.excludedFields.stream().map(Projections::exclude).toList()))
              .toBsonDocument());
    }

    return builder;
  }

  public ChangeStreamAggregateCommand fromResumeInfo(
      ChangeStreamResumeInfo resumeInfo, ObjectId indexId, ChangeStreamMode mode) {
    return builder(indexId, mode).startAfter(resumeInfo.getResumeToken()).build();
  }

  public ChangeStreamAggregateCommand fromOperationTime(
      BsonTimestamp operationTime, ObjectId indexId, ChangeStreamMode mode) {
    return builder(indexId, mode).startAtOperationTime(operationTime).build();
  }

  public ChangeStreamAggregateCommand unpinned(ObjectId indexId, ChangeStreamMode mode) {
    return builder(indexId, mode).build();
  }
}
