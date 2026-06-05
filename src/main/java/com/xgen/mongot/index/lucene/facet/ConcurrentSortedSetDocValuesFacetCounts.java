package com.xgen.mongot.index.lucene.facet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;
import org.apache.lucene.facet.FacetUtils;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiDocValues.MultiSortedSetDocValues;
import org.apache.lucene.index.OrdinalMap;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LongValues;
import org.jetbrains.annotations.Nullable;

/**
 * Like {@link com.xgen.mongot.index.lucene.facet.SortedSetDocValuesFacetCounts}, but aggregates
 * counts concurrently across segments.
 *
 * <p>NOTE(guanlinzhou): This file copied from Lucene (version 9.11): {@link
 * ConcurrentSortedSetDocValuesFacetCounts}. Its base class has modifications required to facet over
 * `token` fields.
 *
 * @lucene.experimental
 */
public class ConcurrentSortedSetDocValuesFacetCounts extends AbstractSortedSetDocValuesFacetCounts {

  final ExecutorService exec;
  final AtomicIntegerArray counts;

  /**
   * Returns all facet counts, same result as searching on {@link MatchAllDocsQuery} but faster.
   * Counts all facet dimensions across the provided hits.
   */
  public ConcurrentSortedSetDocValuesFacetCounts(
      SortedSetDocValuesReaderState state, FacetsCollector hits, ExecutorService exec)
      throws IOException, InterruptedException {
    super(state);
    this.exec = exec;
    this.counts = new AtomicIntegerArray(state.getSize());
    if (hits == null) {
      // browse only
      countAll();
    } else {
      count(hits.getMatchingDocs());
    }
  }

  @Override
  boolean hasCounts() {
    return true;
  }

  @Override
  int getCount(int ord) {
    return this.counts.get(ord);
  }

  @Override
  PrimitiveIterator.OfInt getOrdIterator() {
    return IntStream.range(0, this.counts.length()).iterator();
  }

  private class CountOneSegment implements Callable<Void> {
    final LeafReader leafReader;
    @Nullable final MatchingDocs hits;
    @Nullable final OrdinalMap ordinalMap;
    final int segOrd;

    public CountOneSegment(
        LeafReader leafReader,
        @Nullable MatchingDocs hits,
        @Nullable OrdinalMap ordinalMap,
        int segOrd) {
      assert leafReader != null;
      this.leafReader = leafReader;
      this.hits = hits;
      this.ordinalMap = ordinalMap;
      this.segOrd = segOrd;
    }

