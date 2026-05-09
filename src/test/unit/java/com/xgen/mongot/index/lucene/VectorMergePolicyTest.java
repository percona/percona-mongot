package com.xgen.mongot.index.lucene;

import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.xgen.mongot.index.lucene.merge.VectorMergePolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;
import org.mockito.Mockito;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      VectorMergePolicyTest.TestPruneMergeSpecification.class,
      VectorMergePolicyTest.TestPolicy.class
    })
public class VectorMergePolicyTest {
  private static final Directory FAKE_SIZE_DIRECTORY =
      new Directory() {
        @Override
        public long fileLength(String name) throws IOException {
          if (!(name.endsWith(".vec") || name.endsWith(".veq"))) {
            throw new IllegalArgumentException(name);
          }
          return Long.parseLong(
              name.substring(
                  name.indexOf("_size=") + "_size=".length(), name.length() - ".vec".length()));
        }

        @Override
        public String[] listAll() throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFile(String name) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public IndexOutput createOutput(String name, IOContext context) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public IndexOutput createTempOutput(String prefix, String suffix, IOContext context)
            throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public void sync(Collection<String> names) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public void syncMetaData() throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public void rename(String source, String dest) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public IndexInput openInput(String name, IOContext context) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public Lock obtainLock(String name) throws IOException {
          throw new UnsupportedOperationException();
        }

        @Override
        public void close() throws IOException {}

        @Override
        public Set<String> getPendingDeletions() throws IOException {
          throw new UnsupportedOperationException();
        }
      };

  private static final MergePolicy.MergeContext FAKE_MERGE_CONTEXT =
      new MergePolicy.MergeContext() {
        @Override
        public int numDeletesToMerge(SegmentCommitInfo info) throws IOException {
          return info.getDelCount();
        }

        @Override
        public int numDeletedDocs(SegmentCommitInfo info) {
          throw new UnsupportedOperationException();
        }

        @Override
        public InfoStream getInfoStream() {
          throw new UnsupportedOperationException();
        }

        @Override
        public Set<SegmentCommitInfo> getMergingSegments() {
          throw new UnsupportedOperationException();
        }
      };

  private static SegmentCommitInfo makeSegment(
      String name, int maxDoc, int numDeletedDocs, long vectorDataSizeMB) {
    return makeSegment(name, maxDoc, numDeletedDocs, new long[] {vectorDataSizeMB});
  }

  private static SegmentCommitInfo makeSegment(
      String name, int maxDoc, int numDeletedDocs, long[] vectorDataSizeMB) {
    return makeSegment(name, maxDoc, numDeletedDocs, Map.of(".vec", vectorDataSizeMB));
  }

  private static SegmentCommitInfo makeSegment(
      String name, int maxDoc, int numDeletedDocs, Map<String, long[]> extensionToFileSizesMB) {
    SegmentInfo info =
        new SegmentInfo(
            FAKE_SIZE_DIRECTORY,
            Version.LATEST,
            Version.LATEST,
            name,
            maxDoc,
            false,
            false,
            TestUtil.getDefaultCodec(),
            Collections.emptyMap(),
            StringHelper.randomId(),
            Collections.singletonMap(IndexWriter.SOURCE, IndexWriter.SOURCE_MERGE),
            null);
    info.setFiles(
        extensionToFileSizesMB.entrySet().stream()
            .flatMap(
                (entry) -> {
                  String extension = entry.getKey();
                  long[] fileSizes = entry.getValue();
                  return Arrays.stream(fileSizes)
                      .mapToObj(size -> name + "_size=" + (size << 20) + extension);
                })
            .toList());
    return new SegmentCommitInfo(info, numDeletedDocs, 0, 0, 0, 0, StringHelper.randomId());
  }

  private static SegmentCommitInfo makeQuantizedSegment(
      String name, int maxDoc, int numDeletedDocs, long vectorDataSizeMB) {
    return makeSegment(name, maxDoc, numDeletedDocs, Map.of(".veq", new long[] {vectorDataSizeMB}));
  }

  private static List<List<String>> specToNames(MergePolicy.MergeSpecification spec) {
    if (spec == null) {
      return List.of();
    }
    return spec.merges.stream()
        .map(
            merge ->
                merge.segments.stream()
                    .map(info -> info.info.name)
                    .sorted()
                    .collect(Collectors.toList()))
        .collect(Collectors.toList());
  }

