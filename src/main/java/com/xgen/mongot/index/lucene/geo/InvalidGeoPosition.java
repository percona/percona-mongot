package com.xgen.mongot.index.lucene.geo;

import org.jetbrains.annotations.Nullable;

public class InvalidGeoPosition extends Exception {

  public InvalidGeoPosition(@Nullable String message) {
    super(message);
  }
}
