package com.xgen.mongot.catalogservice;

import com.xgen.mongot.util.LoggableException;

public class TopologyMismatchException extends LoggableException {

  public TopologyMismatchException(String message) {
    super(message);
  }
}