  private static MergePolicy.MergeSpecification makeMergeSpec(
      List<List<SegmentCommitInfo>> merges) {
    var spec = new MergePolicy.MergeSpecification();
    for (var merge : merges) {
      spec.add(new MergePolicy.OneMerge(merge));
    }
    return spec;
  }

  private static final double NO_EPSILON = 0.0;

  private static class PruneTestSpec {
    public final String name;
    public final List<List<SegmentCommitInfo>> inputSpec;
    public final List<List<String>> outputSpec;

    public final int discardedMerge;
    public final int prunedSegment;
    public final int segmentMaxSizeExceeded;
    public final int segmentHeapSizeExceeded;

    public PruneTestSpec(
        String name, List<List<SegmentCommitInfo>> inputSpec, List<List<String>> outputSpec) {
      this(name, inputSpec, outputSpec, 0, 0, 0, 0);
    }

    public PruneTestSpec(
        String name,
        List<List<SegmentCommitInfo>> inputSpec,
        List<List<String>> outputSpec,
        int discardedMerge,
        int prunedSegment,
        int segmentMaxSizeExceeded,
        int segmentHeapSizeExceeded) {
      this.name = name;
      this.inputSpec = inputSpec;
      this.outputSpec = outputSpec;
      this.discardedMerge = discardedMerge;
      this.prunedSegment = prunedSegment;
      this.segmentMaxSizeExceeded = segmentMaxSizeExceeded;
      this.segmentHeapSizeExceeded = segmentHeapSizeExceeded;
    }

    @Override
    public String toString() {
      return "PruneTestCase(" + this.name + ")";
    }
  }

  @RunWith(Parameterized.class)
  public static class TestPruneMergeSpecification {
    private final PruneTestSpec testCase;

