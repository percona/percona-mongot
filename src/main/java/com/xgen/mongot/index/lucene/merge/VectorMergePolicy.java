package com.xgen.mongot.index.lucene.merge;

import static com.xgen.mongot.util.Check.checkState;
import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Var;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.xgen.mongot.metrics.MetricsFactory;
import com.xgen.mongot.metrics.ServerStatusDataExtractor;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.lucene.index.FilterMergePolicy;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.MergeTrigger;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.util.RamUsageEstimator;

/**
 * Wrapper for <code>MergePolicy</code> that applies additional limits for vector indices.
 *
 * <p>Vector data is easily merged, but HNSW vector indices are not -- once segments are merged the
 * index is rebuilt from scratch. This requires random access to the entire vector data set to
 * compute distances and prune the graph. If the size of the merge exceeds available memory, merge
 * operations page fault endlessly and may not complete in a reasonable amount of time.
 *
 * <p>To combat this problem we apply two limits: one to the size of vector input for any given
 * merge, and another to the amount of memory scheduled for merge at any one time for all indices
 * using this merge policy. The first part is necessary to avoid doing a single large problematic
 * merge, the second is necessary to avoid concurrent merges that together exceed available memory.
 *
 * <p>When computing the amount of memory an input segment "costs" we use the size of largest vector
 * data field adjust for document deletion, the reason being that if multiple vector fields are
 * present they are merged serially rather than in parallel.
 */
public class VectorMergePolicy extends FilterMergePolicy {
  /**
   * These are extensions used by Lucene9xVectorsFormat and Lucene9xScalarQuantizedVectorsFormat to
   * store raw and quantized vector data (as opposed to hnsw indices or metadata). These members are
   * not public in the codecs, so we reproduce values.
   */
  private static final String VECTOR_DATA_EXTENSION = "vec";

  private static final String QUANTIZED_VECTOR_DATA_EXTENSION = "veq";

  private final long maxCompoundDataSize;
  private final long maxVectorInputBytes;
  private final long budgetBytesTotal;
  // Max allowed heap size for a single field of a merge.
  private final long segmentHeapBytesBudget;
  // Max allowed heap size for in-flight vector merges.
  private final long globalHeapBytesBudget;
  private final int maxConn;

  @GuardedBy("this")
  private long budgetBytesUsed;

  @GuardedBy("this")
  private long budgetBytesHeapUsed;

  // Track every time we veto the underlying policy's decision to compound a file.
  private final Counter skippedCompoundFile;
  // Track every segment merge from the underlying policy that we discard.
  private final Counter discardedMerge;
  // Track every segment the underlying policy would merge that we removed from the list.
  private final Counter prunedSegment;
  // Count every merged segment that exceeds the per-segment vector input max.
  private final Counter segmentMaxSizeExceeded;
  // Count every merged segment that exceeds the per-segment allowed heap max.
  private final Counter segmentHeapSizeExceeded;

  public static class Builder {
    private long maxCompoundDataBytes = Bytes.ofMebi(100).toBytes();
    private long maxVectorInputBytes = Bytes.ofGibi(2).toBytes();
    private long mergeBudgetBytes = Bytes.ofGibi(4).toBytes();
    private long segmentHeapBytesBudget = Bytes.ofGibi(2).toBytes();
    private long globalHeapBytesBudget = Bytes.ofGibi(4).toBytes();
    // Set maxConn default to DEFAULT_MAX_CONN.
    private int maxConn = DEFAULT_MAX_CONN;

    private Builder() {}

    /**
     * Maximum output vector field size to encode as a compound file.
     *
     * <p>When a segment is encoded as a compound file we are unable to determine the size of any
     * vector field(s) when budgeting vector merges. These are recorded as input size zero on the
     * assumption that there isn't a significant amount of data. Increasing this value decreases
     * <code>VectorMergePolicy</code>'s visibility into how much memory each merge will use.
     *
     * <p>Default: 100 MiB.
     *
     * @param maxCompoundDataBytes maximum vector field size to encode as a compound file.
     * @return this
     */
    public Builder setMaxCompoundDataBytes(long maxCompoundDataBytes) {
      this.maxCompoundDataBytes = maxCompoundDataBytes;
      return this;
    }

