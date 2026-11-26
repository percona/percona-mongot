package com.xgen.mongot.index;

import com.xgen.mongot.index.query.aggregation.AggregationResult;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.parser.BsonDocumentBuilder;
import com.xgen.mongot.util.bson.parser.BsonDocumentParser;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.mongot.util.bson.parser.DocumentEncodable;
import com.xgen.mongot.util.bson.parser.DocumentParser;
import com.xgen.mongot.util.bson.parser.Field;
import com.xgen.mongot.util.bson.parser.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Contains meta info for the query.
 *
 * @param facet Keyed by facet name.
 * @param metrics Keyed by metric name; each value is a BSON double, or BSON null when no matched
 *     document had a numeric value at the metric's path (mirrors MQL {@code $avg}/{@code $min}/
 *     {@code $max}).
 */
public record MetaResults(
    CountResult count,
    Optional<Map<String, FacetInfo>> facet,
    Optional<AggregationResult> aggregation,
    Optional<Map<String, BsonValue>> metrics)
    implements DocumentEncodable {

  private static class Fields {
    static final Field.Required<CountResult> COUNT_RESULT =
        Field.builder("count").classField(CountResult::fromBson).disallowUnknownFields().required();

    static final Field.Optional<Map<String, FacetInfo>> FACET_RESULTS =
        Field.builder("facet")
            .mapOf(
                Value.builder().classValue(FacetInfo::fromBson).disallowUnknownFields().required())
            .optional()
            .noDefault();

    // Parsed as a whole document (rather than a map of values) because metric values may be BSON
    // null, which the per-value parsers reject.
    static final Field.Optional<BsonDocument> METRIC_RESULTS =
        Field.builder("metrics").documentField().optional().noDefault();
  }

  public static final MetaResults EMPTY = new MetaResults(CountResult.totalCount(0));

  public MetaResults(CountResult count) {
    this(count, Optional.empty(), Optional.empty());
  }

  public MetaResults(
      CountResult count,
      Optional<Map<String, FacetInfo>> facet,
      Optional<AggregationResult> aggregation) {
    this(count, facet, aggregation, Optional.empty());
  }

  /**
   * This method merges the count field only, and intentionally rejects to merge facet and metric
   * fields. This is because string facet merging requires every facet value instead of top facet
   * values from the merge sources, while this class usually prunes to the top N string facet
   * values; metrics are merged from their intermediate components by {@code
   * MetricsMergingBatchProducer}.
   */
  public static MetaResults mergeCountResult(List<MetaResults> metaResultsList) {
    for (var metaResults : metaResultsList) {
      Check.isEmpty(metaResults.facet(), "metaResults.getFacet().");
      Check.isEmpty(metaResults.metrics(), "metaResults.metrics().");
    }
    List<CountResult> countResults =
        metaResultsList.stream().map(MetaResults::count).collect(Collectors.toList());
    return new MetaResults(CountResult.merge(countResults));
  }

  public static MetaResults fromBson(BsonDocument document) throws BsonParseException {
    try (var parser = BsonDocumentParser.fromRoot(document).build()) {
      return fromBson(parser);
    }
  }

  public static MetaResults fromBson(DocumentParser parser) throws BsonParseException {
    // TODO(CLOUDP-413102) provide AggregationResult fromBson
    return new MetaResults(
        parser.getField(Fields.COUNT_RESULT).unwrap(),
        parser.getField(Fields.FACET_RESULTS).unwrap(),
        Optional.empty(),
        parser.getField(Fields.METRIC_RESULTS).unwrap().map(LinkedHashMap::new));
  }

  private static BsonDocument metricsToBson(Map<String, BsonValue> metrics) {
    BsonDocument document = new BsonDocument();
    metrics.forEach(document::append);
    return document;
  }

  /**
   * Return a copy with the aggregation's per-node numBuckets top-N applied. Call this ONLY on a
   * fully-merged, non-sharded / non-partitioned (standalone) result: top-N-by-count is not
   * associative across shard/partition merges, so it must be applied exactly once at the outermost
   * merge point (see {@link AggregationResult#applyLimit}). Intentionally NOT folded into {@link
   * #toBson}, so serializing a per-partition / per-shard (not-yet-fully-merged) {@code MetaResults}
   * can never truncate on partial counts.
   */
  public MetaResults applyAggregationLimit() {
    return this.aggregation()
        .map(
            agg ->
                new MetaResults(
                    this.count, this.facet, Optional.of(agg.applyLimit()), this.metrics))
        .orElse(this);
  }

  @Override
  public BsonDocument toBson() {
    if (this.aggregation().isPresent()) {
      BsonDocument result =
          BsonDocumentBuilder.builder().field(Fields.COUNT_RESULT, this.count).build();

      // TODO(CLOUDP-413102) provide aggregation result toBson
      // Serialize verbatim -- the numBuckets top-N is applied by applyAggregationLimit() at the
      // standalone (non-intermediate) terminal, NOT here, so this never truncates a partial result.
      result.append("facet", this.aggregation().get().toStandaloneBson());
      return result;
    }

    return BsonDocumentBuilder.builder()
        .field(Fields.COUNT_RESULT, this.count)
        .field(Fields.FACET_RESULTS, this.facet)
        .field(Fields.METRIC_RESULTS, this.metrics.map(MetaResults::metricsToBson))
        .build();
  }
}
