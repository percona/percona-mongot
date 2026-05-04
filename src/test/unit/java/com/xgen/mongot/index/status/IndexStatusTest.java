package com.xgen.mongot.index.status;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import java.util.Arrays;
import org.bson.BsonTimestamp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {IndexStatusTest.DeserializationTest.class, IndexStatusTest.SerializationTest.class})
public class IndexStatusTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "index-status-deserialization";
    private static final BsonDeserializationTestSuite<IndexStatus> TEST_SUITE =
        fromDocument("src/test/unit/resources/index/status", SUITE_NAME, IndexStatus::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<IndexStatus> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<IndexStatus> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<IndexStatus>> data() {
      return TEST_SUITE.withExamples(
          simple(),
          initialSyncWithMessage(),
          stale(),
          recoveringNonTransient(),
          recoveringTransient(),
          failed());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple", new IndexStatus(IndexStatus.StatusCode.INITIAL_SYNC));
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> initialSyncWithMessage() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "initial sync with message", IndexStatus.initialSync("foo-bar"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> stale() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "stale index", IndexStatus.stale("foo-bar", new BsonTimestamp(1000)));
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> recoveringNonTransient() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "recovering non transient", IndexStatus.recoveringNonTransient(new BsonTimestamp(1000)));
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> recoveringTransient() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "recovering transient", IndexStatus.recoveringTransient("recovering"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<IndexStatus> failed() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "failed",
          IndexStatus.failed("recovering", IndexStatus.Reason.INITIAL_SYNC_REPLICATION_FAILED));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "index-status-serialization";
    private static final BsonSerializationTestSuite<IndexStatus> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/status", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<IndexStatus> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<IndexStatus> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<IndexStatus>> data() {
      return Arrays.asList(
          simple(),
          initialSyncWithMessage(),
          stale(),
          recoveringNonTransient(),
          recoveringTransient(),
          failed());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple", new IndexStatus(IndexStatus.StatusCode.INITIAL_SYNC));
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> initialSyncWithMessage() {
      return BsonSerializationTestSuite.TestSpec.create(
          "initial sync with message", IndexStatus.initialSync("foo-bar"));
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> stale() {
      return BsonSerializationTestSuite.TestSpec.create(
          "stale index", IndexStatus.stale("foo-bar", new BsonTimestamp(1000)));
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> recoveringNonTransient() {
      return BsonSerializationTestSuite.TestSpec.create(
          "recovering non transient", IndexStatus.recoveringNonTransient(new BsonTimestamp(1000)));
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> recoveringTransient() {
      return BsonSerializationTestSuite.TestSpec.create(
          "recovering transient", IndexStatus.recoveringTransient("recovering"));
    }

    private static BsonSerializationTestSuite.TestSpec<IndexStatus> failed() {
      return BsonSerializationTestSuite.TestSpec.create(
          "failed",
          IndexStatus.failed("recovering", IndexStatus.Reason.INITIAL_SYNC_REPLICATION_FAILED));
    }
  }
}
