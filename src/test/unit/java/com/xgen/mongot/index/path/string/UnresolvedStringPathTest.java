package com.xgen.mongot.index.path.string;

import static com.xgen.testing.BsonDeserializationTestSuite.fromValue;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.path.string.UnresolvedStringPathBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      UnresolvedStringPathTest.DeserializationTest.class,
      UnresolvedStringPathTest.SerializationTest.class,
      UnresolvedStringPathTest.ClassTest.class,
    })
public class UnresolvedStringPathTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "unresolved-string-path-deserialization";
    private static final BsonDeserializationTestSuite<UnresolvedStringPath> TEST_SUITE =
        fromValue(
            "src/test/unit/resources/index/path/string/",
            SUITE_NAME,
            UnresolvedStringPath::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<UnresolvedStringPath> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<UnresolvedStringPath> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<UnresolvedStringPath>>
        data() {
      return TEST_SUITE.withExamples(
          stringValue(),
          emptyStringValue(),
          documentValueNoMulti(),
          documentEmptyValueNoMulti(),
          documentEmptyValueWithMulti(),
          documentWildcard());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath> stringValue() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "string value", UnresolvedStringPathBuilder.fieldPath("my-path"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath> emptyStringValue() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "empty string value", UnresolvedStringPathBuilder.fieldPath(""));
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath>
        documentValueNoMulti() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "document value no multi", UnresolvedStringPathBuilder.fieldPath("my-path"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath>
        documentEmptyValueNoMulti() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "document empty value no multi", UnresolvedStringPathBuilder.fieldPath(""));
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath>
        documentEmptyValueWithMulti() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "document value with multi",
          UnresolvedStringPathBuilder.withMulti("my-path", "my-multi"));
    }

    private static BsonDeserializationTestSuite.ValidSpec<UnresolvedStringPath> documentWildcard() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "document wildcard", UnresolvedStringPathBuilder.wildcardPath("some-path-*"));
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {

    private static final String SUITE_NAME = "unresolved-string-path-serialization";
    private static final BsonSerializationTestSuite<UnresolvedStringPath> TEST_SUITE =
        fromEncodable("src/test/unit/resources/index/path/string/", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<UnresolvedStringPath> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<UnresolvedStringPath> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<UnresolvedStringPath>> data() {
      return Arrays.asList(field(), multi(), wildcard());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<UnresolvedStringPath> field() {
      return BsonSerializationTestSuite.TestSpec.create(
          "field", UnresolvedStringPathBuilder.fieldPath("my-path"));
    }

    private static BsonSerializationTestSuite.TestSpec<UnresolvedStringPath> multi() {
      return BsonSerializationTestSuite.TestSpec.create(
          "multi", UnresolvedStringPathBuilder.withMulti("my-path", "my-multi"));
    }

    private static BsonSerializationTestSuite.TestSpec<UnresolvedStringPath> wildcard() {
      return BsonSerializationTestSuite.TestSpec.create(
          "wildcard", UnresolvedStringPathBuilder.wildcardPath("some-path-*"));
    }
  }

  public static class ClassTest {
    @Test
    public void testEquality() {
      TestUtils.assertEqualityGroups(
          () -> UnresolvedStringPathBuilder.fieldPath("foo"),
          () -> UnresolvedStringPathBuilder.withMulti("foo", "bar"),
          () -> UnresolvedStringPathBuilder.wildcardPath("foo*"));
    }
  }
}
