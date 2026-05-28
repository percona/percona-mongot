package com.xgen.mongot.index.autoembedding;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.embedding.mongodb.MaterializedViewCollectionResolver;
import com.xgen.mongot.index.definition.FieldDefinition;
import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.definition.VectorAutoEmbedFieldDefinition;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import com.xgen.mongot.index.definition.VectorIndexDefinition;
import com.xgen.mongot.index.definition.VectorIndexFieldDefinition;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for {@link MaterializedViewIndexGeneration}, including detection of when two index
 * definitions differ only by Lucene-only parameters (e.g. hnswOptions, analyzers). When Lucene-only
 * params change, replaceGenerator updates the lease in place (persistLeaseForGeneration) and
 * preserves the resume token (doInitialSync=false). Other changes trigger full replace with
 * doInitialSync=true.
 */
public final class MaterializedViewIndexGenerationUtil {

  private static final Logger LOG =
      LoggerFactory.getLogger(MaterializedViewIndexGenerationUtil.class);

  /**
   * Wire keys dropped from {@link VectorAutoEmbedFieldDefinition#toBson()} before comparison. New
   * Lucene-only keys should be added here; any other new keys in {@code toBson()} participate
   * automatically.
   */
  private static final Set<String> AUTO_EMBED_BSON_KEYS_IGNORED_FOR_NON_LUCENE_COMPARE =
      Set.of(
          VectorFieldSpecification.Fields.SIMILARITY.getName(),
          VectorFieldSpecification.Fields.HNSW_OPTIONS.getName(),
          VectorFieldSpecification.Fields.INDEXING_ALGORITHM.getName());

  /**
   * True if the new definition could skip resync, e.g. differ only by Lucene params (hnswOptions,
   * numPartitions, indexFeatureVersion, analyzers). When false, resync is needed.
   */
  public static boolean skipInitialSync(IndexDefinition oldDef, IndexDefinition newDef) {
    return switch (oldDef) {
      case VectorIndexDefinition oldVector -> {
        if (!(newDef instanceof VectorIndexDefinition newVector)) {
          throw new IllegalArgumentException(
              "Cannot compare definitions of different types: "
                  + oldDef.getClass().getSimpleName()
                  + " vs "
                  + newDef.getClass().getSimpleName());
        }
        yield skipInitialSyncVector(oldVector, newVector);
      }
      case SearchIndexDefinition oldSearch -> {
        if (!(newDef instanceof SearchIndexDefinition newSearch)) {
          throw new IllegalArgumentException(
              "Cannot compare definitions of different types: "
                  + oldDef.getClass().getSimpleName()
                  + " vs "
                  + newDef.getClass().getSimpleName());
        }
        yield skipInitialSyncSearch(oldSearch, newSearch);
      }
    };
  }

  // ---- Vector index ----

  private static boolean skipInitialSyncVector(
      VectorIndexDefinition oldDef, VectorIndexDefinition newDef) {
    // Conditions that REQUIRE resync (return false):

    // 1. AUTO_EMBED fields changed (path, model, modality, dimensions). This should never happen
    // because such changes will create a new MV and not enter the replaceGenerator path where
    // method skipInitialSync is called. Returning false as a defensive move.
    if (autoEmbedFieldChanged(oldDef, newDef)) {
      return false;
    }

    // 2. Filter fields changed
    if (filterFieldsChanged(oldDef, newDef)) {
      return false;
    }

    // 3. Lucene-only params changed (indexingMethod, hnswOptions, similarity, etc.) → reuse
    if (vectorLuceneParamsChanged(oldDef, newDef)) {
      return true;
    }

    // 4. Same definition version (e.g. Lucene rebuild, fell off oplog). No MV schema change,
    // so we preserve the existing commit and resume steady-state replication.
    if (oldDef.getDefinitionVersion().equals(newDef.getDefinitionVersion())) {
      LOG.atInfo().log("Definition version did not change");
      return true;
    }

    // 5. Only definitionVersion differs → resync
    return false;
  }

