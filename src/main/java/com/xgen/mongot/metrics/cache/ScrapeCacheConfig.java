package com.xgen.mongot.metrics.cache;

/** Tunable thresholds for {@link ScrapeCache}. */
public record ScrapeCacheConfig(
    long scrapeTimeoutSec, long cachedModeStickyDurationSec, long backgroundRefreshIntervalSec) {

  /** Production-tuned defaults. */
  public static final ScrapeCacheConfig DEFAULT =
      new ScrapeCacheConfig(
          /* scrapeTimeoutSec= */ 3,
          /* cachedModeStickyDurationSec= */ 1_800, // 30m
          /* backgroundRefreshIntervalSec= */ 60);
}
