package com.xgen.mongot.index.lucene;

import static com.xgen.mongot.index.lucene.Comparators.RELEVANCE_COMPARATOR;
import static com.xgen.mongot.util.Check.checkState;

import com.google.errorprone.annotations.Var;
import com.xgen.mongot.cursor.batch.BatchCursorOptions;
import com.xgen.mongot.index.BatchProducer;
import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.mongot.util.Bytes;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.BsonArrayBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import org.apache.lucene.search.SortField;
import org.bson.BsonArray;

/**
 * A batch producer that takes in multiple LuceneSearchBatchProducers, each from one index
 * partition, and merges their returned batches into one single batch based on merging sorted list
 * (sorted by relevance score or custom sort fields`). This class handles search results only, not
 * metadata results.
 */
public class SearchMergingBatchProducer implements BatchProducer {
  private final List<LuceneSearchBatchProducer> batchProducers;
  private Optional<MergedSearchResultIter> mergedSearchResultIter;

  public SearchMergingBatchProducer(List<LuceneSearchBatchProducer> batchProducers) {
    Check.argNotEmpty(batchProducers, "batchProducers");
    this.batchProducers = batchProducers;
    this.mergedSearchResultIter = Optional.empty();
  }

  @Override
  public void execute(Bytes sizeLimit, BatchCursorOptions queryCursorOptions) throws IOException {
    // Overall design: We keep a SearchResultsIter open for every underlying batchProducer, and
    // advance to next batch when any SearchResultsIter is exhausted. We use the same sizeLimit and
    // queryCursorOptions for every getting next batch from underlying batchProducer. This design
    // has the benefit of re-using left-over docs from previous calls of this method, which is
    // especially helpful when mongod wants to exhaust this cursor. However, this design has the
    // downside of keeping more things in memory (basically one SearchResultsIter per index
    // partition is
    // live, through the entire cursor lifetime). In the case of long dangling cursor, this will
    // waste memory. But the situation of long dangling cursor is rare. This class wrapping a single
    // underlying batchProducer will produce the same output, but will have different memory
    // footprints.
    if (this.mergedSearchResultIter.isEmpty()) {
      List<LuceneSearchBatchProducer.SearchResultsIter> iters = new ArrayList<>();
      for (int i = 0; i < this.batchProducers.size(); i++) {
        try (var indexPartitionResourceManager =
            Explain.maybeEnterIndexPartitionQueryContext(
                this.batchProducers.get(i).getIndexPartitionId())) {
          this.batchProducers.get(i).execute(sizeLimit, queryCursorOptions);
          iters.add(this.batchProducers.get(i).getSearchResultsIter());
        }
      }
      this.mergedSearchResultIter =
          Optional.of(new MergedSearchResultIter(iters, queryCursorOptions));
    }
  }

  @Override
  public BsonArray getNextBatch(Bytes sizeLimit) throws IOException {
    // This line must be after the batchProducer.getNextBatchAsIterator, since the
    // getNextBatchAsIterator may refresh the batchProducer.batchSizeStrategy. The
    // ExponentiallyIncreasingBatchSizeStrategy is stateful and will complicate the case here.
    // But do we need to worry about it since it's an old strategy.
    int batchSize = this.batchProducers.get(0).getBatchSizeStrategy().adviseNextBatchSize();
    var builder = BsonArrayBuilder.withLimit(sizeLimit);
    // TODO(CLOUDP-280897): Do we want to add the avgDocSize based capping logic here?
    for (int i = 0; i < batchSize; i++) {
      Optional<LuceneSearchBatchProducer.IterValue> iterValue =
          this.mergedSearchResultIter.get().peek(sizeLimit);
      if (iterValue.isEmpty()) {
        // All iters are exhausted.
        break;
      }
      if (builder.append(iterValue.get().bsonDoc())) {
        // Calling advance() here, instead of in getBestScoreIter(), because we only advance()
        // when the value is successfully consumed (can fit in the BsonArrayBuilder). Only when
        // successfully consumed, the LuceneSearchBatchProducer.lastScoreDoc is updated.
        this.mergedSearchResultIter.get().acceptAndAdvance();
      } else {
        break;
      }
    }
    return builder.build();
  }

  /**
   * This iterator can iterate through multiple SearchResultsIters from high to low scores. Typical
   * usage: while (iter.peek().isPresent()) { // Do something with the iter value.
   * iter.acceptAndAdvance(); } The peek() and acceptAndAdvance() must be called alternatively.
   */
  public static class MergedSearchResultIter {
    private final PriorityQueue<LuceneSearchBatchProducer.SearchResultsIter> priorityQueue;
    private final BatchCursorOptions queryCursorOptions;