  /**
   * Checks if only KNOWN Lucene parameters changed.
   *
   * <p>Per AUTO_EMBED field: {@link VectorAutoEmbedFieldDefinition#toBson()} with Lucene keys
   * removed (see {@link #AUTO_EMBED_BSON_KEYS_IGNORED_FOR_NON_LUCENE_COMPARE}), then {@link
   * BsonDocument#equals(Object)} on key-sorted copies. Top-level: numPartitions,
   * indexFeatureVersion.
   */
  private static boolean vectorLuceneParamsChanged(
      VectorIndexDefinition oldDef, VectorIndexDefinition newDef) {
    var oldAutoEmbedFields =
        oldDef.getFields().stream()
            .filter(f -> f.getType() == VectorIndexFieldDefinition.Type.AUTO_EMBED)
            .sorted(Comparator.comparing(f -> f.getPath().toString()))
            .toList();
    var newAutoEmbedFields =
        newDef.getFields().stream()
            .filter(f -> f.getType() == VectorIndexFieldDefinition.Type.AUTO_EMBED)
            .sorted(Comparator.comparing(f -> f.getPath().toString()))
            .toList();

    // Fields must have same count
    if (oldAutoEmbedFields.size() != newAutoEmbedFields.size()) {
      return false;
    }

    // Compare each pair with Lucene fields (similarity, indexingAlgorithm) ignored: if the
    // remainder differs, not a pure Lucene bump. If remainders match but full fields differ, only
    // Lucene params changed for that pair.
    @Var boolean autoEmbedLuceneParamChange = false;
    for (int i = 0; i < oldAutoEmbedFields.size(); i++) {
      var oldAutoEmbedField = oldAutoEmbedFields.get(i);
      var newAutoEmbedField = newAutoEmbedFields.get(i);
      if (!autoEmbedFieldsEqualIgnoringLuceneParams(oldAutoEmbedField, newAutoEmbedField)) {
        LOG.atInfo()
            .addKeyValue("oldAutoEmbedField", oldAutoEmbedField)
            .addKeyValue("newAutoEmbedField", newAutoEmbedField)
            .log("AUTO_EMBED field differs outside Lucene params");
        return false;
      }
      if (!oldAutoEmbedField.equals(newAutoEmbedField)) {
        autoEmbedLuceneParamChange = true;
      }
    }

    if (autoEmbedLuceneParamChange) {
      LOG.atInfo().log("At least one AUTO_EMBED field differs by Lucene params");
      return true;
    }

    // Top-level Lucene params
    // TODO(CLOUDP-391893): assess other top-level VectorIndexDefinition field changes
    if (oldDef.getNumPartitions() != newDef.getNumPartitions()) {
      LOG.atInfo()
          .addKeyValue("oldNumPartitions", oldDef.getNumPartitions())
          .addKeyValue("newNumPartitions", newDef.getNumPartitions())
          .log("numPartitions changed");
      return true;
    }
    if (oldDef.getParsedIndexFeatureVersion() != newDef.getParsedIndexFeatureVersion()) {
      LOG.atInfo()
          .addKeyValue("oldIndexFeatureVersion", oldDef.getParsedIndexFeatureVersion())
          .addKeyValue("newIndexFeatureVersion", newDef.getParsedIndexFeatureVersion())
          .log("indexFeatureVersion changed");
      return true;
    }

    return false;
  }

  /**
   * Path match and same auto-embed field shape in BSON after removing Lucene-only keys ({@link
   * BsonDocument#equals(Object)} on key-sorted documents so order does not matter).
   */
  private static boolean autoEmbedFieldsEqualIgnoringLuceneParams(
      VectorIndexFieldDefinition oldField, VectorIndexFieldDefinition newField) {
    var oldAutoEmbed = (VectorAutoEmbedFieldDefinition) oldField;
    var newAutoEmbed = (VectorAutoEmbedFieldDefinition) newField;
    if (!oldAutoEmbed.getPath().equals(newAutoEmbed.getPath())) {
      return false;
    }
    return nonLuceneAutoEmbedFieldBsonNormalized(oldAutoEmbed)
        .equals(nonLuceneAutoEmbedFieldBsonNormalized(newAutoEmbed));
  }

  private static BsonDocument nonLuceneAutoEmbedFieldBsonNormalized(
      VectorAutoEmbedFieldDefinition f) {
    BsonDocument raw = f.toBson();
    BsonDocument sansLucene = new BsonDocument();
    for (Map.Entry<String, BsonValue> e : raw.entrySet()) {
      if (!AUTO_EMBED_BSON_KEYS_IGNORED_FOR_NON_LUCENE_COMPARE.contains(e.getKey())) {
        sansLucene.append(e.getKey(), e.getValue());
      }
    }
    TreeMap<String, BsonValue> sorted = new TreeMap<>();
    for (String k : sansLucene.keySet()) {
      sorted.put(k, sansLucene.get(k));
    }
    BsonDocument ordered = new BsonDocument();
    sorted.forEach(ordered::append);
    return ordered;
  }

  /** Filter fields have changed (order-independent comparison). */
  private static boolean filterFieldsChanged(
      VectorIndexDefinition oldDef, VectorIndexDefinition newDef) {
    var oldFilterFields =
        oldDef.getFields().stream()
            .filter(f -> f.getType() == VectorIndexFieldDefinition.Type.FILTER)
            .sorted(Comparator.comparing(f -> f.getPath().toString()))
            .toList();
    var newFilterFields =
        newDef.getFields().stream()
            .filter(f -> f.getType() == VectorIndexFieldDefinition.Type.FILTER)
            .sorted(Comparator.comparing(f -> f.getPath().toString()))
            .toList();
    if (!oldFilterFields.equals(newFilterFields)) {
      LOG.atInfo().log("Filter fields changed");
      return true;
    }
    return false;
  }

