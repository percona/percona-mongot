package com.xgen.testing.mongot.index.query.collectors;

import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.util.Check;
import java.util.Optional;

public class MetricDefinitionBuilder {

  private Optional<MetricDefinition.Type> type = Optional.empty();
  private Optional<String> path = Optional.empty();

  public static MetricDefinitionBuilder builder() {
    return new MetricDefinitionBuilder();
  }

  public MetricDefinitionBuilder type(MetricDefinition.Type type) {
    this.type = Optional.of(type);
    return this;
  }

  public MetricDefinitionBuilder path(String path) {
    this.path = Optional.of(path);
    return this;
  }

  public MetricDefinition build() {
    Check.isPresent(this.type, "type");
    Check.isPresent(this.path, "path");
    return new MetricDefinition(this.type.get(), this.path.get());
  }
}
