package com.xgen.mongot.embedding.mongodb.leasing;

import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.index.status.IndexStatus.StatusCode;

/**
 * Utility class containing methods for resolving the effective status of an auto-embedding related
 * entity based on the status of its constituent parts.
 */
public class StatusResolutionUtils {

  /**
   * Return effective status of the MV when there are multiple versions as indexes are being updated
   */
  public static IndexStatus getEffectiveMaterializedViewStatus(
      Lease.IndexDefinitionVersionStatus requestedIndexDefinitionVersionStatus,
      Lease.IndexDefinitionVersionStatus latestIndexDefinitionVersionStatus) {
    StatusCode requestedStatus = requestedIndexDefinitionVersionStatus.indexStatusCode();
    StatusCode latestStatus = latestIndexDefinitionVersionStatus.indexStatusCode();

    // Same Version: Pass through
    if (requestedIndexDefinitionVersionStatus.equals(latestIndexDefinitionVersionStatus)) {
      // If the index was previously queryable but is now rebuilding (e.g. fell off the oplog),
      // report RECOVERING_TRANSIENT (which gets reported externally as STALE) rather than
      // INITIAL_SYNC (BUILDING) so Atlas reflects "queryable but potentially out of date" while the
      // resync is in progress.
      if (requestedIndexDefinitionVersionStatus.isQueryable()
          && requestedStatus == StatusCode.INITIAL_SYNC) {
        return IndexStatus.recoveringTransient("Recovering via resync");
      }
      return new IndexStatus(requestedStatus);
    }

    // Terminal/DNE states in requested always take precedence
    if (requestedStatus == StatusCode.FAILED
        || requestedStatus == StatusCode.STALE
        || requestedStatus == StatusCode.DOES_NOT_EXIST) {
      return new IndexStatus(requestedStatus);
    }

    // Latest hasn't started building yet - indicate transition with RECOVERING_TRANSIENT
    if (latestStatus == StatusCode.UNKNOWN || latestStatus == StatusCode.NOT_STARTED) {
      return IndexStatus.recoveringTransient("New version pending build");
    }

    // Latest is actively building - use requested (old version serving)
    if (latestStatus == StatusCode.INITIAL_SYNC) {
      return new IndexStatus(requestedStatus);
    }

    // Latest has progressed past building - use its status
    return new IndexStatus(latestStatus);
  }
}
