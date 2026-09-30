package com.xgen.testing.mongot.index.query.collectors;

import com.xgen.mongot.index.query.collectors.Collector;

public abstract class CollectorBuilder {

  public static FacetCollectorBuilder facet() {
    return new FacetCollectorBuilder();
  }

  public static MetricsCollectorBuilder metrics() {
    return new MetricsCollectorBuilder();
  }

  public abstract Collector build();
}
