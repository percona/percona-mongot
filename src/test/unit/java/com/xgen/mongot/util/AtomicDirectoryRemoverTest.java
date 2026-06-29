package com.xgen.mongot.util;

import com.xgen.testing.TestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AtomicDirectoryRemoverTest {
  @Test
  public void testMakesTrashDirIfNotExist() throws IOException {
    TemporaryFolder tempDir = TestUtils.getTempFolder();
    File root = tempDir.getRoot();
    Path dir = root.toPath().resolve("dir");

    assertNotExist(dir);
    remover(dir);
    assertExists(dir);

    // should not throw if directory does exist
    remover(dir);
  }

  @Test
  public void testCleansTrashDirectoryWhenInitialized() throws IOException {
    Path dir = tempDir();

    writeNestedFiles(dir);

    remover(dir);
    assertDirEmpty(dir);
  }

  @Test
  public void testAtomicallyRemovesEmptyDirectory() throws IOException {
    Path tempDir = tempDir();
    Path dir = tempDir.resolve("my_dir");
    Path trash = tempDir.resolve("trash");

    FileUtils.mkdirIfNotExist(dir);

    remover(trash).deleteDirectory(dir);
    assertNotExist(dir);
    assertDirEmpty(trash);
  }

  public AtomicDirectoryRemover remover(Path trash) throws IOException {
    return new AtomicDirectoryRemover(trash);
  }

  @Test
  public void testAtomicallyRemovesNonEmptyDirectory() throws IOException {
    Path tempDir = tempDir();
    Path dir = tempDir.resolve("my_dir");
    Path trash = tempDir.resolve("trash");

    writeNestedFiles(dir);

    remover(trash).deleteDirectory(dir);
    assertNotExist(dir);
    assertDirEmpty(trash);
  }

  @Test
  public void testDeleteFilesInDirectory() throws IOException {
    Path tempDir = tempDir();
    Path dir = tempDir.resolve("my_dir");
    Path trash = tempDir.resolve("trash");

    writeNestedFiles(dir);

    remover(trash).deleteFilesInDirectory(dir);
    assertExists(dir);

    try (var directoryEntries = Files.walk(dir)) {
      Assert.assertTrue(directoryEntries.allMatch(path -> path.toFile().isDirectory()));
    }
    Assert.assertTrue(dir.resolve("nested").toFile().exists());
    assertDirEmpty(trash);
  }

  /**
   * While this isn't the intended use, tests that atomic removal of directories already in the
   * trash directory succeeds.
   */
  @Test
  public void testRemovesDirectoryAlreadyInTrash() throws IOException {
    Path tempDir = tempDir();
    Path trash = tempDir.resolve("trash");
    Path dir = trash.resolve("my_dir");
    // we must initialize remover first, o.w it will remove `dir`
    AtomicDirectoryRemover remover = remover(trash);

    // now create some data inside the trash:
    writeNestedFiles(dir);

    remover.deleteDirectory(dir);
    assertNotExist(dir);
    assertDirEmpty(trash);
  }

  @Test
  public void testRemovesTrashDirectoryThrows() throws IOException {
    Path tempDir = tempDir();
    Path trash = tempDir.resolve("trash");
    AtomicDirectoryRemover remover = remover(trash);

    Assert.assertThrows(IllegalArgumentException.class, () -> remover.deleteDirectory(trash));
  }

  @Test
  public void testRemovesNonExistingDirectoryIgnores() throws IOException {
    Path tempDir = tempDir();
    Path trash = tempDir.resolve("trash");
    AtomicDirectoryRemover remover = remover(trash);

    Path doesNotExist = tempDir.resolve("dir");
    assertNotExist(doesNotExist);

    // shouldn't throw:
    remover.deleteDirectory(doesNotExist);

    assertNotExist(doesNotExist);
  }

  @Test
  public void testAtomicallyRemovesFileThrows() throws IOException {
    Path tempDir = tempDir();
    Path trash = tempDir.resolve("trash");
    AtomicDirectoryRemover remover = remover(trash);

    Path validFile = tempDir.resolve("file");
    FileUtils.atomicallyReplace(validFile, "contents");

    Assert.assertThrows(IllegalArgumentException.class, () -> remover.deleteDirectory(validFile));
  }

  private static Path tempDir() throws IOException {
    TemporaryFolder tempDir = TestUtils.getTempFolder();
    return tempDir.getRoot().toPath();
  }

  private static void writeNestedFiles(Path dir) throws IOException {
    // write some nested files and directories
    FileUtils.mkdirIfNotExist(dir.resolve("nested").resolve("nested2"));
    FileUtils.atomicallyReplace(dir.resolve("file1"), "contents");
    FileUtils.atomicallyReplace(dir.resolve("nested").resolve("file2"), "contents");
  }

  private static void assertDirEmpty(Path dir) {
    File[] files = dir.toFile().listFiles();
    Assert.assertNotNull(files);
    Assert.assertEquals(0, files.length);
  }

  private static void assertExists(Path dir) {
    Assert.assertTrue(dir.toFile().exists());
  }

  private static void assertNotExist(Path dir) {
    Assert.assertFalse(dir.toFile().exists());
  }
}
