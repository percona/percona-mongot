package com.xgen.mongot.util.mongodb.serialization;

import com.xgen.mongot.util.Check;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

/**
 * AggregateCommandProxy is a proxy for an <em>aggregate</em> command.
 *
 * <p>See https://docs.mongodb.com/manual/reference/command/aggregate/#dbcmd.aggregate
 */
public class AggregateCommandProxy implements Bson {

  private static final String AGGREGATE_FIELD = "aggregate";
  private static final String PIPELINE_FIELD = "pipeline";
  private static final String CURSOR_FIELD = "cursor";
  private static final String HINT_FIELD = "hint";
  private static final String READ_CONCERN_FIELD = "readConcern";
  private static final String REQUEST_RESUME_TOKEN = "$_requestResumeToken";
  private static final String START_AT = "$_startAt";
  private static final String MAX_TIME_MS_FIELD = "maxTimeMS";

  private final BsonValue aggregate;
  private final List<Bson> pipeline;
  private final CursorProxy cursor;
  private final Optional<Bson> hint;
  private final Optional<BsonDocument> readConcern;

  private final Optional<BsonBoolean> requestResumeToken;
  private final Optional<BsonDocument> startAt;
  private final Optional<Long> maxTimeMs;

  public AggregateCommandProxy(
      BsonValue aggregate,
      List<Bson> pipeline,
      CursorProxy cursor,
      Optional<Bson> hint,
      Optional<BsonDocument> readConcern,
      Optional<BsonBoolean> requestResumeToken,
      Optional<BsonDocument> startAt,
      Optional<Long> maxTimeMs) {
    Check.argNotNull(aggregate, "aggregate");
    Check.argNotNull(pipeline, "pipeline");
    Check.argNotNull(cursor, "cursor");

    this.aggregate = aggregate;
    this.pipeline = pipeline;
    this.cursor = cursor;
    this.hint = hint;
    this.readConcern = readConcern;
    this.requestResumeToken = requestResumeToken;
    this.startAt = startAt;
    this.maxTimeMs = maxTimeMs;
  }

  public static class CursorProxy implements Bson {

    private static final String BATCH_SIZE_FIELD = "batchSize";

    private final OptionalLong batchSize;

    public CursorProxy(OptionalLong batchSize) {
      this.batchSize = batchSize;
    }

    @Override
    public <T> BsonDocument toBsonDocument(Class<T> documentClass, CodecRegistry codecRegistry) {
      Check.argNotNull(documentClass, "documentClass");
      Check.argNotNull(codecRegistry, "codecRegistry");

      BsonDocument doc = new BsonDocument();
      this.batchSize.ifPresent(batchSize -> doc.put(BATCH_SIZE_FIELD, new BsonDouble(batchSize)));

      return doc;
    }
  }

  @Override
  public <T> BsonDocument toBsonDocument(Class<T> documentClass, CodecRegistry codecRegistry) {
    Check.argNotNull(documentClass, "documentClass");
    Check.argNotNull(codecRegistry, "codecRegistry");

    BsonDocument doc =
        new BsonDocument()
            .append(AGGREGATE_FIELD, this.aggregate)
            .append(CURSOR_FIELD, this.cursor.toBsonDocument(documentClass, codecRegistry));

    BsonArray pipeline = new BsonArray();
    this.pipeline.forEach(bson -> pipeline.add(bson.toBsonDocument(documentClass, codecRegistry)));
    doc.append(PIPELINE_FIELD, pipeline);

    this.hint.ifPresent(
        bson -> doc.append(HINT_FIELD, bson.toBsonDocument(documentClass, codecRegistry)));

    this.readConcern.ifPresent(
        bson -> doc.append(READ_CONCERN_FIELD, bson.toBsonDocument(documentClass, codecRegistry)));

    this.requestResumeToken.ifPresent(bson -> doc.append(REQUEST_RESUME_TOKEN, bson));

    this.startAt.ifPresent(bson -> doc.append(START_AT, bson));

    this.maxTimeMs.ifPresent(ms -> doc.append(MAX_TIME_MS_FIELD, new BsonInt64(ms)));

    return doc;
  }
}
