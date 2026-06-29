package com.xgen.mongot.server.command.search.definition;

import static com.xgen.testing.BsonSerializationTestSuite.load;

import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite.TestSpec;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      PlanShardedSearchCommandResponseDefinitionTest.SerializationTest.class,
      PlanShardedSearchCommandResponseDefinitionTest.ShardedSearchPlanTest.class
    })
public class PlanShardedSearchCommandResponseDefinitionTest {
  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "plan-sharded-search-response";
    private static final BsonSerializationTestSuite<PlanShardedSearchCommandResponseDefinition>
        TEST_SUITE =
            load(
                "src/test/unit/resources/server/command/search/definition/",
                SUITE_NAME,
                PlanShardedSearchCommandResponseDefinition::toBson);

    private final TestSpec<PlanShardedSearchCommandResponseDefinition> testSpec;

    public SerializationTest(TestSpec<PlanShardedSearchCommandResponseDefinition> testSpec) {
      this.testSpec = testSpec;
    }

    /** Test data. */
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<TestSpec<PlanShardedSearchCommandResponseDefinition>> data() {
      return List.of(empty(), withSortSpec());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static TestSpec<PlanShardedSearchCommandResponseDefinition> empty() {
      return TestSpec.create(
          "empty",
          PlanShardedSearchCommandResponseDefinition.create(
              new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(
                  new ArrayList<>(),
                  PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.Fields.SORT_SPEC
                      .getDefaultValue())));
    }

    private static TestSpec<PlanShardedSearchCommandResponseDefinition> withSortSpec() {
      return TestSpec.create(
          "with sortSpec",
          PlanShardedSearchCommandResponseDefinition.create(
              new PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan(
                  new ArrayList<>(),
                  new BsonDocument()
                      .append("$searchSortValues._0", new BsonInt32(1))
                      .append("$searchSortValues._1", new BsonInt32(-1)))));
    }
  }

  public static class ShardedSearchPlanTest {
    @Test
    public void testDefaultSortSpec() {
      Assert.assertEquals(
          new BsonDocument().append("$searchScore", new BsonInt32(-1)),
          PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.DEFAULT_SORT_SPEC);
    }

    @Test
    public void testCurrentProtocolVersion() {
      Assert.assertEquals(
          1, PlanShardedSearchCommandResponseDefinition.ShardedSearchPlan.CURRENT_PROTOCOL_VERSION);
    }
  }
}