    @Override
    public Void call() throws IOException {
      // If we're counting collected hits but there were none, short-circuit:
      if (this.hits != null && this.hits.totalHits() == 0) {
        return null;
      }

      SortedSetDocValues multiValues =
          DocValues.getSortedSet(
              this.leafReader, ConcurrentSortedSetDocValuesFacetCounts.this.field);
      if (multiValues == null) {
        // nothing to count here
        return null;
      }

      // It's slightly more efficient to work against SortedDocValues if the field is actually
      // single-valued (see: LUCENE-5309)
      SortedDocValues singleValues = DocValues.unwrapSingleton(multiValues);
      DocIdSetIterator valuesIt = singleValues != null ? singleValues : multiValues;

      DocIdSetIterator it;
      if (this.hits == null) {
        Bits liveDocs = this.leafReader.getLiveDocs();
        it = (liveDocs != null) ? FacetUtils.liveDocsDISI(valuesIt, liveDocs) : valuesIt;
      } else {
        it =
            ConjunctionUtils.intersectIterators(
                Arrays.asList(this.hits.bits().iterator(), valuesIt));
      }

      if (this.ordinalMap != null) {
        LongValues ordMap = this.ordinalMap.getGlobalOrds(this.segOrd);

        int numSegOrds = (int) multiValues.getValueCount();

        if (this.hits != null && this.hits.totalHits() < numSegOrds / 10) {
          // Remap every ord to global ord as we iterate:
          if (singleValues != null) {
            if (singleValues == it) {
              for (int doc = singleValues.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = singleValues.nextDoc()) {
                ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                    (int) ordMap.get(singleValues.ordValue()));
              }
            } else {
              for (int doc = it.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = it.nextDoc()) {
                ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                    (int) ordMap.get(singleValues.ordValue()));
              }
            }
          } else {
            if (multiValues == it) {
              for (int doc = multiValues.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = multiValues.nextDoc()) {
                for (int i = 0; i < multiValues.docValueCount(); i++) {
                  int term = (int) multiValues.nextOrd();
                  ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                      (int) ordMap.get(term));
                }
              }
            } else {
              for (int doc = it.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = it.nextDoc()) {
                for (int i = 0; i < multiValues.docValueCount(); i++) {
                  int term = (int) multiValues.nextOrd();
                  ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                      (int) ordMap.get(term));
                }
              }
            }
          }
        } else {

          // First count in seg-ord space:
          int[] segCounts = new int[numSegOrds];
          if (singleValues != null) {
            if (singleValues == it) {
              for (int doc = singleValues.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = singleValues.nextDoc()) {
                segCounts[singleValues.ordValue()]++;
              }
            } else {
              for (int doc = it.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = it.nextDoc()) {
                segCounts[singleValues.ordValue()]++;
              }
            }
          } else {
            if (multiValues == it) {
              for (int doc = multiValues.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = multiValues.nextDoc()) {
                for (int i = 0; i < multiValues.docValueCount(); i++) {
                  int term = (int) multiValues.nextOrd();
                  segCounts[term]++;
                }
              }
            } else {
              for (int doc = it.nextDoc();
                  doc != DocIdSetIterator.NO_MORE_DOCS;
                  doc = it.nextDoc()) {
                for (int i = 0; i < multiValues.docValueCount(); i++) {
                  int term = (int) multiValues.nextOrd();
                  segCounts[term]++;
                }
              }
            }
          }

          // Then, migrate to global ords:
          for (int ord = 0; ord < numSegOrds; ord++) {
            int count = segCounts[ord];
            if (count != 0) {
              ConcurrentSortedSetDocValuesFacetCounts.this.counts.addAndGet(
                  (int) ordMap.get(ord), count);
            }
          }
        }
      } else {
        // No ord mapping (e.g., single segment index):
        // just aggregate directly into counts:
        if (singleValues != null) {
          if (singleValues == it) {
            for (int doc = singleValues.nextDoc();
                doc != DocIdSetIterator.NO_MORE_DOCS;
                doc = singleValues.nextDoc()) {
              ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                  singleValues.ordValue());
            }
          } else {
            for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
              ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(
                  singleValues.ordValue());
            }
          }
        } else {
          if (multiValues == it) {
            for (int doc = multiValues.nextDoc();
                doc != DocIdSetIterator.NO_MORE_DOCS;
                doc = multiValues.nextDoc()) {
              for (int i = 0; i < multiValues.docValueCount(); i++) {
                int term = (int) multiValues.nextOrd();
                ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(term);
              }
            }
          } else {
            for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
              for (int i = 0; i < multiValues.docValueCount(); i++) {
                int term = (int) multiValues.nextOrd();
                ConcurrentSortedSetDocValuesFacetCounts.this.counts.incrementAndGet(term);
              }
            }
          }
        }
      }

      return null;
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private void count(List<MatchingDocs> matchingDocs) throws IOException, InterruptedException {
    OrdinalMap ordinalMap;

    if (this.dv instanceof MultiDocValues.MultiSortedSetDocValues && matchingDocs.size() > 1) {
      ordinalMap = ((MultiSortedSetDocValues) this.dv).mapping;
    } else {
      ordinalMap = null;
    }

    IndexReader reader = this.state.getReader();
    List<Future<Void>> results = new ArrayList<>();

    for (MatchingDocs hits : matchingDocs) {
      // LUCENE-5090: make sure the provided reader context "matches"
      // the top-level reader passed to the
      // SortedSetDocValuesReaderState, else cryptic
      // AIOOBE can happen:
      if (ReaderUtil.getTopLevelContext(hits.context()).reader() != reader) {
        throw new IllegalStateException(
            "the TokenSsdvFacetState provided to this class does not "
                + "match the reader being searched; you must create a new TokenSsdvFacetState "
                + "every time you open a new IndexReader");
      }

      results.add(
          this.exec.submit(
              new CountOneSegment(hits.context().reader(), hits, ordinalMap, hits.context().ord)));
    }

    for (Future<Void> result : results) {
      try {
        result.get();
      } catch (ExecutionException ee) {
        // Theoretically cause can be null; guard against that.
        Throwable cause = ee.getCause();
        throw IOUtils.rethrowAlways(cause != null ? cause : ee);
      }
    }
  }

  /** Does all the "real work" of tallying up the counts. */
  private void countAll() throws IOException, InterruptedException {
    OrdinalMap ordinalMap;

    if (this.dv instanceof MultiDocValues.MultiSortedSetDocValues) {
      ordinalMap = ((MultiSortedSetDocValues) this.dv).mapping;
    } else {
      ordinalMap = null;
    }

    List<Future<Void>> results = new ArrayList<>();

    for (LeafReaderContext context : this.state.getReader().leaves()) {
      results.add(
          this.exec.submit(new CountOneSegment(context.reader(), null, ordinalMap, context.ord)));
    }

    for (Future<Void> result : results) {
      try {
        result.get();
      } catch (ExecutionException ee) {
        // Theoretically cause can be null; guard against that.
        Throwable cause = ee.getCause();
        throw IOUtils.rethrowAlways(cause != null ? cause : ee);
      }
    }
  }
}