    // If present, the peak() should use getNextBatchBeginningIterator() and put this result into
    // the priorityQueue.
    private Optional<LuceneSearchBatchProducer> pendingProducer;

    public MergedSearchResultIter(
        List<LuceneSearchBatchProducer.SearchResultsIter> iters,
        BatchCursorOptions queryCursorOptions) {
      Check.argNotEmpty(iters, "List<SearchResultsIter>");
      this.pendingProducer = Optional.empty();
      this.queryCursorOptions = queryCursorOptions;
      Optional<SortField[]> sortedFields = iters.get(0).sortFields();
      Comparator<LuceneSearchBatchProducer.SearchResultsIter> comparator =
          Comparator.comparing(
              // Only iterator with peek().isPresent() == true are inserted to the heap.
              (LuceneSearchBatchProducer.SearchResultsIter itr) -> itr.peek().get(),
              sortedFields.isPresent()
                  ? new Comparators.CustomSortComparator(sortedFields.get())
                  : RELEVANCE_COMPARATOR);

      this.priorityQueue = new PriorityQueue<>(comparator);
      for (var iter : iters) {
        if (iter.peek().isPresent()) {
          this.priorityQueue.add(iter);
        }
      }
    }

    /**
     * Returns empty if this class is exhausted. NOTE: This method may fetch results from Lucene.
     * This is intentional since we want to fetch from Lucene as late as possible to avoid the
     * performance hit
     */
    public Optional<LuceneSearchBatchProducer.IterValue> peek(Bytes sizeLimit) throws IOException {
      if (this.pendingProducer.isPresent()) {
        checkState(
            !this.pendingProducer.get().isExhausted(),
            "this.pendingProducer.get().isExhausted() must be false.");
        try (var indexPartitionResourceManager =
            Explain.maybeEnterIndexPartitionQueryContext(
                this.pendingProducer.get().getIndexPartitionId())) {
          this.pendingProducer.get().execute(sizeLimit, this.queryCursorOptions);
          LuceneSearchBatchProducer.SearchResultsIter newBatchIter =
              this.pendingProducer.get().getSearchResultsIter();
          this.pendingProducer = Optional.empty();
          if (newBatchIter.peek().isPresent()) {
            this.priorityQueue.add(newBatchIter);
          }
        }
      }
      if (this.priorityQueue.isEmpty()) {
        return Optional.empty();
      }
      return this.priorityQueue.peek().peek();
    }

    /** If true, peek() will return empty. */
    public boolean isExhausted() {
      if (this.pendingProducer.isPresent()) {
        checkState(
            !this.pendingProducer.get().isExhausted(),
            "this.pendingProducer.get().isExhausted() must be false.");
      }
      return this.pendingProducer.isEmpty() && this.priorityQueue.isEmpty();
    }

    /**
     * Precondition: this.peek().isPresent() must be true. This method will not fetch results from
     * Lucene.
     */
    public void acceptAndAdvance() throws IOException {
      // The peek() must have been called prior to this call, so the the pendingProducer is cleared.
      checkState(this.pendingProducer.isEmpty(), "pendingProducer should be empty.");
      if (this.priorityQueue.isEmpty()) {
        throw new IllegalStateException("The iterator has been exhausted.");
      }
      LuceneSearchBatchProducer.SearchResultsIter iter = this.priorityQueue.remove();
      iter.acceptAndAdvance();
      if (iter.peek().isPresent()) {
        this.priorityQueue.add(iter);
      } else if (!iter.parentBatchProducer().isExhausted()) {
        this.pendingProducer = Optional.of(iter.parentBatchProducer());
      }
    }
  }

  @Override
  public boolean isExhausted() {
    if (this.mergedSearchResultIter.isEmpty()) {
      return false;
    }
    return this.mergedSearchResultIter.get().isExhausted();
  }

  @Override
  public void close() throws IOException {
    @Var Optional<IOException> first = Optional.empty();
    for (var batchProducer : this.batchProducers) {
      try {
        batchProducer.close();
      } catch (IOException e) {
        if (first.isEmpty()) {
          first = Optional.of(e);
        } else {
          first.get().addSuppressed(e);
        }
      }
    }
    if (first.isPresent()) {
      throw first.get();
    }
  }
}
