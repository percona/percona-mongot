package com.xgen.mongot.embedding.mongodb.leasing;

import static org.junit.Assert.assertEquals;

import com.xgen.mongot.index.status.IndexStatus;
import com.xgen.mongot.index.status.IndexStatus.StatusCode;
import org.junit.Test;

public class StatusResolutionUtilsTest {

  @Test
  public void testGetEffectiveMaterializedViewStatus() {
    // Same version pass-through
    assertEffectiveStatus(StatusCode.STEADY, true, StatusCode.STEADY, true, StatusCode.STEADY);
    assertEffectiveStatus(
        StatusCode.INITIAL_SYNC, false, StatusCode.INITIAL_SYNC, false, StatusCode.INITIAL_SYNC);
    // Same version, INITIAL_SYNC but previously queryable (e.g. fell off the oplog) ->
    // RECOVERING_TRANSIENT
    assertEffectiveStatus(
        StatusCode.INITIAL_SYNC,
        true,
        StatusCode.INITIAL_SYNC,
        true,
        StatusCode.RECOVERING_TRANSIENT);

    // V1 terminal states always take precedence (regardless of V2)
    assertEffectiveStatus(StatusCode.FAILED, false, StatusCode.STEADY, true, StatusCode.FAILED);
    assertEffectiveStatus(
        StatusCode.FAILED, false, StatusCode.INITIAL_SYNC, false, StatusCode.FAILED);
    assertEffectiveStatus(StatusCode.STALE, true, StatusCode.STEADY, true, StatusCode.STALE);
    assertEffectiveStatus(StatusCode.STALE, true, StatusCode.INITIAL_SYNC, false, StatusCode.STALE);
    assertEffectiveStatus(
        StatusCode.DOES_NOT_EXIST, false, StatusCode.STEADY, true, StatusCode.DOES_NOT_EXIST);
    assertEffectiveStatus(
        StatusCode.DOES_NOT_EXIST,
        false,
        StatusCode.INITIAL_SYNC,
        false,
        StatusCode.DOES_NOT_EXIST);

    // V2 is STEADY -> switch to V2 (latest is ready)
    assertEffectiveStatus(
        StatusCode.INITIAL_SYNC, false, StatusCode.STEADY, true, StatusCode.STEADY);
    assertEffectiveStatus(
        StatusCode.NOT_STARTED, false, StatusCode.STEADY, true, StatusCode.STEADY);
    assertEffectiveStatus(StatusCode.UNKNOWN, false, StatusCode.STEADY, true, StatusCode.STEADY);
    assertEffectiveStatus(
        StatusCode.RECOVERING_TRANSIENT, true, StatusCode.STEADY, true, StatusCode.STEADY);

    // V2 is INITIAL_SYNC -> use V1 (old version serves while new one builds)
    assertEffectiveStatus(
        StatusCode.STEADY, true, StatusCode.INITIAL_SYNC, false, StatusCode.STEADY);

    // V2 hasn't started building (UNKNOWN, NOT_STARTED) -> RECOVERING_TRANSIENT
    assertEffectiveStatus(
        StatusCode.STEADY, true, StatusCode.UNKNOWN, false, StatusCode.RECOVERING_TRANSIENT);
    assertEffectiveStatus(
        StatusCode.STEADY, true, StatusCode.NOT_STARTED, false, StatusCode.RECOVERING_TRANSIENT);
    assertEffectiveStatus(
        StatusCode.INITIAL_SYNC, false, StatusCode.UNKNOWN, false, StatusCode.RECOVERING_TRANSIENT);

    // V2 has progressed past building -> use V2's status
    assertEffectiveStatus(StatusCode.STEADY, true, StatusCode.FAILED, false, StatusCode.FAILED);
    assertEffectiveStatus(StatusCode.STEADY, true, StatusCode.STALE, true, StatusCode.STALE);
    assertEffectiveStatus(
        StatusCode.STEADY,
        true,
        StatusCode.RECOVERING_TRANSIENT,
        true,
        StatusCode.RECOVERING_TRANSIENT);
    assertEffectiveStatus(
        StatusCode.INITIAL_SYNC, false, StatusCode.FAILED, false, StatusCode.FAILED);
  }

  private void assertEffectiveStatus(
      StatusCode v1Status,
      boolean v1Queryable,
      StatusCode v2Status,
      boolean v2Queryable,
      StatusCode expected) {
    Lease.IndexDefinitionVersionStatus v1 =
        new Lease.IndexDefinitionVersionStatus(v1Queryable, v1Status);
    Lease.IndexDefinitionVersionStatus v2 =
        new Lease.IndexDefinitionVersionStatus(v2Queryable, v2Status);

    IndexStatus result = StatusResolutionUtils.getEffectiveMaterializedViewStatus(v1, v2);

    assertEquals(
        String.format(
            "V1=%s(queryable=%s), V2=%s(queryable=%s) should yield %s",
            v1Status, v1Queryable, v2Status, v2Queryable, expected),
        expected,
        result.getStatusCode());
  }
}