    /**
     * Maximum number of input vector bytes to allow for a single field of a merge.
     *
     * <p>Merging requires random access to vector data in all input segments, meaning the working
     * set is O(vector_input_size). Individual fields are merged in serial rather than in parallel
     * so vector_input_size is field_sizes.max() rather than field_sizes.reduce(sum). This value
     * should not exceed the amount of memory on the host less java heap/jvm overhead.
     *
     * <p>NOTE: we will still merge segments with vector input exceeding this size to avoid a
     * situation where a segment may not be merged at all. In these cases the merge will be a single
     * segment deletion processing merge.
     *
     * <p>Default: 2 GiB
     *
     * @param maxVectorInputBytes bytes
     * @return this
     */
    public Builder setMaxVectorInputBytes(long maxVectorInputBytes) {
      this.maxVectorInputBytes = maxVectorInputBytes;
      return this;
    }

    /**
     * Bytes budget for all in-flight vector merges.
     *
     * <p>vector_field_sizes.reduce(sum) across all scheduled merges will not exceed this value.
     * This budget is consumed whenever a merge is scheduled and freed when the merge completes.
     * Merges that do not fit in budget are dropped -- they may be re-scheduled in a future run if
     * budget is available.
     *
     * @param mergeBudgetBytes bytes
     * @return this
     */
    public Builder setMergeBudgetBytes(long mergeBudgetBytes) {
      this.mergeBudgetBytes = mergeBudgetBytes;
      return this;
    }

    /**
     * Maximum number of heap bytes allowed for HNSW graph data for a single field in one merge.
     *
     * <p>Merging HNSW graphs requires keeping all input segment graphs in memory at once. This
     * value constrains the maximum per-merge heap usage across segments for a *single* field.
     *
     * <p>Each field is merged sequentially, so the budget applies to the field with the highest
     * heap requirement in the merge. This limit should reflect the available native heap on the
     * host, excluding Java heap overhead.
     *
     * <p>If a merge exceeds this per-merge heap limit, it will still proceed if it is the only way
     * to process a segment (e.g., during deletion merges), but it may be delayed otherwise.
     *
     * <p>Default: 2 GiB
     *
     * @param segmentHeapBytesBudget maximum heap bytes per merge for HNSW data
     * @return this
     */
    public Builder setSegmentHeapBytesBudget(long segmentHeapBytesBudget) {
      this.segmentHeapBytesBudget = segmentHeapBytesBudget;
      return this;
    }

    /**
     * Maximum total heap usage across all in-flight merges.
     *
     * <p>This is a global budget shared by all concurrent merges and constrains the combined heap
     * usage from HNSW data structures across those merges. Each scheduled merge must fit both the
     * global budget and the per-merge {@link #setSegmentHeapBytesBudget} limit.
     *
     * <p>Merges that would exceed the global budget are dropped and retried in later cycles. This
     * prevents overwhelming the native heap with concurrent graph building work.
     *
     * <p>Default: 4 GiB
     *
     * @param globalHeapBytesBudget total heap budget for concurrent HNSW merges
     * @return this
     */
    public Builder setGlobalHeapBytesBudget(long globalHeapBytesBudget) {
      this.globalHeapBytesBudget = globalHeapBytesBudget;
      return this;
    }

    /**
     * Maximum number of neighbors (M) to store for each node in HNSW during memory estimation.
     *
     * <p>This value is used to estimate how much RAM is needed for the HNSW graph during a merge. A
     * higher value assumes denser graphs and increases the estimated heap usage. Lower values
     * reduce the estimate, which may result in more merges being allowed under tight memory
     * budgets.
     *
     * <p>By default, this uses {@link Lucene99HnswVectorsFormat#DEFAULT_MAX_CONN}.
     *
     * @param maxConn the number of neighbors to assume per HNSW level for estimation
     * @return this
     */
    public Builder setMaxConn(int maxConn) {
      this.maxConn = maxConn;
      return this;
    }

