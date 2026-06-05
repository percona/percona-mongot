package com.xgen.mongot.config.backup;

import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.FileUtils;
import com.xgen.mongot.util.bson.JsonCodec;
import java.io.IOException;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.jetbrains.annotations.Nullable;

public class JournalWriter {
  private final Path configJournalPath;

  public JournalWriter(Path configJournalPath) {
    this.configJournalPath = configJournalPath;
  }

  /** Writes the supplied ConfigJournal to a file at the supplied path. */
  public void persist(ConfigJournalV1 configJournal) throws IOException {
    BsonDocument bson = configJournal.toBson();
    String json = JsonCodec.toJson(bson);

    @Nullable Path parent = this.configJournalPath.getParent();
    Check.argNotNull(parent, "configJournalPath.getParent()");
    FileUtils.mkdirIfNotExist(parent);
    FileUtils.atomicallyReplace(this.configJournalPath, json);
  }
}
