package com.xgen.mongot.server.command.management.definition.common;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.NumericFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.StringFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.TypeSetDefinitionBuilder;
import com.xgen.testing.mongot.server.command.management.definition.UserSearchIndexDefinitionBuilder;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  UserSearchIndexDefinitionTest.DeserializationTest.class,
  UserSearchIndexDefinitionTest.SerializationTest.class
})
public class UserSearchIndexDefinitionTest {
  static final String RESOURCES_PATH =
      "src/test/unit/resources/server/command/management/definition/common";

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "user-search-index-deserialization";
    private static final BsonDeserializationTestSuite<UserSearchIndexDefinition> TEST_SUITE =
        fromDocument(RESOURCES_PATH, SUITE_NAME, UserSearchIndexDefinition::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<UserSearchIndexDefinition> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<UserSearchIndexDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<UserSearchIndexDefinition>>
        data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<UserSearchIndexDefinition> simple() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple",
          UserSearchIndexDefinitionBuilder.builder()
              .mappings(DocumentFieldDefinitionBuilder.builder().dynamic(true))
              .typeSets(
                  List.of(
                      TypeSetDefinitionBuilder.builder()
                          .name("foo")
                          .addType(NumericFieldDefinitionBuilder.builder().buildNumberField())
                          .build(),
                      TypeSetDefinitionBuilder.builder()
                          .name("bar")
                          .addType(StringFieldDefinitionBuilder.builder().build())
                          .build()))
              .build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "user-search-index-serialization";
    private static final BsonSerializationTestSuite<UserSearchIndexDefinition> TEST_SUITE =
        fromEncodable(RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<UserSearchIndexDefinition> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<UserSearchIndexDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<UserSearchIndexDefinition>> data() {
      return Arrays.asList(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<UserSearchIndexDefinition> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          UserSearchIndexDefinitionBuilder.builder()
              .typeSets(
                  List.of(
                      TypeSetDefinitionBuilder.builder()
                          .name("foo")
                          .addType(NumericFieldDefinitionBuilder.builder().buildNumberField())
                          .build(),
                      TypeSetDefinitionBuilder.builder()
                          .name("bar")
                          .addType(StringFieldDefinitionBuilder.builder().build())
                          .build()))
              .build());
    }
  }
}
