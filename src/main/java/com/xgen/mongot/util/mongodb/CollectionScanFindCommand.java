package com.xgen.mongot.util.mongodb;

import com.google.common.annotations.VisibleForTesting;
import com.mongodb.ReadConcern;
import com.xgen.mongot.util.mongodb.serialization.FindCommandProxy;
import java.util.Optional;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.conversions.Bson;

public class CollectionScanFindCommand {

  private final String collectionName;
  private final Optional<Bson> sort;
  private final Optional<Bson> hint;
  private final Optional<Bson> projection;
  private final Optional<BsonBoolean> noCursorTimeout;
  private final Optional<Bson> min;
  private final Optional<Bson> filter;
  private final Optional<BsonDocument> readConcernDocument;

  private CollectionScanFindCommand(
      String collectionName,
      Optional<Bson> sort,
      Optional<Bson> hint,
      Optional<Bson> projection,
      Optional<BsonBoolean> noCursorTimeout,
      Optional<Bson> min,
      Optional<Bson> filter,
      Optional<BsonDocument> readConcernDocument) {

    this.collectionName = collectionName;
    this.sort = sort;
    this.hint = hint;
    this.projection = projection;
    this.noCursorTimeout = noCursorTimeout;
    this.min = min;
    this.filter = filter;
    this.readConcernDocument = readConcernDocument;
  }

  public static class Builder {
    private static final String AFTER_CLUSTER_TIME_FIELD = "afterClusterTime";

    private final String collectionName;
    private Optional<Bson> sort;
    private Optional<Bson> hint;
    private Optional<Bson> projection;
    private Optional<BsonBoolean> noCursorTimeout;
    private Optional<Bson> min;
    private Optional<Bson> filter;
    private Optional<BsonDocument> readConcernDocument;

    /** Constructs a new Builder. */
    public Builder(String collectionName) {
      this.collectionName = collectionName;

      this.sort = Optional.empty();
      this.hint = Optional.empty();
      this.projection = Optional.empty();
      this.noCursorTimeout = Optional.empty();
      this.min = Optional.empty();
      this.filter = Optional.empty();
      this.readConcernDocument = Optional.empty();
    }

    /** Builds a new CollectionScanFindCommand. */
    public CollectionScanFindCommand build() {
      return new CollectionScanFindCommand(
          this.collectionName,
          this.sort,
          this.hint,
          this.projection,
          this.noCursorTimeout,
          this.min,
          this.filter,
          this.readConcernDocument);
    }

    /** Sets the sort. */
    public Builder sort(Bson sort) {
      this.sort = Optional.of(sort);
      return this;
    }

    /** Sets the hint. */
    public Builder hint(Bson hint) {
      this.hint = Optional.of(hint);
      return this;
    }

    /** Sets the projection. */
    public Builder projection(Bson projection) {
      this.projection = Optional.of(projection);
      return this;
    }

    /** Sets the readConcern. */
    public Builder readConcern(ReadConcern readConcern) {
      this.readConcernDocument = Optional.of(readConcern.asDocument());
      return this;
    }

    /** Sets the readConcern and afterClusterTime. */
    public Builder readConcern(ReadConcern readConcern, BsonTimestamp afterClusterTime) {
      this.readConcernDocument = Optional.of(readConcern.asDocument());
      this.readConcernDocument.get().append(AFTER_CLUSTER_TIME_FIELD, afterClusterTime);
      return this;
    }

    /** Sets the noCursorTimeout. */
    public Builder noCursorTimeout(BsonBoolean noCursorTimeout) {
      this.noCursorTimeout = Optional.of(noCursorTimeout);
      return this;
    }

    /** Sets the inclusive index lower bound. */
    public Builder min(Bson min) {
      this.min = Optional.of(min);
      return this;
    }

    /** Applies a filter. */
    public Builder filter(Bson filter) {
      this.filter = Optional.of(filter);
      return this;
    }
  }

  /** Constructs the proper proxy for the CollectionScanFindCommand. */
  public FindCommandProxy toProxy() {
    return new FindCommandProxy(
        new BsonString(this.collectionName),
        this.sort,
        this.hint,
        this.projection,
        this.noCursorTimeout,
        this.min,
        this.filter,
        this.readConcernDocument);
  }

  @VisibleForTesting
  public Optional<Bson> getMin() {
    return this.min;
  }

  @VisibleForTesting
  public Optional<BsonDocument> getReadConcernDocument() {
    return this.readConcernDocument;
  }
}
