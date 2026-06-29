package com.xgen.mongot.index.lucene.explain.tracing;

import com.xgen.mongot.index.lucene.explain.information.SearchExplainInformation;
import com.xgen.mongot.util.BsonUtils;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

public class ExplainTooLargeException extends IOException {
  public ExplainTooLargeException(@Nullable String message) {
    super(message);
  }

  public static void validate(SearchExplainInformation explainInformation)
      throws ExplainTooLargeException {
    if (BsonUtils.isOversized(explainInformation.toBson())) {
      throw new ExplainTooLargeException(
          "Serialized explain is larger than max allowed BsonDocument size");
    }
  }
}
