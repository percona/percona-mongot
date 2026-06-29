package com.xgen.mongot.index.synonym;

import static com.google.common.truth.Truth.assertThat;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.util.bson.parser.BsonParseContext;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.synonym.SynonymDocumentBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      SynonymDocumentTest.DeserializationTest.class,
      SynonymDocumentTest.SerializationTest.class,
      SynonymDocumentTest.IdStringFromBsonValueTest.class
    })
public class SynonymDocumentTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "synonym-document-deserialization";

    private static final BsonDeserializationTestSuite<SynonymDocument> TEST_SUITE =
        BsonDeserializationTestSuite.fromValue(
            "src/test/unit/resources/index/synonym/",
            SUITE_NAME,
            SynonymDocumentTest.DeserializationTest::fromBsonExceptionUnwrapped);

    private final BsonDeserializationTestSuite.TestSpecWrapper<SynonymDocument> testSpec;

    /**
     * Make a wrapper for deserialization to
     *
     * <p>1) Enter through the "front door, {@link SynonymDocument#fromBson(BsonDocument)} - _not_
     * {@code SynonymDocument#fromBson(DocumentParser)}. Method taking DocumentParser argument may
     * not be configured to allow unknown fields on the parser, which doesn't let us test that
     * SynonymDocument is configured that way.
     *
     * <p>2) {@link SynonymDocument} parsing wraps {@link BsonParseException}s in {@link
     * SynonymMappingException}s, and {@link BsonDeserializationTestSuite} expects to handle
     * BsonParseExceptions. Unwrap them here to present exceptions as expected by the test suite.
     */
    private static SynonymDocument fromBsonExceptionUnwrapped(
        BsonParseContext context, BsonValue document) throws BsonParseException {
      if (document.getBsonType() != BsonType.DOCUMENT) {
        Assert.fail("did not deserialize to document");
      }
      try {
        return SynonymDocument.fromTestBson(document.asDocument());
      } catch (SynonymMappingException e) {
        if (e.getCause() instanceof BsonParseException) {
          throw (BsonParseException) e.getCause();
        }

        throw new AssertionError("cause should always be BsonParseException", e);
      }
    }

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<SynonymDocument> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<SynonymDocument>> data() {
      return TEST_SUITE.withExamples(
          equivalent(),
          explicit(),
          equivalentUnvalidatedInput(),
          explicitAllowsOtherFields(),
          equivalentNumericDocId(),
          equivalentStringDocId(),
          equivalentObjectIdDocId(),
          equivalentUuidDocId());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument> equivalent() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent", SynonymDocumentBuilder.equivalent(List.of("car", "truck")));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument> explicit() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit", SynonymDocumentBuilder.explicit(List.of("vehicle"), List.of("car", "truck")));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument>
        equivalentUnvalidatedInput() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent has unvalidated input field",
          SynonymDocumentBuilder.equivalent(List.of("car", "truck")));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument>
        explicitAllowsOtherFields() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "explicit allows other fields",
          SynonymDocumentBuilder.explicit(List.of("vehicle", "jalopy"), List.of("car", "truck")));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument>
        equivalentNumericDocId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent with numeric docId",
          SynonymDocumentBuilder.equivalent(List.of("car", "truck"), new BsonInt32(12345)));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument> equivalentStringDocId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent with string docId",
          SynonymDocumentBuilder.equivalent(List.of("car", "truck"), new BsonString("12345")));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument>
        equivalentObjectIdDocId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent with objectId docId",
          SynonymDocumentBuilder.equivalent(
              List.of("car", "truck"), new BsonObjectId(new ObjectId("507f191e810c19729de860ea"))));
    }

    private static BsonDeserializationTestSuite.ValidSpec<SynonymDocument> equivalentUuidDocId() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "equivalent with uuid docId",
          SynonymDocumentBuilder.equivalent(
              List.of("car", "truck"),
              new BsonBinary(UUID.fromString("eb6c40ca-f25e-47e8-b48c-02a05b64a5aa"))));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "synonym-document-serialization";
    private static final BsonSerializationTestSuite<SynonymDocument> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/synonym/", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<SynonymDocument> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<SynonymDocument> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<SynonymDocument>> data() {
      return List.of(equivalent(), explicit());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<SynonymDocument> equivalent() {
      return BsonSerializationTestSuite.TestSpec.create(
          "equivalent", SynonymDocumentBuilder.equivalent(List.of("car", "truck")));
    }

    private static BsonSerializationTestSuite.TestSpec<SynonymDocument> explicit() {
      return BsonSerializationTestSuite.TestSpec.create(
          "explicit",
          SynonymDocumentBuilder.explicit(
              List.of("vehicle", "transportation"), List.of("car", "truck")));
    }
  }

  /** Tests for {@link SynonymDocument#idStringFromBsonValue(Optional)}. */
  public static class IdStringFromBsonValueTest {

    @Test
    public void idStringFromBsonValue_withStandardUuid_returnsUuidString() {
      UUID uuid = UUID.fromString("eb6c40ca-f25e-47e8-b48c-02a05b64a5aa");
      BsonBinary bsonUuid = new BsonBinary(uuid);

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(bsonUuid));

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(uuid.toString());
    }

    @Test
    public void idStringFromBsonValue_withLegacyUuid_returnsEmpty() {
      byte[] uuidBytes =
          new byte[] {
            (byte) 0xeb, (byte) 0x6c, (byte) 0x40, (byte) 0xca,
            (byte) 0xf2, (byte) 0x5e, (byte) 0x47, (byte) 0xe8,
            (byte) 0xb4, (byte) 0x8c, (byte) 0x02, (byte) 0xa0,
            (byte) 0x5b, (byte) 0x64, (byte) 0xa5, (byte) 0xaa
          };
      BsonBinary legacyUuid = new BsonBinary(BsonBinarySubType.UUID_LEGACY, uuidBytes);

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(legacyUuid));

      assertThat(result).isEmpty();
    }

    @Test
    public void idStringFromBsonValue_withGenericBinary_returnsEmpty() {
      byte[] data = new byte[] {0x01, 0x02, 0x03, 0x04};
      BsonBinary genericBinary = new BsonBinary(BsonBinarySubType.BINARY, data);

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(genericBinary));

      assertThat(result).isEmpty();
    }

    @Test
    public void idStringFromBsonValue_withEmptyOptional_returnsEmpty() {
      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.empty());

      assertThat(result).isEmpty();
    }

    @Test
    public void idStringFromBsonValue_withObjectId_returnsObjectIdString() {
      ObjectId objectId = new ObjectId("507f191e810c19729de860ea");
      BsonObjectId bsonObjectId = new BsonObjectId(objectId);

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(bsonObjectId));

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(objectId.toString());
    }

    @Test
    public void idStringFromBsonValue_withString_returnsStringValue() {
      BsonString bsonString = new BsonString("test-id-123");

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(bsonString));

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo("test-id-123");
    }

    @Test
    public void idStringFromBsonValue_withInt32_returnsStringRepresentation() {
      BsonInt32 bsonInt = new BsonInt32(12345);

      Optional<String> result = SynonymDocument.idStringFromBsonValue(Optional.of(bsonInt));

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo("12345");
    }
  }
}
