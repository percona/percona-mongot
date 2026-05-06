package com.xgen.mongot.server.command.search.definition.request;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;
import static com.xgen.testing.BsonSerializationTestSuite.fromEncodable;

import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpec;
import com.xgen.testing.BsonDeserializationTestSuite.TestSpecWrapper;
import com.xgen.testing.BsonDeserializationTestSuite.ValidSpec;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.server.command.search.definition.request.ExplainDefinitionBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ExplainDefinitionTest.DeserializationTest.class,
      ExplainDefinitionTest.SerializationTest.class,
    })
public class ExplainDefinitionTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "explain-deserialization";
    private static final BsonDeserializationTestSuite<ExplainDefinition> TEST_SUITE =
        fromDocument(
            "src/test/unit/resources/server/command/search/definition/request",
            SUITE_NAME,
            ExplainDefinition::fromBson);

    private final TestSpecWrapper<ExplainDefinition> testSpec;

    public DeserializationTest(TestSpecWrapper<ExplainDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpecWrapper<ExplainDefinition>> data() {
      return TEST_SUITE.withExamples(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static ValidSpec<ExplainDefinition> simple() {
      return TestSpec.valid(
          "simple",
          ExplainDefinitionBuilder.builder().verbosity(Explain.Verbosity.EXECUTION_STATS).build());
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "explain-serialization";
    private static final BsonSerializationTestSuite<ExplainDefinition> TEST_SUITE =
        fromEncodable(
            "src/test/unit/resources/server/command/search/definition/request", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ExplainDefinition> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<ExplainDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ExplainDefinition>> data() {
      return List.of(simple());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ExplainDefinition> simple() {
      return BsonSerializationTestSuite.TestSpec.create(
          "simple",
          ExplainDefinitionBuilder.builder().verbosity(Explain.Verbosity.QUERY_PLANNER).build());
    }
  }
}
