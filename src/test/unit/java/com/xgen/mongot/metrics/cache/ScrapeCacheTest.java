package com.xgen.mongot.metrics.cache;

import org.junit.Assert;
import org.junit.Test;

public class ScrapeCacheTest {

  @Test
  public void testGetReturnsSupplerValue() {
    ScrapeCache cache = new ScrapeCache(() -> "data");
    Assert.assertEquals("data", cache.get());
  }

  @Test
  public void testGetPropagatesException() {
    RuntimeException ex = new RuntimeException("boom");
    ScrapeCache cache =
        new ScrapeCache(
            () -> {
              throw ex;
            });
    RuntimeException thrown = Assert.assertThrows(RuntimeException.class, cache::get);
    Assert.assertSame(ex, thrown);
  }
}