  /** AUTO_EMBED field hash changed (path, model, numDimensions, quantization). */
  private static boolean autoEmbedFieldChanged(
      VectorIndexDefinition oldDef, VectorIndexDefinition newDef) {
    String oldHash = MaterializedViewCollectionResolver.computeHash(oldDef);
    String newHash = MaterializedViewCollectionResolver.computeHash(newDef);
    if (!oldHash.equals(newHash)) {
      LOG.atError()
          .log("AUTO_EMBED fields changed, which should NOT happen in replaceGenerator path");
      return true;
    }
    return false;
  }

  // ---- Search index ----

  private static boolean skipInitialSyncSearch(
      SearchIndexDefinition oldDef, SearchIndexDefinition newDef) {
    // 1. Auto-embed fields changed -> resync
    String oldHash = MaterializedViewCollectionResolver.computeHash(oldDef);
    String newHash = MaterializedViewCollectionResolver.computeHash(newDef);
    if (!oldHash.equals(newHash)) {
      LOG.atError()
          .log(
              "Search AUTO_EMBED fields changed, "
                  + "which should NOT happen in replaceGenerator path");
      return false;
    }

    // 2. Non-auto-embed fields changed -> resync
    if (searchNonAutoEmbedFieldsChanged(oldDef, newDef)) {
      return false;
    }

    // 3. Lucene-only params changed -> reuse
    if (searchLuceneParamsChanged(oldDef, newDef)) {
      return true;
    }

    // 4. Same definition version (e.g. Lucene rebuild, fell off oplog). Step 4 trusts that
    // steps 1-3 cover every schema-affecting field; new mapping properties must be added to
    // step 2 (`searchNonAutoEmbedFieldsChanged`) before they can land in the def or the
    // schema-change path will silently short-circuit here.
    if (oldDef.getDefinitionVersion().equals(newDef.getDefinitionVersion())) {
      LOG.atInfo().log("Definition version did not change");
      return true;
    }

    // 5. Only definitionVersion differs -> resync
    return false;
  }

  private static boolean searchNonAutoEmbedFieldsChanged(
      SearchIndexDefinition oldDef, SearchIndexDefinition newDef) {
    // TODO(CLOUDP-353553): allowlist-of-checks below misses per-field Lucene-only tweaks
    // (analyzer/tokenization on non-auto-embed fields, hnswOptions/similarity on auto-embed
    // fields) — forces a full re-embed when only Lucene params changed. Vector strips these
    // per-field; search should mirror that. Also: every new top-level mapping property
    // (typeSets, synonyms, storedSource, sort, …) has to be added below explicitly today.
    if (!oldDef.getMappings().dynamic().equals(newDef.getMappings().dynamic())) {
      LOG.atInfo().log("Mappings dynamic flag changed");
      return true;
    }

    // Compare non-auto-embed fields (order-independent via sorted map)
    var oldNonAutoEmbed = new TreeMap<String, FieldDefinition>();
    var newNonAutoEmbed = new TreeMap<String, FieldDefinition>();
    for (var entry : oldDef.getMappings().fields().entrySet()) {
      if (entry.getValue().searchAutoEmbedFieldDefinition().isEmpty()) {
        oldNonAutoEmbed.put(entry.getKey(), entry.getValue());
      }
    }
    for (var entry : newDef.getMappings().fields().entrySet()) {
      if (entry.getValue().searchAutoEmbedFieldDefinition().isEmpty()) {
        newNonAutoEmbed.put(entry.getKey(), entry.getValue());
      }
    }
    if (!oldNonAutoEmbed.equals(newNonAutoEmbed)) {
      LOG.atInfo().log("Search non-auto-embed fields changed");
      return true;
    }
    return false;
  }

  private static boolean searchLuceneParamsChanged(
      SearchIndexDefinition oldDef, SearchIndexDefinition newDef) {
    @Var boolean changed = false;
    if (oldDef.getNumPartitions() != newDef.getNumPartitions()) {
      LOG.atInfo()
          .addKeyValue("oldNumPartitions", oldDef.getNumPartitions())
          .addKeyValue("newNumPartitions", newDef.getNumPartitions())
          .log("numPartitions changed");
      changed = true;
    }
    if (oldDef.getParsedIndexFeatureVersion() != newDef.getParsedIndexFeatureVersion()) {
      LOG.atInfo()
          .addKeyValue("oldIndexFeatureVersion", oldDef.getParsedIndexFeatureVersion())
          .addKeyValue("newIndexFeatureVersion", newDef.getParsedIndexFeatureVersion())
          .log("indexFeatureVersion changed");
      changed = true;
    }
    if (!oldDef.getAnalyzerName().equals(newDef.getAnalyzerName())) {
      LOG.atInfo().log("analyzerName changed");
      changed = true;
    }
    if (!oldDef.getSearchAnalyzerName().equals(newDef.getSearchAnalyzerName())) {
      LOG.atInfo().log("searchAnalyzerName changed");
      changed = true;
    }
    if (!oldDef.getAnalyzers().equals(newDef.getAnalyzers())) {
      LOG.atInfo().log("custom analyzers changed");
      changed = true;
    }
    return changed;
  }
}
