package com.xgen.mongot.index.autoembedding;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.embedding.mongodb.MaterializedViewCollectionResolver;
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
 * Utilities for {@link MaterializedViewIndexGeneration}, including detection of when two vector
 * index definitions differ only by Lucene-only parameters (e.g. indexingMethod, hnswOptions,
 * similarity, etc...). When Lucene params change, replaceGenerator updates the lease in place and
 * preserves the resume token (skipInitialSync=true). Other changes trigger full replace with
 * skipInitialSync=false.
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
   * numPartitions, indexFeatureVersion). When false, resync is needed.
   */
  public static boolean skipInitialSync(
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
    if (luceneParamsChanged(oldDef, newDef)) {
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
  private static boolean luceneParamsChanged(
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
}
