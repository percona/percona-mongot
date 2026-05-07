package com.xgen.mongot.metrics.cache;

import java.io.Closeable;
import java.util.function.Supplier;

/**
 * Caches and throttles requests to the metrics scrape supplier, decoupling metrics calculation from
 * the metrics reading path.
 */
public class ScrapeCache implements Closeable {
  private final Supplier<String> metricsSupplier;

  public ScrapeCache(Supplier<String> metricsSupplier) {
    this.metricsSupplier = metricsSupplier;
  }

  public String get() {
    // TODO(CLOUDP-402354): throttling and caching will be implemented in a follow-up PR.
    return this.metricsSupplier.get();
  }

  @Override
  public void close() {}
}
