package com.xgen.mongot.config.provider.community;

import static org.junit.Assert.assertEquals;

import com.xgen.mongot.server.executors.RegularBlockingRequestSettings;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

public class RegularBlockingRequestConfigMapperTest {

  private static final Path TUNING_CONFIG =
      Path.of("src/test/unit/resources/config/provider/community/communityConfigTuning.yaml");

  @Test
  public void createRegularBlockingRequestSettings_honorsConfiguredValues() throws Exception {
    CommunityConfig config = CommunityConfig.readFromFile(TUNING_CONFIG).config();

    RegularBlockingRequestSettings settings =
        RegularBlockingRequestConfigMapper.createRegularBlockingRequestSettings(
            config.advancedConfigs().flatMap(AdvancedConfigs::regularBlockingRequestConfig));

    assertEquals(3.0, settings.threadPoolSizeMultiplier(), 0.0);
    assertEquals(30.0, settings.queueCapacityMultiplier(), 0.0);
    assertEquals(true, settings.virtualQueueCapacity());
    assertEquals(
        RegularBlockingRequestSettings.Mode.FIXED_POOL_UNBOUNDED_QUEUE, settings.getMode());
  }

  @Test
  public void createRegularBlockingRequestSettings_appliesDefaultsWhenUnset() {
    RegularBlockingRequestSettings settings =
        RegularBlockingRequestConfigMapper.createRegularBlockingRequestSettings(Optional.empty());

    assertEquals(RegularBlockingRequestSettings.defaults(), settings);
    assertEquals(RegularBlockingRequestSettings.Mode.UNBOUNDED_CACHING, settings.getMode());
  }

  @Test
  public void createRegularBlockingRequestSettings_nonPositiveMultipliersFallBackToDefaults() {
    RegularBlockingRequestSettings settings =
        RegularBlockingRequestConfigMapper.createRegularBlockingRequestSettings(
            Optional.of(
                new RegularBlockingRequestConfig(
                    Optional.of(0.0), Optional.of(-1.0), Optional.of(false))));

    assertEquals(0.0, settings.threadPoolSizeMultiplier(), 0.0);
    assertEquals(0.0, settings.queueCapacityMultiplier(), 0.0);
    assertEquals(RegularBlockingRequestSettings.Mode.UNBOUNDED_CACHING, settings.getMode());
  }
}
