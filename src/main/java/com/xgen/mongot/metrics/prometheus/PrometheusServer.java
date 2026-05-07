package com.xgen.mongot.metrics.prometheus;

import com.sun.net.httpserver.HttpServer;
import com.xgen.mongot.featureflag.Feature;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.metrics.cache.ScrapeCache;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusNamingConvention;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exports an endpoint that Prometheus can pull metrics from. */
public class PrometheusServer {
  private static final Logger LOG = LoggerFactory.getLogger(PrometheusServer.class);
  protected static final String ENDPOINT_PATH = "/metrics";
  public static final int DEFAULT_PORT = 9946;

  protected static final String SCRAPING_TIMER_NAME = "prometheus-server-scraping-timer";
  protected static final Tags SCRAPING_TIMER_TAGS = Tags.of("timeUnit", "milliseconds");

  // See https://prometheus.io/docs/instrumenting/exposition_formats/#basic-info
  // Matches our version of micrometer:
  // https://github.com/micrometer-metrics/micrometer/blob/1.15.x/implementations/micrometer-registry-prometheus/src/main/java/io/micrometer/prometheusmetrics/PrometheusMeterRegistry.java#L655
  private static final String PROMETHEUS_SCRAPE_CONTENT_TYPE =
      "text/plain; version=0.0.4; charset=utf-8";

  private final HttpServer server;
  private final PrometheusMeterRegistry prometheusMeterRegistry;

  private final Optional<ScrapeCache> scrapeCache;

  public PrometheusServer(
      HttpServer server,
      PrometheusMeterRegistry prometheusMeterRegistry,
      Optional<ScrapeCache> scrapeCache) {
    this.server = server;
    this.prometheusMeterRegistry = prometheusMeterRegistry;
    this.scrapeCache = scrapeCache;
  }

  public PrometheusMeterRegistry getPrometheusMeterRegistry() {
    return this.prometheusMeterRegistry;
  }

  public InetSocketAddress getAddress() {
    return this.server.getAddress();
  }

  /** Starts the Prometheus server. */
  public static PrometheusServer start(
      InetSocketAddress address,
      Iterable<Tag> commonTags,
      List<MeterFilter> filters,
      FeatureFlags featureFlags) {
    var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    PrometheusNamingConvention mongotPrometheusNamingConvention =
        new MongotPrometheusNamingConvention();
    prometheusRegistry.config().namingConvention(mongotPrometheusNamingConvention);
    prometheusRegistry.config().commonTags(commonTags);
    filters.forEach(filter -> prometheusRegistry.config().meterFilter(filter));

    Timer scrapingTimer =
        Timer.builder(SCRAPING_TIMER_NAME)
            .tags(SCRAPING_TIMER_TAGS)
            .publishPercentiles(0.5, 0.75, 0.9, 0.99)
            .register(prometheusRegistry);

    // Configure metrics supplier
    Optional<ScrapeCache> scrapeCache;
    Supplier<String> scrapeSupplier;
    if (featureFlags.isEnabled(Feature.METRICS_CACHE)) {
      ScrapeCache cache = new ScrapeCache(prometheusRegistry::scrape);
      scrapeCache = Optional.of(cache);
      scrapeSupplier = cache::get;
    } else {
      scrapeCache = Optional.empty();
      scrapeSupplier = prometheusRegistry::scrape;
    }

    LOG.atInfo()
        .addKeyValue("address", address)
        .addKeyValue("commonTags", commonTags)
        .addKeyValue("numMeterFilters", filters.size())
        .addKeyValue("cacheEnabled", scrapeCache.isPresent())
        .log("Starting and configuring prometheus");

    try {
      HttpServer server = HttpServer.create(address, 0);

      server.createContext(
          ENDPOINT_PATH,
          httpExchange -> {
            @Nullable String response = scrapingTimer.record(scrapeSupplier);
            byte[] bytes = response != null ? response.getBytes() : "".getBytes();
            httpExchange.getResponseHeaders().add("Content-Type", PROMETHEUS_SCRAPE_CONTENT_TYPE);
            httpExchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = httpExchange.getResponseBody()) {
              os.write(bytes);
            }
          });

      server.setExecutor(null);
      server.start();

      return new PrometheusServer(server, prometheusRegistry, scrapeCache);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void shutdown() {
    LOG.info("Shutting down Prometheus endpoint...");
    this.server.stop(0);
    this.scrapeCache.ifPresent(ScrapeCache::close);
  }

  private static class MongotPrometheusNamingConvention extends PrometheusNamingConvention {
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
      return String.format("%s_%s", "mongot", super.name(name, type, baseUnit));
    }
  }
}
