package com.xgen.mongot.server.command.search.definition.request;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.server.command.search.definition.request.OptimizationFlagsDefinitionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      OptimizationFlagsDefinitionTest.DeserializationTest.class,
      OptimizationFlagsDefinitionTest.SerializationTest.class,
    })
public class OptimizationFlagsDefinitionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "optimization-flags-deserialization";
    private static final BsonDeserializationTestSuite<OptimizationFlagsDefinition> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/server/command/search/definition/request",
            SUITE_NAME,
            OptimizationFlagsDefinition::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<OptimizationFlagsDefinition>
        testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<OptimizationFlagsDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<
            BsonDeserializationTestSuite.TestSpecWrapper<OptimizationFlagsDefinition>>
        data() {
      return TEST_SUITE.withExamples(simpleOmitSearchDocumentResults(), missingAllParameters());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<OptimizationFlagsDefinition>
        simpleOmitSearchDocumentResults() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "simple-omitSearchDocumentResults",
          OptimizationFlagsDefinitionBuilder.builder().omitSearchDocumentResults(true).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<OptimizationFlagsDefinition>
        missingAllParameters() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "missing-all-parameters", OptimizationFlagsDefinitionBuilder.builder().build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "optimization-flags-serialization";
    private static final BsonSerializationTestSuite<OptimizationFlagsDefinition> TEST_SUITE =
        fromEncodable(
            "src/test/unit/resources/server/command/search/definition/request", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<OptimizationFlagsDefinition> testSpec;

    public SerializationTest(
        BsonSerializationTestSuite.TestSpec<OptimizationFlagsDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<OptimizationFlagsDefinition>>
        data() {
      return List.of(simpleOmitSearchDocumentResults());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<OptimizationFlagsDefinition>
        simpleOmitSearchDocumentResults() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple-omitSearchDocumentResults",
          OptimizationFlagsDefinitionBuilder.builder().omitSearchDocumentResults(true).build());
    }
  }
}
