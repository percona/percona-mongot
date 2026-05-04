package com.xgen.mongot.index.path.string;

import static com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.path.string.StringPathBuilder;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      StringPathTest.DeserializationTest.class,
      StringPathTest.SerializationTest.class,
      StringPathTest.ClassTest.class,
    })
public class StringPathTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "string-path-deserialization";
    private static final BsonDeserializationTestSuite<StringPath> TEST_SUITE =
        fromValue("src/test/unit/resources/index/path/string/", SUITE_NAME, StringPath::fromBson);

    private final TestSpecWrapper<StringPath> testSpec;

    public DeserializationTest(TestSpecWrapper<StringPath> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<StringPath>> data() {
      return TEST_SUITE.withExamples(
          stringValue(),
          emptyStringValue(),
          documentValueNoMulti(),
          documentEmptyValueNoMulti(),
          documentEmptyValueWithMulti());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<StringPath> stringValue() {
      return TestSpec.valid("string value", StringPathBuilder.fieldPath("my-path"));
    }

    private static ValidSpec<StringPath> emptyStringValue() {
      return TestSpec.valid("empty string value", StringPathBuilder.fieldPath(""));
    }

    private static ValidSpec<StringPath> documentValueNoMulti() {
      return TestSpec.valid("document value no multi", StringPathBuilder.fieldPath("my-path"));
    }

    private static ValidSpec<StringPath> documentEmptyValueNoMulti() {
      return TestSpec.valid("document empty value no multi", StringPathBuilder.fieldPath(""));
    }

    private static ValidSpec<StringPath> documentEmptyValueWithMulti() {
      return TestSpec.valid(
          "document value with multi", StringPathBuilder.withMulti("my-path", "my-multi"));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "string-path-serialization";
    private static final BsonSerializationTestSuite<StringPath> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/path/string/", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<StringPath> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<StringPath> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<StringPath>> data() {
      return Arrays.asList(field(), multi());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<StringPath> field() {
      return BsonSerializationTestSuite.TestSpec.create(
          "field", StringPathBuilder.fieldPath("my-path"));
    }

    private static BsonSerializationTestSuite.TestSpec<StringPath> multi() {
      return BsonSerializationTestSuite.TestSpec.create(
          "multi", StringPathBuilder.withMulti("my-path", "my-multi"));
    }
  }

  public static class ClassTest {

    @Test
    public void testField() {
      StringPath definition = StringPathBuilder.fieldPath("foo");
      Assert.assertEquals(StringPath.Type.FIELD, definition.getType());

      Assert.assertTrue(definition.isField());
      Assert.assertFalse(definition.isMultiField());

      // Should not throw.
      definition.asField();

      // Should throw.
      Assert.assertThrows(RuntimeException.class, definition::asMultiField);
    }

    @Test
    public void testMultiField() {
      StringPath definition = StringPathBuilder.withMulti("foo", "bar");
      Assert.assertEquals(StringPath.Type.MULTI_FIELD, definition.getType());

      Assert.assertTrue(definition.isMultiField());
      Assert.assertFalse(definition.isField());

      // Should not throw.
      definition.asMultiField();

      // Should throw.
      Assert.assertThrows(RuntimeException.class, definition::asField);
    }

    @Test
    public void testEquality() {
      TestUtils.assertEqualityGroups(
          () -> StringPathBuilder.fieldPath("foo"),
          () -> StringPathBuilder.withMulti("foo", "bar"));
    }
  }
}
