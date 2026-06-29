package com.xgen.mongot.replication.mongodb.common;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonSerializationTestSuite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.UuidRepresentation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      InitialSyncResumeInfoTest.DeserializationTest.class,
      InitialSyncResumeInfoTest.SerializationTest.class,
    })
public class InitialSyncResumeInfoTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "resume-info-deserialization";
    private static final String SUITE_PATH = "src/test/unit/resources/replication/mongodb/common";
    private static final BsonDeserializationTestSuite<Optional<InitialSyncResumeInfo>> TEST_SUITE =
        BsonDeserializationTestSuite.fromDocument(
            SUITE_PATH, SUITE_NAME, InitialSyncResumeInfo::fromBson);

    private final TestSpecWrapper<Optional<InitialSyncResumeInfo>> testSpec;

    public DeserializationTest(TestSpecWrapper<Optional<InitialSyncResumeInfo>> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<Optional<InitialSyncResumeInfo>>> data() {
      return TEST_SUITE.withExamples(
          empty(),
          collectionScan(),
          changeStream(),
          changeStreamWithoutResumeToken(),
          bufferless(),
          bufferlessNaturalOrder());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>> empty() {
      return BsonDeserializationTestSuite.TestSpec.valid("empty", Optional.empty());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>>
        collectionScan() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "collection scan resume info", Optional.empty());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>>
        changeStream() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "change stream application resume info", Optional.empty());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>>
        changeStreamWithoutResumeToken() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "change stream application without batch resume token", Optional.empty());
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>>
        bufferless() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "bufferless",
          Optional.of(
              new BufferlessIdOrderInitialSyncResumeInfo(
                  new BsonTimestamp(1234567890L), new BsonString("a"))));
    }

    private static BsonDeserializationTestSuite.ValidSpec<Optional<InitialSyncResumeInfo>>
        bufferlessNaturalOrder() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "bufferlessNaturalOrder",
          Optional.of(
              new BufferlessNaturalOrderInitialSyncResumeInfo(
                  new BsonTimestamp(1234567890L),
                  new BsonDocument()
                      .append("$recordId", new BsonInt64(1))
                      .append(
                          "$initialSyncId",
                          new BsonBinary(
                              UUID.fromString("390a604a-4979-4d10-9f4f-3d2552224aee"),
                              UuidRepresentation.STANDARD)),
                  Optional.of("testHost"))));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "resume-info-serialization";
    private static final String SUITE_PATH = "src/test/unit/resources/replication/mongodb/common";
    private static final BsonSerializationTestSuite<InitialSyncResumeInfo> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(SUITE_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<InitialSyncResumeInfo> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<InitialSyncResumeInfo> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<InitialSyncResumeInfo>> data() {
      return List.of(bufferless(), bufferlessNaturalOrder());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<InitialSyncResumeInfo> bufferless() {
      return BsonSerializationTestSuite.TestSpec.create(
          "bufferless",
          new BufferlessIdOrderInitialSyncResumeInfo(
              new BsonTimestamp(1234567890L), new BsonString("a")));
    }

    private static BsonSerializationTestSuite.TestSpec<InitialSyncResumeInfo>
        bufferlessNaturalOrder() {
      return BsonSerializationTestSuite.TestSpec.create(
          "bufferlessNaturalOrder",
          new BufferlessNaturalOrderInitialSyncResumeInfo(
              new BsonTimestamp(1234567890L),
              new BsonDocument()
                  .append("$recordId", new BsonInt64(1))
                  .append(
                      "$initialSyncId",
                      new BsonBinary(
                          UUID.fromString("390a604a-4979-4d10-9f4f-3d2552224aee"),
                          UuidRepresentation.STANDARD)),
              Optional.of("testHost")));
    }
  }
}
