package com.xgen.mongot.lifecycle;

import com.xgen.testing.BsonSerializationTestSuite;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {LifecycleConfigTest.SerializationTest.class, LifecycleConfigTest.ConfigTest.class})
public class LifecycleConfigTest {

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "lifecycle-config-serialization";
    private static final BsonSerializationTestSuite<LifecycleConfig> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable("src/test/unit/resources/lifecycle", SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<LifecycleConfig> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<LifecycleConfig> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<LifecycleConfig>> data() {
      return List.of(fullConfig(), noInitializationThreads());
    }

    private static BsonSerializationTestSuite.TestSpec<LifecycleConfig> fullConfig() {
      return BsonSerializationTestSuite.TestSpec.create(
          "full config", LifecycleConfig.create(true, Optional.of(4)));
    }

    private static BsonSerializationTestSuite.TestSpec<LifecycleConfig> noInitializationThreads() {
      return BsonSerializationTestSuite.TestSpec.create(
          "no init executor threads", LifecycleConfig.create(true, Optional.empty()));
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }
  }

  public static class ConfigTest {
    @Test
    public void testConfigDefault() {
      var config = LifecycleConfig.getDefault();
      Assert.assertEquals(true, config.useLifecycleManager);
    }
  }
}
