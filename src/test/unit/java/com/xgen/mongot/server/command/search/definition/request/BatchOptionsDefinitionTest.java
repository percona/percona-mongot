package com.xgen.mongot.server.command.search.definition.request;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.server.command.search.definition.request.BatchOptionsDefinitionBuilder;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      BatchOptionsDefinitionTest.DeserializationTest.class,
      BatchOptionsDefinitionTest.SerializationTest.class,
    })
public class BatchOptionsDefinitionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "batch-cursor-options-deserialization";
    private static final BsonDeserializationTestSuite<BatchOptionsDefinition> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/server/command/search/definition/request",
            SUITE_NAME,
            BatchOptionsDefinition::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<BatchOptionsDefinition> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<BatchOptionsDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<BsonDeserializationTestSuite.TestSpecWrapper<BatchOptionsDefinition>>
        data() {
      return TEST_SUITE.withExamples(
          simpleDocsRequested(), simpleBatchSize(), missingAllParameters());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<BatchOptionsDefinition>
        simpleDocsRequested() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simpleDocsRequested", BatchOptionsDefinitionBuilder.builder().docsRequested(25).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<BatchOptionsDefinition>
        simpleBatchSize() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simpleBatchSize", BatchOptionsDefinitionBuilder.builder().batchSize(25).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<BatchOptionsDefinition>
        missingAllParameters() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-all-parameters", BatchOptionsDefinitionBuilder.builder().build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "batch-cursor-options-serialization";
    private static final BsonSerializationTestSuite<BatchOptionsDefinition> TEST_SUITE =
        fromEncodable(
            "src/test/unit/resources/server/command/search/definition/request", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<BatchOptionsDefinition> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<BatchOptionsDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<BatchOptionsDefinition>> data() {
      return Arrays.asList(simpleDocsRequested(), simpleBatchSize());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<BatchOptionsDefinition>
        simpleDocsRequested() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simpleDocsRequested", BatchOptionsDefinitionBuilder.builder().docsRequested(25).build());
    }

    private static BsonSerializationTestSuite.TestSpec<BatchOptionsDefinition> simpleBatchSize() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simpleBatchSize", BatchOptionsDefinitionBuilder.builder().batchSize(25).build());
    }
  }
}
