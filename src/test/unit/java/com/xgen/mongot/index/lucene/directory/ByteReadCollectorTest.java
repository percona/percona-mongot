package com.xgen.mongot.index.lucene.directory;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xgen.mongot.metrics.MetricsFactory;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.junit.Test;

public class ByteReadCollectorTest {
  private static final Path PATH = Path.of(System.getenv("TEST_TMPDIR"));

  @Test
  public void testReadByteWithoutCollector() throws IOException {
    IndexInput mockIndexInput = mock(IndexInput.class);
    when(mockIndexInput.readByte()).thenReturn((byte) 1);
    FileSystemDirectory directory = new FileSystemDirectory(PATH, Optional.empty());
    FileSystemDirectory spyDirectory = spy(directory);
    String testFileName = "testFile.vec";
    doReturn(mockIndexInput)
        .when(spyDirectory)
        .openInputBase(eq(testFileName), any(IOContext.class));
    var result = spyDirectory.openInput(testFileName, IOContext.DEFAULT).readByte();
    assertEquals(1, result); // Verify the byte read is correct
    verify(mockIndexInput).readByte(); // Ensure the underlying IndexInput is called
  }

  @Test
  public void testReadByteWithCollector() throws IOException {
    IndexInput mockIndexInput = mock(IndexInput.class);
    when(mockIndexInput.readByte()).thenReturn((byte) 1);
    var registry = new SimpleMeterRegistry();
    var metricsFactory = new MetricsFactory("test", registry);
    var byteReadCollector = new ByteReadCollector(metricsFactory);

    FileSystemDirectory directory = new FileSystemDirectory(PATH, Optional.of(byteReadCollector));
    FileSystemDirectory spyDirectory = spy(directory);
    String testFileName = "testFile.vec";
    doReturn(mockIndexInput)
        .when(spyDirectory)
        .openInputBase(eq(testFileName), any(IOContext.class));
    spyDirectory.openInput(testFileName, IOContext.DEFAULT).readByte();
    assertEquals(
        1,
        (int)
            metricsFactory
                .counter(ByteReadCollector.METRIC_NAME, Tags.of("fileType", "vec"))
                .count());
  }

  @Test
  public void testReadBytesWithoutCollector() throws IOException {
    IndexInput mockIndexInput = mock(IndexInput.class);
    FileSystemDirectory directory = new FileSystemDirectory(PATH, Optional.empty());
    FileSystemDirectory spyDirectory = spy(directory);
    String testFileName = "testFile.vec";
    doReturn(mockIndexInput)
        .when(spyDirectory)
        .openInputBase(eq(testFileName), any(IOContext.class));
    var buf = new byte[100];
    spyDirectory.openInput(testFileName, IOContext.DEFAULT).readBytes(buf, 50, 20, false);
    verify(mockIndexInput).readBytes(buf, 50, 20, false);
  }

  @Test
  public void testReadBytesWithCollector() throws IOException {
    IndexInput mockIndexInput = mock(IndexInput.class);
    var registry = new SimpleMeterRegistry();
    var metricsFactory = new MetricsFactory("test", registry);
    var byteReadCollector = new ByteReadCollector(metricsFactory);

    FileSystemDirectory directory = new FileSystemDirectory(PATH, Optional.of(byteReadCollector));
    FileSystemDirectory spyDirectory = spy(directory);
    String testFileName = "testFile.vec";
    doReturn(mockIndexInput)
        .when(spyDirectory)
        .openInputBase(eq(testFileName), any(IOContext.class));
    var buf = new byte[100];
    spyDirectory.openInput(testFileName, IOContext.DEFAULT).readBytes(buf, 50, 20);
    assertEquals(
        20,
        (int)
            metricsFactory
                .counter(ByteReadCollector.METRIC_NAME, Tags.of("fileType", "vec"))
                .count());
  }

  @Test
  public void testMultipleReadBytesWithCollector() throws IOException {
    IndexInput mockIndexInput = mock(IndexInput.class);
    var registry = new SimpleMeterRegistry();
    var metricsFactory = new MetricsFactory("test", registry);
    var byteReadCollector = new ByteReadCollector(metricsFactory);

    FileSystemDirectory directory = new FileSystemDirectory(PATH, Optional.of(byteReadCollector));
    FileSystemDirectory spyDirectory = spy(directory);
    String testFileName = "testFile.vec";
    doReturn(mockIndexInput)
        .when(spyDirectory)
        .openInputBase(eq(testFileName), any(IOContext.class));
    var buf = new byte[100];
    var indexInput = spyDirectory.openInput(testFileName, IOContext.DEFAULT);
    indexInput.readBytes(buf, 50, 20);
    indexInput.readBytes(buf, 50, 20);
    verify(mockIndexInput, times(2)).readBytes(buf, 50, 20);
    assertEquals(1, registry.getMeters().size());
    assertEquals(
        40,
        (int)
            metricsFactory
                .counter(ByteReadCollector.METRIC_NAME, Tags.of("fileType", "vec"))
                .count());
  }
}
