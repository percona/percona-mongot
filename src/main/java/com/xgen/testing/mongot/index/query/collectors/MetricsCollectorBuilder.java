package com.xgen.testing.mongot.index.query.collectors;

import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.index.query.collectors.MetricsCollector;
import com.xgen.mongot.index.query.operators.AllDocumentsOperator;
import com.xgen.mongot.index.query.operators.Operator;
import com.xgen.mongot.util.Check;
import java.util.Map;
import java.util.Optional;

public class MetricsCollectorBuilder extends CollectorBuilder {

  private Optional<Operator> operator = Optional.empty();
  private Optional<Map<String, MetricDefinition>> metricDefinitions = Optional.empty();

  public MetricsCollectorBuilder operator(Operator operator) {
    this.operator = Optional.of(operator);
    return this;
  }

  public MetricsCollectorBuilder metricDefinitions(
      Map<String, MetricDefinition> metricDefinitions) {
    this.metricDefinitions = Optional.of(metricDefinitions);
    return this;
  }

  @Override
  public MetricsCollector build() {
    Check.isPresent(this.metricDefinitions, "metricDefinitions");
    return new MetricsCollector(
        this.operator.orElse(AllDocumentsOperator.INSTANCE), this.metricDefinitions.get());
  }
}
