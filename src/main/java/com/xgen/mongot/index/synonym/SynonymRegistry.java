package com.xgen.mongot.index.synonym;

import com.xgen.mongot.index.definition.SynonymMappingDefinition;
import com.xgen.mongot.index.status.SynonymStatus;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link SynonymRegistry} stores {@link SynonymMapping} entries for an index. Each {@link
 * SynonymMapping} is referenced by name and contains artifacts used to apply synonyms at
 * query-time.
 */
public interface SynonymRegistry {

  /**
   * Get a {@link SynonymMapping.Builder} configured to build {@link SynonymMapping}s for this
   * {@link SynonymMappingDefinition}.
   */
  SynonymMapping.Builder mappingBuilder(SynonymMappingDefinition definition);

  /**
   * Get the {@link SynonymMapping} with name {@code synonymMappingName}.
   *
   * @throws SynonymMappingException if there is no mapping with name {@code synonymMappingName}.
   */
  SynonymMapping get(String synonymMappingName) throws SynonymMappingException;

  /**
   * Insert a {@link SynonymMapping} into this registry, clobbering any existing mapping with that
   * name. Marks the {@link SynonymStatus} for this mapping as {@link SynonymStatus#READY}.
   */
  void update(String name, SynonymMapping synonymMapping);

  /**
   * Marks that a change was observed that is relevant to {@link SynonymMapping}. Does not modify
   * artifacts associated with a {@link SynonymMapping}, only changes the {@link SynonymStatus}.
   * Intended to notify users that this {@link SynonymMapping} has noticed a change, and will soon
   * begin updating.
   *
   * <p>When {@link SynonymStatus#READY}, changes status to {@link SynonymStatus#READY_UPDATING}.
   *
   * <p>When {@link SynonymStatus#INVALID}, changes status to {@link SynonymStatus#SYNC_ENQUEUED}.
   */
  void observeChange(String name);

  /**
   * Marks that work has begun to update a {@link SynonymMapping}. Does not modify artifacts
   * associated with a {@link SynonymMapping}, only changes the {@link SynonymStatus}. Intended to
   * notify users that this {@link SynonymMapping} is being updated, and may soon be replaced with a
   * more recent artifact or invalidated.
   *
   * <p>When {@link SynonymStatus#INVALID} or {@link SynonymStatus#SYNC_ENQUEUED}, changes status to
   * {@link SynonymStatus#INITIAL_SYNC}.
   *
   * <p>When {@link SynonymStatus#READY}, changes status to {@link SynonymStatus#READY_UPDATING}.
   */
  void beginUpdate(String name);

  /**
   * Sets the {@link SynonymMapping} associated with this {@link SynonymMappingDefinition} to an
   * empty {@link SynonymMapping}. Marks the {@link SynonymStatus} for this mapping as {@link
   * SynonymStatus#READY}.
   *
   * <p>Future attempts to get a {@link SynonymMapping} for this {@link SynonymMappingDefinition}
   * should find an associated mapping configured to apply a valid, empty set of synonyms.
   *
   * <p>This may be used, for example, when a synonym source collection backing a {@link
   * SynonymMapping} is dropped.
   *
   * <p>{@code clearMapping} may be called regardless of whether or not this {@link SynonymRegistry}
   * contains an existing, valid {@link SynonymMapping} for this {@link SynonymMappingDefinition}.
   */
  void clear(SynonymMappingDefinition definition);

  /**
   * Removes a {@link SynonymMapping} from this registry, releasing artifacts associated with it.
   * Marks the {@link SynonymStatus} for this mapping as {@link SynonymStatus#INVALID}.
   *
   * <p>Future attempts to get a {@link SynonymMapping} named {@code name} should not find an
   * associated mapping. This may be used, for example, when an invalid document is present in a
   * synonym source collection.
   */
  void invalidate(String name, String message);

  /**
   * Removes a {@link SynonymMapping} from this registry, releasing artifacts associated with it.
   * Marks the {@link SynonymStatus} for this mapping as {@link SynonymStatus#FAILED}.
   *
   * <p>Future attempts to get a {@link SynonymMapping} named {@code name} will not find an
   * associated mapping. This may be used when a mapping is shutting down - and is not expected
   * behavior otherwise.
   */
  void fail(String name, @Nullable String message);

  /**
   * The {@link SynonymStatus}es for each {@link SynonymMapping} that exists in the index. {@link
   * SynonymMapping}s have a {@link SynonymStatus} in this map after they are first created - and
   * will continue to have a status for the lifetime of this {@link SynonymRegistry}.
   */
  Map<String, SynonymStatus> getStatuses();

  Map<String, SynonymDetailedStatus> getDetailedStatuses();

  /** Get the maximum number of documents allowed in a single synonyms collection. */
  Optional<Integer> getMaxDocsPerSynonymCollection();
}
