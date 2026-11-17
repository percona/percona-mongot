package com.xgen.mongot.index.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.index.definition.NumericFieldOptions;
import com.xgen.mongot.index.definition.SearchIndexCapabilities;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.ReturnScope;
import com.xgen.mongot.index.query.collectors.MetricDefinition;
import com.xgen.mongot.index.query.collectors.MetricsCollector;
import com.xgen.mongot.index.version.IndexFormatVersion;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.EmbeddedDocumentsFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.NumericFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.query.collectors.MetricsCollectorBuilder;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

public class LuceneFacetContextTest {

  private static final Optional<ReturnScope> ITEMS_SCOPE =
      Optional.of(new ReturnScope(FieldPath.parse("items")));

  /** "items" is embeddedDocuments with dynamic mappings, so any path resolves to a number. */
  private static LuceneFacetContext dynamicItemsContext() {
    SearchIndexDefinition indexDefinition =
        SearchIndexDefinitionBuilder.builder()
            .defaultMetadata()
            .indexFeatureVersion(SearchIndexCapabilities.CURRENT_FEATURE_VERSION)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .dynamic(true)
                    .field(
                        "items",
                        FieldDefinitionBuilder.builder()
                            .embeddedDocuments(
                                EmbeddedDocumentsFieldDefinitionBuilder.builder()
                                    .dynamic(true)
                                    .build())
                            .build())
                    .build())
            .build();
    return new LuceneFacetContext(
        indexDefinition.createFieldDefinitionResolver(IndexFormatVersion.CURRENT),
        indexDefinition.getIndexCapabilities(IndexFormatVersion.CURRENT));
  }

  private static MetricsCollector avgOf(String path) {
    return new MetricsCollectorBuilder()
        .metricDefinitions(Map.of("avg", new MetricDefinition(MetricDefinition.Type.AVG, path)))
        .build();
  }

  @Test
  public void testMetricPathUnderReturnScopeResolves() throws Exception {
    assertEquals(
        Map.of(
            "avg",
            FieldName.TypeField.NUMBER_DOUBLE_V2.getLuceneFieldName(
                FieldPath.parse("items.price"), ITEMS_SCOPE.map(ReturnScope::path))),
        dynamicItemsContext().getMetricLuceneFields(avgOf("items.price"), ITEMS_SCOPE));
  }

  @Test
  public void testMetricPathOutsideReturnScopeIsRejected() {
    // "price" resolves through the dynamic embedded mapping, but no child document has it.
    InvalidQueryException e =
        assertThrows(
            InvalidQueryException.class,
            () -> dynamicItemsContext().getMetricLuceneFields(avgOf("price"), ITEMS_SCOPE));
    assertEquals(
        "metric path 'price' must be a descendant of returnScope.path 'items'", e.getMessage());
  }

  @Test
  public void testMetricPathEqualToReturnScopeIsRejected() {
    assertThrows(
        InvalidQueryException.class,
        () -> dynamicItemsContext().getMetricLuceneFields(avgOf("items"), ITEMS_SCOPE));
  }

  @Test
  public void testInt64RepresentationIsRejectedWithPrecisionExplanation() {
    SearchIndexDefinition indexDefinition =
        SearchIndexDefinitionBuilder.builder()
            .defaultMetadata()
            .indexFeatureVersion(SearchIndexCapabilities.CURRENT_FEATURE_VERSION)
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .field(
                        "count",
                        FieldDefinitionBuilder.builder()
                            .number(
                                NumericFieldDefinitionBuilder.builder()
                                    .representation(NumericFieldOptions.Representation.INT64)
                                    .buildNumberField())
                            .build())
                    .build())
            .build();
    LuceneFacetContext context =
        new LuceneFacetContext(
            indexDefinition.createFieldDefinitionResolver(IndexFormatVersion.CURRENT),
            indexDefinition.getIndexCapabilities(IndexFormatVersion.CURRENT));

    InvalidQueryException e =
        assertThrows(
            InvalidQueryException.class,
            () -> context.getMetricLuceneFields(avgOf("count"), Optional.empty()));
    assertTrue(e.getMessage(), e.getMessage().contains("representation \"int64\""));
    assertTrue(e.getMessage(), e.getMessage().contains("integers beyond 2^53 are rounded"));
  }
}
