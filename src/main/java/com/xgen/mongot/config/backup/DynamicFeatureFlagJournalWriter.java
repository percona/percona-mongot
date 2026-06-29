package com.xgen.mongot.config.backup;

import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.FileUtils;
import com.xgen.mongot.util.bson.JsonCodec;
import java.io.IOException;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.jetbrains.annotations.Nullable;

public class DynamicFeatureFlagJournalWriter {

  private final Path dynamicFeatureFlagJournalPath;

  public DynamicFeatureFlagJournalWriter(Path dynamicFeatureFlagJournalPath) {
    this.dynamicFeatureFlagJournalPath = dynamicFeatureFlagJournalPath;
  }

  public Path dynamicFeatureFlagJournalPath() {
    return this.dynamicFeatureFlagJournalPath;
  }

  /** Writes the supplied {@link DynamicFeatureFlagJournal} to a file at the supplied path. */
  public void persist(DynamicFeatureFlagJournal dynamicFeatureFlagJournal) throws IOException {
    BsonDocument bson = dynamicFeatureFlagJournal.toBson();
    String json = JsonCodec.toJson(bson);

    @Nullable Path parent = this.dynamicFeatureFlagJournalPath.getParent();
    Check.argNotNull(parent, "dynamicFeatureFlagJournalPath.getParent()");
    FileUtils.mkdirIfNotExist(parent);
    FileUtils.atomicallyReplace(this.dynamicFeatureFlagJournalPath, json);
  }
}