    public TestPruneMergeSpecification(PruneTestSpec testCase) {
      this.testCase = testCase;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<PruneTestSpec> testCases() {
      return List.of(
          new PruneTestSpec(
              "simple", List.of(List.of(makeSegment("_a", 100000, 100, 10))),
                  List.of(List.of("_a"))),
          new PruneTestSpec(
              "pruneSegmentToSingle",
              List.of(
                  List.of(makeSegment("_a", 100000, 1, 900), makeSegment("_b", 10000, 0, 125))),
              List.of(List.of("_a")),
              0,
              1,
              0,
              0),
          new PruneTestSpec(
              "pruneSegmentToSingleQuantized",
              List.of(
                  List.of(
                      makeQuantizedSegment("_a", 100000, 1, 900),
                      makeQuantizedSegment("_b", 10000, 0, 125))),
              List.of(List.of("_a")),
              0,
              1,
              0,
              0),
          new PruneTestSpec(
              "segmentsUnmergedDueToQuantizedDataBelowCap",
              List.of(
                  List.of(
                      makeSegment(
                          "_a",
                          100000,
                          1,
                          Map.of(".vec", new long[] {900}, ".veq", new long[] {200})),
                      makeSegment(
                          "_b",
                          10000,
                          0,
                          Map.of(".vec", new long[] {125}, ".veq", new long[] {20})))),
              List.of(List.of("_a", "_b")),
              0,
              0,
              0,
              0),
          new PruneTestSpec(
              "pruneSegmentToEmpty",
              List.of(
                  List.of(makeSegment("_a", 100000, 0, 900), makeSegment("_b", 10000, 0, 125))),
              List.of(),
              1,
              0,
              0,
              0),
          new PruneTestSpec(
              "pruneSegmentGreedily",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, 900),
                      makeSegment("_b", 10000, 0, 125),
                      makeSegment("_c", 1000, 0, 12),
                      makeSegment("_d", 10000, 0, 125))),
              List.of(List.of("_a", "_c")),
              0,
              2,
              0,
              0),
          new PruneTestSpec(
              "tiebreakByName",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, 900),
                      makeSegment("_c", 10000, 0, 90),
                      makeSegment("_b", 10000, 0, 90))),
              List.of(List.of("_a", "_c")),
              0,
              1,
              0,
              0),
          new PruneTestSpec(
              "prorateDeletes",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, 900), makeSegment("_b", 10000, 5000, 150))),
              List.of(List.of("_a", "_b")),
              0,
              0,
              0,
              0),
          new PruneTestSpec(
              "globalBudget",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, 900), makeSegment("_b", 10000, 5000, 150)),
                  List.of(
                      makeSegment("_c", 100000, 0, 900), makeSegment("_d", 10000, 5000, 150))),
              List.of(List.of("_a", "_b")),
              1,
              0,
              0,
              0),
          new PruneTestSpec(
              "globalBudgetGreedy",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, 900), makeSegment("_b", 10000, 5000, 150)),
                  List.of(
                      makeSegment("_c", 100000, 0, 900), makeSegment("_d", 10000, 5000, 150)),
                  List.of(makeSegment("_e", 10000, 0, 90), makeSegment("_f", 10000, 5000, 150))),
              List.of(List.of("_a", "_b"), List.of("_e", "_f")),
              1,
              0,
              0,
              0),
          new PruneTestSpec(
              "jumbo",
              List.of(List.of(makeSegment("_a", 100000, 10000, 1536))),
              List.of(List.of("_a")),
              0,
              0,
              1,
              0),
          new PruneTestSpec(
              "jumboAndMore",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 10000, 1536),
                      makeSegment("_b", 1000, 0, 50),
                      makeSegment("_c", 1000, 0, 50))),
              List.of(List.of("_a")),
              0,
              2,
              1,
              0),
          new PruneTestSpec(
              "jumboSelectDeleted",
              List.of(
                  List.of(makeSegment("_a", 1000, 0, 1536), makeSegment("_b", 1000, 1, 1536))),
              List.of(List.of("_b")),
              0,
              1,
              1,
              0),
          new PruneTestSpec(
              "skipJumboSegments",
              List.of(
                  List.of(
                      makeSegment("_jumbo1", 1000, 0, 2024),
                      makeSegment("_jumbo2", 1000, 0, 2024),
                      makeSegment("_a", 100, 0, 10),
                      makeSegment("_b", 1000, 0, 10))),
              List.of(List.of("_a", "_b")),
              0,
              2,
              2,
              0),
          new PruneTestSpec(
              "multipleInputFields",
              List.of(
                  List.of(
                      makeSegment("_a", 100000, 0, new long[] {700, 10}),
                      makeSegment("_b", 100000, 0, new long[] {20, 500}),
                      makeSegment("_c", 100, 1, 5))),
              List.of(List.of("_a", "_c")),
              0,
              1,
              0,
              0),
          new PruneTestSpec(
              "heapPruneSegmentOneDelete",
              List.of(
                  List.of(
                      makeSegment("_a", 100, 1, 10),
                      makeSegment("_b", 800000, 0, 10))),
              List.of(List.of("_a")),
              0,
              1,
              0,
              1),
          new PruneTestSpec(
              "heapPruneMerge",
              List.of(
                  List.of(
                      makeSegment("_a", 800000, 0, 10),
                      makeSegment("_b", 800000, 0, 10))),
              List.of(),
              1,
              0,
              0,
              2),
          new PruneTestSpec(
              "heapPruneNoSegments",
              List.of(
                  List.of(
                      makeSegment("_a", 1000, 0, 10),
                      makeSegment("_b", 1000, 0, 10),
                      makeSegment("_c", 1000, 0, 10),
                      makeSegment("_d", 1000, 0, 10))),
              List.of(List.of("_a", "_b", "_c", "_d")),
              0,
              0,
              0,
              0),
          new PruneTestSpec(
              "heapPruneOneSegment",
              List.of(
                  List.of(
                      makeSegment("_a", 1000, 0, 10),
                      makeSegment("_b", 1000, 0, 10),
                      makeSegment("_c", 100000000, 0, 10),
                      makeSegment("_d", 1000, 0, 10))),
              List.of(List.of("_a", "_b", "_d")),
              0,
              1,
              0,
              1),
          new PruneTestSpec(
              "heapPruneThreeSegments",
              List.of(
                  List.of(
                      makeSegment("_a", 100, 0, 10),
                      makeSegment("_b", 700000, 100, 10),
                      makeSegment("_c", 100, 0, 10),
                      makeSegment("_d", 100, 0, 10))),
              List.of(List.of("_b")),
              0,
              3,
              0,
              1),
          new PruneTestSpec(
              "heapPruneSegmentsSegmentBudget",
              List.of(
                  List.of(
                      makeSegment("_a", 550000, 0, 10),
                      makeSegment("_b", 10, 0, 10),
                      makeSegment("_c", 10, 0, 10),
                      makeSegment("_d", 550000, 0, 10))),
              List.of(List.of("_b", "_c")),
              0,
              2,
              0,
              2),
          new PruneTestSpec(
              "heapPruneSegmentsGlobalBudget",
              List.of(
                  List.of(
                      makeSegment("_a", 200000, 0, 10), // 200000 maxDocs is ~400MB heap usage.
                      makeSegment("_b", 200000, 0, 10),
                      makeSegment("_c", 200000, 0, 10),
                      makeSegment("_d", 200000, 0, 10))),
              List.of(List.of("_a", "_b")),
              0,
              2,
              0,
              0));
    }

    @Test
    public void runTest() throws IOException {
      MeterRegistry meterRegistry = new SimpleMeterRegistry();
      var policy =
          VectorMergePolicy.newBuilder()
              .setMaxCompoundDataBytes(50L << 20)
              .setMaxVectorInputBytes(1024L << 20)
              .setMergeBudgetBytes(1536L << 20)
              .setSegmentHeapBytesBudget(1024L << 20)
              .setGlobalHeapBytesBudget(1536L << 20)
              .setMaxConn(DEFAULT_MAX_CONN)
              .build(spy(MergePolicy.class), meterRegistry);
      Assert.assertEquals(
          this.testCase.outputSpec,
          specToNames(
              policy.maybePruneMergeSpecification(
                  makeMergeSpec(this.testCase.inputSpec), FAKE_MERGE_CONTEXT)));
      Assert.assertEquals(
          this.testCase.discardedMerge,
          meterRegistry.find("vectorMergePolicy.discardedMerge").counter().count(),
          NO_EPSILON);
      Assert.assertEquals(
          this.testCase.prunedSegment,
          meterRegistry.find("vectorMergePolicy.prunedSegment").counter().count(),
          NO_EPSILON);
      Assert.assertEquals(
          this.testCase.segmentMaxSizeExceeded,
          meterRegistry.find("vectorMergePolicy.segmentMaxSizeExceeded").counter().count(),
          NO_EPSILON);
      Assert.assertEquals(
          this.testCase.segmentHeapSizeExceeded,
          meterRegistry.find("vectorMergePolicy.segmentHeapSizeExceeded").counter().count(),
          NO_EPSILON);
    }
  }

  public static class TestPolicy {
    @Test
    public void testPruneNullMergeSpec() throws IOException {
      MeterRegistry meterRegistry = new SimpleMeterRegistry();
      var policy =
          VectorMergePolicy.newBuilder()
              .setMaxCompoundDataBytes(50L << 20)
              .setMaxVectorInputBytes(1024L << 20)
              .setMergeBudgetBytes(1536L << 20)
              .setSegmentHeapBytesBudget(1024L << 20)
              .setGlobalHeapBytesBudget(1536L << 20)
              .setMaxConn(DEFAULT_MAX_CONN)
              .build(spy(MergePolicy.class), meterRegistry);
      Assert.assertEquals(null, policy.maybePruneMergeSpecification(null, FAKE_MERGE_CONTEXT));
    }

    @Test
    public void testBudgetSemaphore() throws IOException {
      MeterRegistry meterRegistry = new SimpleMeterRegistry();
      var policy =
          VectorMergePolicy.newBuilder()
              .setMaxCompoundDataBytes(50L << 20)
              .setMaxVectorInputBytes(1024L << 20)
              .setMergeBudgetBytes(1536L << 20)
              .setSegmentHeapBytesBudget(1024L << 20)
              .setGlobalHeapBytesBudget(1536L << 20)
              .setMaxConn(DEFAULT_MAX_CONN)
              .build(spy(MergePolicy.class), meterRegistry);

      Assert.assertEquals(
          0.0, meterRegistry.find("vectorMergePolicy.budgetBytesUsed").gauge().value(), NO_EPSILON);
      Assert.assertEquals(
          0.0,
          meterRegistry.find("vectorMergePolicy.budgetBytesHeapUsed").gauge().value(),
          NO_EPSILON);
      Assert.assertEquals(
          1536L << 20,
          meterRegistry.find("vectorMergePolicy.budgetBytesTotal").gauge().value(),
          NO_EPSILON);

      // Create 3 merges:
      // * a large one that schedules due to deleted docs estimation.
      // * a moderate size discarded merge.
      // * a small one that schedules.
      // Verify budget bytes after scheduling and when a completion occurs.
      var spec =
          policy.maybePruneMergeSpecification(
              makeMergeSpec(
                  List.of(
                      List.of(
                          makeSegment("_a", 100000, 100, 1000),
                          makeSegment("_b", 80000, 40000, 800)),
                      List.of(makeSegment("_c", 100000, 40000, 1000)),
                      List.of(makeSegment("_d", 1000, 0, 10), makeSegment("_e", 1000, 0, 10)))),
              FAKE_MERGE_CONTEXT);
      Assert.assertEquals(
          1019 << 20,
          meterRegistry.find("vectorMergePolicy.budgetBytesUsed").gauge().value(),
          NO_EPSILON);
      double heapAfterSchedule =
          meterRegistry.find("vectorMergePolicy.budgetBytesHeapUsed").gauge().value();
      Assert.assertTrue(
          "Expected heap-used > 0 after scheduling vector merges, got " + heapAfterSchedule,
          heapAfterSchedule > 0);

      spec.merges.get(1).mergeFinished(true, false);
      Assert.assertEquals(
          999 << 20,
          meterRegistry.find("vectorMergePolicy.budgetBytesUsed").gauge().value(),
          NO_EPSILON);
      double heapAfterOneFinished =
          meterRegistry.find("vectorMergePolicy.budgetBytesHeapUsed").gauge().value();
      Assert.assertTrue(
          "Expected heap-used to decrease after a merge finishes ("
              + heapAfterOneFinished
              + " vs "
              + heapAfterSchedule
              + ")",
          heapAfterOneFinished < heapAfterSchedule);
      Assert.assertTrue(heapAfterOneFinished > 0);

      spec.merges.get(0).mergeFinished(true, false);
      Assert.assertEquals(
          0.0, meterRegistry.find("vectorMergePolicy.budgetBytesUsed").gauge().value(), NO_EPSILON);
      Assert.assertEquals(
          0.0,
          meterRegistry.find("vectorMergePolicy.budgetBytesHeapUsed").gauge().value(),
          NO_EPSILON);
    }

    @Test
    public void testCompoundFilePolicy() throws IOException {
      var meterRegistry = new SimpleMeterRegistry();
      var parentPolicy = Mockito.mock(MergePolicy.class);
      var policy =
          VectorMergePolicy.newBuilder()
              .setMaxCompoundDataBytes(100L << 20)
              .build(parentPolicy, meterRegistry);

      var emptySegmentInfos = new SegmentInfos(Version.LATEST.major);
      var smolSegment = makeSegment("_a", 10000, 0, 80);
      var bigSegment = makeSegment("_b", 1000000, 0, 8000);

      // When the underlying policy returns false our policy doesn't matter.
      when(parentPolicy.useCompoundFile(any(), any(), any())).thenReturn(false);
      Assert.assertFalse(
          policy.useCompoundFile(emptySegmentInfos, smolSegment, FAKE_MERGE_CONTEXT));
      Assert.assertFalse(
          policy.useCompoundFile(emptySegmentInfos, smolSegment, FAKE_MERGE_CONTEXT));
      Assert.assertEquals(
          0.0,
          meterRegistry.find("vectorMergePolicy.skippedCompoundFile").counter().count(),
          NO_EPSILON);

      // When the underlying policy returns true we apply our policy to veto compounding.
      when(parentPolicy.useCompoundFile(any(), any(), any())).thenReturn(true);
      Assert.assertTrue(policy.useCompoundFile(emptySegmentInfos, smolSegment, FAKE_MERGE_CONTEXT));
      Assert.assertEquals(
          0.0,
          meterRegistry.find("vectorMergePolicy.skippedCompoundFile").counter().count(),
          NO_EPSILON);
      Assert.assertFalse(policy.useCompoundFile(emptySegmentInfos, bigSegment, FAKE_MERGE_CONTEXT));
      Assert.assertEquals(
          1.0,
          meterRegistry.find("vectorMergePolicy.skippedCompoundFile").counter().count(),
          NO_EPSILON);
    }
  }
}
