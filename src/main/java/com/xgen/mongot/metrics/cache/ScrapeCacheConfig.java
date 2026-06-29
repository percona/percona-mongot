package com.xgen.mongot.metrics.cache;

import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import org.bson.BsonDocument;

/** Tunable thresholds for {@link ScrapeCache}. */
public record ScrapeCacheConfig(
    int scrapeTimeoutSec,
    int slowModeStickyDurationSec,
    int backgroundRefreshIntervalSec,
    int throttleIntervalSec)
    implements DocumentEncodable {

  static class Fields {
    // Maximum time mongot has to compute metrics during an HTTP scrape request before the cache
    // switches to serving cached data.
    public static final Field.WithDefault<Integer> SCRAPE_TIMEOUT_SEC =
        Field.builder("scrapeTimeoutSec")
            .intField()
            .mustBePositive()
            .optional()
            .withDefault(DEFAULT_SCRAPE_TIMEOUT_SEC);

    // How long to keep serving cached metrics after a live scrape times out.
    public static final Field.WithDefault<Integer> SLOW_MODE_STICKY_DURATION_SEC =
        Field.builder("slowModeStickyDurationSec")
            .intField()
            .mustBePositive()
            .optional()
            .withDefault(DEFAULT_SLOW_MODE_STICKY_DURATION_SEC);

    // How often the background task recomputes cached metrics.
    public static final Field.WithDefault<Integer> BACKGROUND_REFRESH_INTERVAL_SEC =
        Field.builder("backgroundRefreshIntervalSec")
            .intField()
            .mustBePositive()
            .optional()
            .withDefault(DEFAULT_BACKGROUND_REFRESH_INTERVAL_SEC);

    // Minimum interval between consecutive cache.get() requests; throttled requests get cached
    // metrics.
    public static final Field.WithDefault<Integer> THROTTLE_INTERVAL_SEC =
        Field.builder("throttleIntervalSec")
            .intField()
            .mustBePositive()
            .optional()
            .withDefault(DEFAULT_THROTTLE_INTERVAL_SEC);
  }

  private static final int DEFAULT_SCRAPE_TIMEOUT_SEC = 3;
  private static final int DEFAULT_SLOW_MODE_STICKY_DURATION_SEC = 1_800;
  private static final int DEFAULT_BACKGROUND_REFRESH_INTERVAL_SEC = 60;
  private static final int DEFAULT_THROTTLE_INTERVAL_SEC = 30;

  public static ScrapeCacheConfig fromBson(DocumentParser p) throws BsonParseException {
    return create(
        p.getField(Fields.SCRAPE_TIMEOUT_SEC).unwrap(),
        p.getField(Fields.SLOW_MODE_STICKY_DURATION_SEC).unwrap(),
        p.getField(Fields.BACKGROUND_REFRESH_INTERVAL_SEC).unwrap(),
        p.getField(Fields.THROTTLE_INTERVAL_SEC).unwrap());
  }

  public static ScrapeCacheConfig create(
      int scrapeTimeoutSec,
      int slowModeStickyDurationSec,
      int backgroundRefreshIntervalSec,
      int throttleIntervalSec) {
    Check.argIsPositive(scrapeTimeoutSec, "scrapeTimeoutSec");
    Check.argIsPositive(slowModeStickyDurationSec, "slowModeStickyDurationSec");
    Check.argIsPositive(backgroundRefreshIntervalSec, "backgroundRefreshIntervalSec");
    Check.argIsPositive(throttleIntervalSec, "throttleIntervalSec");
    return new ScrapeCacheConfig(
        scrapeTimeoutSec,
        slowModeStickyDurationSec,
        backgroundRefreshIntervalSec,
        throttleIntervalSec);
  }

  @Override
  public BsonDocument toBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.SCRAPE_TIMEOUT_SEC, this.scrapeTimeoutSec)
        .field(Fields.SLOW_MODE_STICKY_DURATION_SEC, this.slowModeStickyDurationSec)
        .field(Fields.BACKGROUND_REFRESH_INTERVAL_SEC, this.backgroundRefreshIntervalSec)
        .field(Fields.THROTTLE_INTERVAL_SEC, this.throttleIntervalSec)
        .build();
  }

  public static ScrapeCacheConfig getDefault() {
    return new ScrapeCacheConfig(
        DEFAULT_SCRAPE_TIMEOUT_SEC,
        DEFAULT_SLOW_MODE_STICKY_DURATION_SEC,
        DEFAULT_BACKGROUND_REFRESH_INTERVAL_SEC,
        DEFAULT_THROTTLE_INTERVAL_SEC);
  }
}
