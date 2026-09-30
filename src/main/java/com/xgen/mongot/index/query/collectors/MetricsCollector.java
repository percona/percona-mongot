package com.xgen.mongot.index.query.collectors;

import com.xgen.mongot.index.query.operators.AllDocumentsOperator;
import com.xgen.mongot.index.query.operators.Operator;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import java.util.Map;
import java.util.Optional;
import org.bson.BsonValue;

/**
 * MetricsCollector computes numeric aggregations ({@link MetricDefinition.Type}) over the documents
 * matched by {@code operator}, using the numeric doc values that {@code number} fields already
 * index. Results are returned in {@link com.xgen.mongot.index.MetaResults#metrics()}.
 *
 * @param metricDefinitions Keyed by metric name.
 */
public record MetricsCollector(Operator operator, Map<String, MetricDefinition> metricDefinitions)
    implements Collector {

  private static class Fields {
    private static final Field.WithDefault<Operator> OPERATOR =
        Field.builder("operator")
            .classField(Operator::parseForMetricsCollector)
            .disallowUnknownFields()
            .optional()
            .withDefault(AllDocumentsOperator.INSTANCE);
    private static final Field.Required<Map<String, MetricDefinition>> METRICS =
        Field.builder("metrics")
            .classField(MetricDefinition::fromBson)
            .disallowUnknownFields()
            .asMap()
            .mustNotBeEmpty()
            .mustNotContainEmptyStringAsKey()
            .validateKeys(MetricsCollector::validateMetricName)
            .required();
  }

  /**
   * Metric names become field names in the pipeline that merges sharded results, where "." and a
   * leading "$" are not allowed.
   */
  private static Optional<String> validateMetricName(String name) {
    if (name.startsWith("$")) {
      return Optional.of("metric name cannot start with \"$\"");
    }
    if (name.contains(".")) {
      return Optional.of("metric name cannot contain \".\"");
    }
    return Optional.empty();
  }

  /** Deserializes collector from a BSON. */
  public static MetricsCollector fromBson(DocumentParser parser) throws BsonParseException {
    return new MetricsCollector(
        parser.getField(Fields.OPERATOR).unwrap(), parser.getField(Fields.METRICS).unwrap());
  }

  @Override
  public BsonValue collectorToBson() {
    return BsonDocumentBuilder.builder()
        .field(Fields.OPERATOR, this.operator)
        .field(Fields.METRICS, this.metricDefinitions)
        .build();
  }

  @Override
  public Type getType() {
    return Type.METRICS;
  }

  @Override
  public Optional<Operator> getOperator() {
    return Optional.of(this.operator);
  }
}
