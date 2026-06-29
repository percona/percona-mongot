package com.xgen.mongot.index.lucene.blobstore;

import com.xgen.mongot.index.blobstore.BlobstoreSnapshotterManager;
import com.xgen.mongot.index.blobstore.IndexSnapshotStatus;
import com.xgen.mongot.index.version.GenerationId;
import java.util.Optional;

/** BlobstoreSnapshotterManager extension that returns LuceneBlobstoreSnapshotter instances. */
public interface LuceneIndexSnapshotterManager extends BlobstoreSnapshotterManager {
  @Override
  Optional<? extends LuceneIndexSnapshotter> get(GenerationId generationId);

  /**
   * Returns index snapshot status.
   *
   * <p>Best-effort: returns {@link Optional#empty()} when the generation is absent or status is
   * unknown.
   *
   * @apiNote This is a <b>beta</b> API and may change or be removed in a future release.
   */
  default Optional<IndexSnapshotStatus> getIndexSnapshotStatus(GenerationId generationId) {
    return Optional.empty();
  }
}