    public VectorMergePolicy build(MergePolicy parent, MeterRegistry meterRegistry) {
      return VectorMergePolicy.create(
          parent,
          this.maxCompoundDataBytes,
          this.maxVectorInputBytes,
          this.mergeBudgetBytes,
          this.segmentHeapBytesBudget,
          this.globalHeapBytesBudget,
          this.maxConn,
          meterRegistry);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * Creates a new VectorMergePolicy and registers gauges after construction to avoid this-escape.
   */
  private static VectorMergePolicy create(
      MergePolicy parent,
      long maxCompoundDataSize,
      long maxVectorInputBytes,
      long mergeBudgetBytes,
      long segmentHeapBytesBudget,
      long globalHeapBytesBudget,
      int maxConn,
      MeterRegistry meterRegistry) {
    var metricsFactory =
        new MetricsFactory(
            "vectorMergePolicy", meterRegistry, ServerStatusDataExtractor.Scope.LUCENE.getTag());

    VectorMergePolicy policy =
        new VectorMergePolicy(
            parent,
            maxCompoundDataSize,
            maxVectorInputBytes,
            mergeBudgetBytes,
            segmentHeapBytesBudget,
            globalHeapBytesBudget,
            maxConn,
            metricsFactory);

    // Register gauges after construction is complete to avoid this-escape
    metricsFactory.objectValueGauge(
        "budgetBytesUsed", policy, VectorMergePolicy::getBudgetBytesUsed);
    metricsFactory.objectValueGauge(
        "budgetBytesTotal", policy, VectorMergePolicy::getBudgetBytesTotal);
    metricsFactory.objectValueGauge(
        "budgetBytesHeapUsed", policy, VectorMergePolicy::getBudgetBytesHeapUsed);

    return policy;
  }

  /** Private constructor - does not register gauges. Use {@link #create} to construct instances. */
  private VectorMergePolicy(
      MergePolicy parent,
      long maxCompoundDataSize,
      long maxVectorInputBytes,
      long mergeBudgetBytes,
      long segmentHeapBytesBudget,
      long globalHeapBytesBudget,
      int maxConn,
      MetricsFactory metricsFactory) {
    super(parent);

    this.maxCompoundDataSize = maxCompoundDataSize;
    this.maxVectorInputBytes = maxVectorInputBytes;
    this.budgetBytesTotal = mergeBudgetBytes;
    this.segmentHeapBytesBudget = segmentHeapBytesBudget;
    this.globalHeapBytesBudget = globalHeapBytesBudget;
    this.maxConn = maxConn;

    this.skippedCompoundFile = metricsFactory.counter("skippedCompoundFile");
    this.discardedMerge = metricsFactory.counter("discardedMerge");
    this.prunedSegment = metricsFactory.counter("prunedSegment");
    this.segmentMaxSizeExceeded = metricsFactory.counter("segmentMaxSizeExceeded");
    this.segmentHeapSizeExceeded = metricsFactory.counter("segmentHeapSizeExceeded");
  }

  @Override
  @Nullable
  public MergeSpecification findMerges(
      MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
      throws IOException {
    return maybePruneMergeSpecification(
        super.findMerges(mergeTrigger, segmentInfos, mergeContext), mergeContext);
  }

  @Override
  @Nullable
  public MergeSpecification findFullFlushMerges(
      MergeTrigger mergeTrigger, SegmentInfos segmentInfos, MergeContext mergeContext)
      throws IOException {
    return maybePruneMergeSpecification(
        super.findFullFlushMerges(mergeTrigger, segmentInfos, mergeContext), mergeContext);
  }

  @Override
  @Nullable
  public MergeSpecification findForcedMerges(
      SegmentInfos segmentInfos,
      int maxSegmentCount,
      Map<SegmentCommitInfo, Boolean> segmentsToMerge,
      MergeContext mergeContext)
      throws IOException {
    return maybePruneMergeSpecification(
        super.findForcedMerges(segmentInfos, maxSegmentCount, segmentsToMerge, mergeContext),
        mergeContext);
  }

  @Override
  @Nullable
  public MergeSpecification findForcedDeletesMerges(
      SegmentInfos segmentInfos, MergeContext mergeContext) throws IOException {
    return maybePruneMergeSpecification(
        super.findForcedDeletesMerges(segmentInfos, mergeContext), mergeContext);
  }

  @Override
  public boolean useCompoundFile(
      SegmentInfos infos, SegmentCommitInfo mergedInfo, MergeContext mergeContext)
      throws IOException {
    boolean vectorCompound =
        vectorSizeInBytes(mergedInfo, mergeContext) <= this.maxCompoundDataSize;
    boolean parentCompound = super.useCompoundFile(infos, mergedInfo, mergeContext);
    if (parentCompound && !vectorCompound) {
      this.skippedCompoundFile.increment();
    }
    return parentCompound && vectorCompound;
  }

  /** Prunes the merge specification by removing merges that exceed configured byte limits. */
  @VisibleForTesting
  @Nullable
  public MergeSpecification maybePruneMergeSpecification(
      @Nullable MergeSpecification mergeSpecification, MergeContext context) throws IOException {
    // MergePolicy may return a null spec if it does not wish to perform any merges.
    if (mergeSpecification == null) {
      return null;
    }

    // At this point we have a list of merges each of which has a list of input specs. The input
    // segments have been filtered according to TMP eligibility checks, namely:
    // * None of the input segments are part of an outstanding merge.
    // * Any large segments are only present if they have a large number of deletes.
    // We do not attempt to prune input segments for any merge that does not fit in budget; this
    // may be appropriate future work.
    var it = mergeSpecification.merges.listIterator();
    while (it.hasNext()) {
      Optional<VectorMerge> merge = wrapMerge(it.next(), context);
      if (merge.isPresent() && acquireBudget(merge.get())) {
        this.prunedSegment.increment(merge.get().prunedSegments);
        it.set(merge.get());
      } else {
        this.discardedMerge.increment();
        it.remove();
      }
    }
    return mergeSpecification.merges.isEmpty() ? null : mergeSpecification;
  }

  private synchronized boolean acquireBudget(VectorMerge merge) {
    if ((this.budgetBytesUsed + merge.vectorByteSize <= this.budgetBytesTotal)
        && (this.budgetBytesHeapUsed + merge.segmentHeapByteSize <= this.globalHeapBytesBudget)) {
      this.budgetBytesUsed += merge.vectorByteSize;
      this.budgetBytesHeapUsed += merge.segmentHeapByteSize;
      return true;
    } else {
      return false;
    }
  }

  private synchronized void releaseBudget(VectorMerge merge) {
    this.budgetBytesUsed -= merge.vectorByteSize;
    checkState(
        this.budgetBytesUsed >= 0,
        "Releasing bytes after merge yields more bytes than in the budget (%d vs %d)",
        this.budgetBytesUsed,
        this.budgetBytesTotal);
    this.budgetBytesHeapUsed -= merge.segmentHeapByteSize;
    checkState(
        this.budgetBytesHeapUsed >= 0,
        "Releasing heap bytes after merge yields more bytes than in the budget (%d vs %d)",
        this.budgetBytesHeapUsed,
        this.globalHeapBytesBudget);
  }

  synchronized long getBudgetBytesUsed() {
    return this.budgetBytesUsed;
  }

  synchronized long getBudgetBytesHeapUsed() {
    return this.budgetBytesHeapUsed;
  }

  long getBudgetBytesTotal() {
    return this.budgetBytesTotal;
  }

  /**
   * Compute vector data size for the segment in question.
   *
   * <p>This returns the byte size of the largest vector data file in the segment adjusted by the
   * number of deleted docs. We use the largest vector data file in the segment because fields are
   * merged one at a time and the default vector format produces a single vector data file for each
   * field; we prorate by deleted documents because the data file is merged before the random read
   * workload (index building) occurs. This value may be pessimistic if multiple fields are merged
   * into a single vector data file since only one field is merged at a time, and summing across
   * segments may be incorrect if the sizes of different vector fields are very uneven across
   * segments.
   *
   * <p>NB: this will return 0 for all compound segments which do not have vector data stored in
   * separate files.
   *
   * <p>NB 2: If quantized vector data files are available, we use them; otherwise, we default to
   * raw data files.
   *
   * @param info the segment in question
   * @return vector byte size.
   * @throws IOException if computing file size failed.
   */
  private long vectorSizeInBytes(SegmentCommitInfo info, MergeContext context) throws IOException {
    // Use numDeletesToMerge() to obey account for soft deletes policy, if any.
    int deleteCount = context.numDeletesToMerge(info);
    // assertDelCount() actually contains assertions so the Check wrapper may not matter.
    Check.checkState(assertDelCount(deleteCount, info), "deleteCount not in range");
    double liveRatio =
        1.0 - (info.info.maxDoc() <= 0 ? 0.0 : (double) deleteCount / info.info.maxDoc());

    @Var long rawSize = 0;
    @Var long quantizedSize = 0;
    for (String filename : info.files()) {
      if (IndexFileNames.matchesExtension(filename, VECTOR_DATA_EXTENSION)) {
        rawSize = Math.max(rawSize, (long) (info.info.dir.fileLength(filename) * liveRatio));
      } else if (IndexFileNames.matchesExtension(filename, QUANTIZED_VECTOR_DATA_EXTENSION)) {
        quantizedSize =
            Math.max(quantizedSize, (long) (info.info.dir.fileLength(filename) * liveRatio));
      }
    }
    return quantizedSize > 0 ? quantizedSize : rawSize;
  }

  /**
   * Estimates RAM usage in bytes for the on-heap HNSW graph, closely matching Lucene's
   * OnHeapHnswGraph.ramBytesUsed(). It estimates the upper bound of memory (in bytes) needed to
   * store the HNSW graph in heap memory. This function assumes the worst-case scenario for each
   * parameter, calculating memory usage based on the size of the neighbor arrays, node-level
   * information, object overheads, and other HNSW-specific data structures.
   *
   * <p>Note that this is an upper bound estimation and does not account for optimizations that
   * could reduce memory overhead, such as sharing structures or reusing allocated memory. The upper
   * bound estimation is useful for understanding the potential memory usage and ensuring that
   * enough memory is allocated for the HNSW graph during its creation or maintenance.
   *
   * <p>Breakdown: - Level 0 neighbor lists: each node has a list of up to 2 * M neighbors, stored
   * as two parallel arrays (int[] and float[]), each with an array header. - Higher-level neighbor
   * lists: for nodes above level 0, each level has up to M neighbors. As the levels increase, each
   * becomes increasingly sparse — we account for this by applying a sparsity scaling factor to
   * reduce overestimation. - Other overhead includes AtomicInteger fields, object references, array
   * headers, and per-node metadata for non-zero levels.
   */
  private long ramHnswBytesNeeded(SegmentCommitInfo info) {
    long totalVectors = info.info.maxDoc();
    int bytesPerNeighbor = Integer.BYTES + Float.BYTES;
    int neighborArrayHeaderBytes = 2 * RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;
    int neighborListObjectOverhead = 2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF + 3 * Integer.BYTES;

    // RAM usage for level 0 neighbors: 2 * M neighbors, each with int[] and float[].
    long neighborArrayBytesLevel0 =
        2L * this.maxConn * bytesPerNeighbor
            + neighborArrayHeaderBytes
            + neighborListObjectOverhead;

    // RAM usage for higher level neighbors: M neighbors per level, each with int[] and float[].
    long neighborArrayBytesUpperLevels =
        (long) this.maxConn * bytesPerNeighbor
            + neighborArrayHeaderBytes
            + neighborListObjectOverhead;

    @Var long total = 0;

    // For level 0: Each vector has its own set of neighbors.
    total += totalVectors * (neighborArrayBytesLevel0 + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER);
    total += RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;

    // Estimate number of upper levels with exponential decay: E[levels] = log(totalVectors)
    int expectedLevels = (int) Math.ceil(Math.log(totalVectors) / Math.log(1 / 0.5));

    // Higher levels are increasingly sparse, so scale the memory estimate down.
    // Geometric series sum (1 + 1/2 + 1/4 + ...) ≈ 2
    double sparsityFactor = 2.0;
    total +=
        (long) (totalVectors * expectedLevels * neighborArrayBytesUpperLevels / sparsityFactor);

    // Primitive and object overheads.
    // All int fields (e.g., size, level count).
    total += 4 * Integer.BYTES;
    // Field: noGrowth (overhead for this field).
    total += 1;
    // Field: entryNode (reference and integers).
    total +=
        RamUsageEstimator.NUM_BYTES_OBJECT_REF
            + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
            + 2 * Integer.BYTES;
    // 3 AtomicIntegers (reference + integer per AtomicInteger)
    total += 3L * (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + Integer.BYTES);
    // Field: cur (reference)
    total += RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    // Field: levelToNodes (array header)
    total += RamUsageEstimator.NUM_BYTES_ARRAY_HEADER;

    if (totalVectors > 0) {
      // Object reference overhead for nodes in each level.
      total += (totalVectors - 1) * RamUsageEstimator.NUM_BYTES_OBJECT_REF;
      // Extra space for each node in the graph.
      total +=
          totalVectors
              * (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                  + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                  + Integer.BYTES);
    }

    return total;
  }

  private static class VectorMerge extends OneMerge {
    public final long vectorByteSize;
    public final long segmentHeapByteSize;
    public final int prunedSegments;
    public final Consumer<VectorMerge> mergeFinished;

    VectorMerge(
        List<SegmentCommitInfo> segments,
        long vectorByteSize,
        long segmentHeapByteSize,
        int prunedSegments,
        Consumer<VectorMerge> mergeFinished) {
      super(segments);
      this.vectorByteSize = vectorByteSize;
      this.segmentHeapByteSize = segmentHeapByteSize;
      this.prunedSegments = prunedSegments;
      this.mergeFinished = mergeFinished;
    }

    @Override
    public void mergeFinished(boolean success, boolean segmentDropped) throws IOException {
      super.mergeFinished(success, segmentDropped);
      this.mergeFinished.accept(this);
    }
  }

  private static class SegmentVectorSize {
    public final SegmentCommitInfo info;
    public final long vectorByteSize;
    public final long segmentHeapByteSize;

    public SegmentVectorSize(SegmentCommitInfo info, long vectorByteSize, long segmentHeapByteSize)
        throws IOException {
      this.info = info;
      this.vectorByteSize = vectorByteSize;
      this.segmentHeapByteSize = segmentHeapByteSize;
    }

    @Override
    public String toString() {
      return "SegmentVectorSize[info="
          + this.info.toString()
          + " vectorByteSize="
          + this.vectorByteSize
          + " segmentHeapByteSize="
          + this.segmentHeapByteSize
          + "]";
    }
  }

  /**
   * Wrap the input merge into a VectorMerge that acquires and releases process-wide memory budget
   * for vector merges. This method may also prune segments out of the merge to get it to fit within
   * the maximum allotted budget.
   *
   * @param merge input merge generated by the underlying policy.
   * @param context context used to compute additional information about the segments.
   * @return a merge to run or empty() if the merge is pruned out completely.
   * @throws IOException from any underlying IO.
   */
  private Optional<VectorMerge> wrapMerge(OneMerge merge, MergeContext context) throws IOException {
    List<SegmentVectorSize> sizedSegments = computeSizedSegments(merge, context);
    long totalVectorBytes = sizedSegments.stream().mapToLong(s -> s.vectorByteSize).sum();
    long totalSegmentHeapBytes = sizedSegments.stream().mapToLong(s -> s.segmentHeapByteSize).sum();
    // If the total vector size of the merge is less than maxVectorInputBytes and the total
    // needed heap size of the merge is less than segmentHeapBytesBudget, exit early.
    if (isWithinBudget(totalVectorBytes, totalSegmentHeapBytes)) {
      return Optional.of(
          createVectorMerge(merge.segments, totalVectorBytes, totalSegmentHeapBytes, 0));
    }

    // Special handling for segments whose vectorByteSize exceeds maxVectorInputBytes.
    Optional<VectorMerge> specialVectorMerge = tryOversizeVectorSegmentMerge(sizedSegments, merge);
    if (specialVectorMerge.isPresent()) {
      return specialVectorMerge;
    }

    // Special handling for segments whose HNSWBytesSize exceeds segmentHeapBytesBudget.
    Optional<VectorMerge> specialHeapMerge = tryOversizeHeapSegmentMerge(sizedSegments, merge);
    if (specialHeapMerge.isPresent()) {
      return specialHeapMerge;
    }

    // Accumulate any segments under the size threshold.
    // When reaching here, it means no early termination due to (i) small sizes,
    // or (ii) large size containing deletions
    return tryGreedyMerge(sizedSegments, merge);
  }

  private List<SegmentVectorSize> computeSizedSegments(OneMerge merge, MergeContext context)
      throws IOException {
    List<SegmentVectorSize> sizedSegments = new ArrayList<>(merge.segments.size());
    for (var segment : merge.segments) {
      sizedSegments.add(
          new SegmentVectorSize(
              segment, vectorSizeInBytes(segment, context), ramHnswBytesNeeded(segment)));
    }
    return sizedSegments;
  }

  private boolean isWithinBudget(long vectorBytes, long heapBytes) {
    return vectorBytes <= this.maxVectorInputBytes && heapBytes <= this.segmentHeapBytesBudget;
  }

  private VectorMerge createVectorMerge(
      List<SegmentCommitInfo> segments, long vectorBytes, long heapBytes, int droppedCount) {
    return new VectorMerge(
        segments, vectorBytes, heapBytes, droppedCount, VectorMergePolicy.this::releaseBudget);
  }

  private Optional<VectorMerge> tryOversizeVectorSegmentMerge(
      List<SegmentVectorSize> sizedSegments, OneMerge merge) {
    var oversize =
        sizedSegments.stream()
            .filter(s -> s.vectorByteSize >= this.maxVectorInputBytes)
            .sorted(
                Comparator.comparingLong((SegmentVectorSize s) -> -s.vectorByteSize)
                    .thenComparing(s -> s.info.info.name))
            .toList();

    Optional<SegmentVectorSize> candidate =
        oversize.stream().filter(s -> s.info.getDelCount() > 0).findFirst();

    if (candidate.isPresent()) {
      this.segmentMaxSizeExceeded.increment();
      return Optional.of(
          createVectorMerge(
              List.of(candidate.get().info),
              candidate.get().vectorByteSize,
              candidate.get().segmentHeapByteSize,
              merge.segments.size() - 1));
    }
    this.segmentMaxSizeExceeded.increment(oversize.size());
    return Optional.empty();
  }

  private Optional<VectorMerge> tryOversizeHeapSegmentMerge(
      List<SegmentVectorSize> sizedSegments, OneMerge merge) {
    var oversize =
        sizedSegments.stream()
            .filter(s -> s.segmentHeapByteSize >= this.segmentHeapBytesBudget)
            .sorted(
                Comparator.comparingLong((SegmentVectorSize s) -> -s.segmentHeapByteSize)
                    .thenComparing(s -> s.info.info.name))
            .toList();

    Optional<SegmentVectorSize> candidate =
        oversize.stream().filter(s -> s.info.getDelCount() > 0).findFirst();

    if (candidate.isPresent()) {
      this.segmentHeapSizeExceeded.increment();
      return Optional.of(
          createVectorMerge(
              List.of(candidate.get().info),
              candidate.get().vectorByteSize,
              candidate.get().segmentHeapByteSize,
              merge.segments.size() - 1));
    }
    this.segmentHeapSizeExceeded.increment(oversize.size());
    return Optional.empty();
  }

  private Optional<VectorMerge> tryGreedyMerge(
      List<SegmentVectorSize> sizedSegments, OneMerge merge) {
    List<SegmentCommitInfo> segments = new ArrayList<>();
    @Var long totalVectorBytes = 0;
    @Var long totalSegmentHeapBytes = 0;

    for (SegmentVectorSize segment : sizedSegments) {
      if (segment.vectorByteSize < this.maxVectorInputBytes
          && segment.segmentHeapByteSize < this.segmentHeapBytesBudget) {
        long newVectorBytes = totalVectorBytes + segment.vectorByteSize;
        long newHeapBytes = totalSegmentHeapBytes + segment.segmentHeapByteSize;

        if (newVectorBytes <= this.maxVectorInputBytes
            && newHeapBytes <= this.segmentHeapBytesBudget) {
          totalVectorBytes = newVectorBytes;
          totalSegmentHeapBytes = newHeapBytes;
          segments.add(segment.info);
        }
      }
    }

    if (segments.isEmpty() || (segments.size() == 1 && segments.get(0).getDelCount() == 0)) {
      return Optional.empty();
    }

    return Optional.of(
        createVectorMerge(
            segments,
            totalVectorBytes,
            totalSegmentHeapBytes,
            merge.segments.size() - segments.size()));
  }
}
