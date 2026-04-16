package com.xgen.mongot.embedding.mongodb.leasing;

import com.xgen.mongot.embedding.config.MaterializedViewCollectionMetadata;
import com.xgen.mongot.embedding.exceptions.MaterializedViewNonTransientException;
import com.xgen.mongot.embedding.exceptions.MaterializedViewTransientException;
import com.xgen.mongot.index.EncodedUserData;
import com.xgen.mongot.index.IndexGeneration;
import com.xgen.mongot.index.definition.MaterializedViewIndexDefinitionGeneration;
import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.index.version.MaterializedViewGenerationId;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bson.BsonTimestamp;
import org.jetbrains.annotations.TestOnly;

// TODO(CLOUDP-371278): Refactor interface to have stricter type that only allows MatView related
// IndexGeneration and GenerationId
/** Interface for managing leases for the materialized view leader. */
public interface LeaseManager {

  long DEFAULT_INDEX_DEFINITION_VERSION = 0;

  /**
   * Result of polling follower statuses from the lease manager.
   *
   * <p>Contains both the current status of each follower generation and the set of generation IDs
   * that are eligible for leadership acquisition. This includes:
   *
   * <ul>
   *   <li>Leases that have expired (previous leader failed or timed out)
   *   <li>Leases owned by this instance (re-acquiring after restart)
   *   <li>New indexes that don't have a lease yet (lease will be created during acquisition)
   * </ul>
   *
   * @param statuses Map of generation IDs to their current replication status
   * @param acquirableLeases Set of generation IDs eligible for leadership acquisition
   */
  record FollowerPollResult(
      Map<MaterializedViewGenerationId, IndexStatus> statuses,
      Set<MaterializedViewGenerationId> acquirableLeases) {
    /** An empty result with no statuses and no acquirable leases. */
    public static final FollowerPollResult EMPTY =
        new FollowerPollResult(Collections.emptyMap(), Collections.emptySet());
  }

  /**
   * Adds a lease for the given index generation. Used for first-time generator setup, where we
   * create a new lease and flag skipInitialSync isn’t used. The value does not make a difference,
   * but we used false here as it aligns with the behavior (initial sync).
   *
   * @param indexGeneration the index generation to add the lease for.
   */
  @TestOnly
  default void add(IndexGeneration indexGeneration) {
    add(indexGeneration, false);
  }

  /**
   * Adds a lease for the given index generation.
   *
   * @param indexGeneration the index generation to add the lease for.
   * @param skipInitialSync when true, preserve existing commitInfo so the new generator can resume
   *     replication from the last checkpoint; when false, the new version gets initial-sync-style
   *     commitInfo from high watermark to trigger initial sync.
   */
  void add(IndexGeneration indexGeneration, boolean skipInitialSync);

  /**
   * Drops the given index generation from the tracking list in LeaseManager.
   *
   * @param generationId the generation id to remove the lease for
   */
  void drop(MaterializedViewGenerationId generationId);

  /**
   * Deletes the lease entirely in both database and memory, only call this when corresponding
   * materialized view collection is GC'd or user deletes the whole index.
   *
   * @param leaseKey the string lease key for lease to be removed, should be same as collection
   *     name.
   * @return a future that completes when the lease has been dropped
   */
  CompletableFuture<Void> dropLease(String leaseKey);

  /**
   * Returns true if the current mongot is the leader for the given generation id.
   *
   * @param generationId the generation id to check
   * @return true if the caller is the leader
   */
  boolean isLeader(MaterializedViewGenerationId generationId);

  /**
   * Updates the commit info (replication checkpoint state) for the given generation id.
   *
   * @param generationId the generation id to update
   * @param encodedUserData the commit info to update
   */
  void updateCommitInfo(MaterializedViewGenerationId generationId, EncodedUserData encodedUserData)
      throws MaterializedViewTransientException, MaterializedViewNonTransientException;

  /**
   * Returns the commit info (replication checkpoint state) for the given generation id.
   *
   * @param generationId the generation id to get the commit info for
   * @return the commit info
   * @throws IOException if there are any issues talking to the lease store
   */
  EncodedUserData getCommitInfo(MaterializedViewGenerationId generationId) throws IOException;

  /**
   * Updates the status of the materialized view replication.
   *
   * @param generationId the generation id of the index generation
   * @param indexDefinitionVersion the index definition version to update
   * @param indexStatus the status to update to
   */
  void updateReplicationStatus(
      MaterializedViewGenerationId generationId,
      long indexDefinitionVersion,
      IndexStatus indexStatus)
      throws MaterializedViewTransientException, MaterializedViewNonTransientException;

  /**
   * Returns all generation IDs where this instance is the leader.
   *
   * @return a set of generation IDs where this instance is the leader
   */
  Set<MaterializedViewGenerationId> getLeaderGenerationIds();

  /**
   * Returns all generation IDs where this instance is a follower.
   *
   * @return a set of generation IDs where this instance is a follower
   */
  Set<MaterializedViewGenerationId> getFollowerGenerationIds();

  /**
   * Polls the status of all follower materialized views from the database. This method reads the
   * latest status from MongoDB for each follower generation ID managed by this lease manager.
   *
   * <p>For dynamic leader election, this also identifies leases that are eligible for leadership
   * acquisition (expired leases, leases we own, or new indexes without leases).
   *
   * @return a {@link FollowerPollResult} containing the status map and the set of acquirable
   *     leases. Returns {@link FollowerPollResult#EMPTY} if this instance is the leader or if there
   *     are no follower generation IDs.
   */
  FollowerPollResult pollFollowerStatuses();

  /**
   * Returns the oplog position at which the MV first became STEADY. Used by Lucene to determine if
   * it has caught up.
   *
   * @param generationId the generation id
   * @return the steady-as-of position, or empty if MV hasn't reached STEADY yet
   */
  Optional<BsonTimestamp> getSteadyAsOfOplogPosition(MaterializedViewGenerationId generationId);

  /**
   * Returns true if the given index definition version has ever been queryable (reached STEADY at
   * least once), as persisted in the lease. Used to initialize the composite index's queryable
   * ratchet after a restart.
   *
   * @param generationId the generation id
   * @param indexDefinitionVersion the definition version to check
   * @return true if that version has been queryable
   */
  boolean isCurrentVersionQueryable(
      MaterializedViewGenerationId generationId, long indexDefinitionVersion);

  /**
   * Returns the current lease version for the given generation ID. Used as a fencing token for MV
   * writes.
   *
   * @param generationId the generation ID
   * @return the current lease version, or Long.MAX_VALUE for static leaders
   */
  default long getLeaseVersion(MaterializedViewGenerationId generationId) {
    return Long.MAX_VALUE;
  }

  /**
   * Performs a heartbeat for all managed leases. For dynamic leader election, this renews the
   * leases for leaders to maintain leadership. For static leader, this is a no-op since leadership
   * is pre-assigned and constant.
   *
   * <p>This method is called periodically by the MaterializedViewManager.
   */
  default void heartbeat() {
    // Default implementation: no-op for static leader
  }

  /**
   * Attempts to acquire leadership for the given generation ID. This is called by the
   * MaterializedViewManager when it detects that a lease has expired and leadership can be
   * acquired.
   *
   * <p>For dynamic leader election, this attempts to claim the lease using optimistic concurrency
   * control. For static leader, this is a no-op since leadership is pre-assigned.
   *
   * @param generationId the generation ID to acquire leadership for
   * @return true if leadership was successfully acquired, false otherwise
   */
  default boolean tryAcquireLeadership(MaterializedViewGenerationId generationId) {
    // Default implementation: no-op for static leader
    return false;
  }

  /**
   * Releases leadership for the given generation, transitioning it to follower state. Used by
   * MaterializedViewManager to release zombie leases (generator dead but lease still held).
   *
   * @param generationId the generation ID to release leadership for
   */
  default void releaseLeadership(MaterializedViewGenerationId generationId) {}

  /**
   * Initializes an unowned Lease by trying to insert the proposed lease document into the database.
   * This operation should be synchronized across all mongots in the same replicaSet, so only one
   * Mongot wins in insertOne and the winner Lease with MaterializedViewCollectionMetadata should be
   * the same across all mongots.
   *
   * @param proposedMetadata the metadata to insert if it doesn't exist
   * @return the synchronized MaterializedViewCollectionMetadata
   */
  MaterializedViewCollectionMetadata initializeLease(
      MaterializedViewIndexDefinitionGeneration indexDefinitionGeneration,
      MaterializedViewCollectionMetadata proposedMetadata)
      throws Exception;

  /**
   * Returns true if the lease manager is in a lease-acquisition blackout period (e.g. after
   * applying an ops give-up command). During blackout, this instance must not acquire new
   * leadership.
   *
   * @return true if in blackout, false otherwise
   */
  default boolean isInLeaseAcquisitionBlackout() {
    return false;
  }
}
