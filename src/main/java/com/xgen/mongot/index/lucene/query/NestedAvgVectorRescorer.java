package com.xgen.mongot.index.lucene.query;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.index.lucene.query.util.WrappedToParentBlockJoinQuery;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.util.BitSet;

/**
 * Rescores parent documents in a nested vector search by computing the true average similarity
 * across ALL child vectors, instead of only the KNN-selected subset.
 *
 * <p>When ANN is used with {@code scoreMode: "avg"} for nested vectors, the KNN child query only
 * matches the top-{@code numCandidates} child vectors globally. Lucene's {@code
 * ToParentBlockJoinQuery} with {@code ScoreMode.Avg} then averages over this biased subset, which
 * inflates parent scores (fewer, higher-scoring children contribute). This rescorer corrects the
 * scores by exhaustively scoring all child vectors for each candidate parent document.
 */
public class NestedAvgVectorRescorer {

  /**
   * Rescores the given TopDocs by computing true average similarity over all child vectors for each
   * parent document.
   *
   * @param searcher the index searcher
   * @param topDocs the initial TopDocs from the block join query (parent doc IDs with biased avg
   *     scores)
   * @param luceneQuery the top-level Lucene query (a {@link WrappedToParentBlockJoinQuery} or a
   *     {@link BooleanQuery} wrapping one)
   * @param limit the maximum number of parent documents to return after rescoring
   * @return rescored TopDocs with corrected average scores, truncated to limit
   */
  public TopDocs rescore(IndexSearcher searcher, TopDocs topDocs, Query luceneQuery, int limit)
      throws IOException {
    if (topDocs.scoreDocs.length == 0) {
      return topDocs;
    }

    Optional<WrappedToParentBlockJoinQuery> blockJoinQuery = extractBlockJoinQuery(luceneQuery);
    if (blockJoinQuery.isEmpty()) {
      return topDocs;
    }

    Query childQuery = blockJoinQuery.get().getChildQuery();
    if (!(childQuery instanceof KnnFloatVectorQuery kfvq)) {
      return topDocs;
    }

    String fieldName = kfvq.getField();
    float[] queryVector = kfvq.getTargetCopy();
    BitSetProducer parentBitSetProducer = blockJoinQuery.get().getParentsFilter();

    @Var ScoreDoc[] scoreDocs = deepCopy(topDocs.scoreDocs);
    Arrays.sort(scoreDocs, Comparator.comparingInt(sd -> sd.doc));

    List<LeafReaderContext> segments = searcher.getIndexReader().leaves();

    @Var int i = 0;
    while (i < scoreDocs.length) {
      LeafReaderContext segment = segments.get(ReaderUtil.subIndex(scoreDocs[i].doc, segments));
      int maxDoc = segment.docBase + segment.reader().maxDoc();

      int start = i;
      for (i = i + 1; i < scoreDocs.length; i++) {
        if (scoreDocs[i].doc >= maxDoc) {
          break;
        }
      }
      int end = i;

      rescoreSegment(segment, scoreDocs, start, end, fieldName, queryVector, parentBitSetProducer);
    }

    Arrays.sort(scoreDocs, (a, b) -> -Float.compare(a.score, b.score));

    if (scoreDocs.length > limit) {
      scoreDocs = Arrays.copyOf(scoreDocs, limit);
    }

    return new TopDocs(new TotalHits(scoreDocs.length, TotalHits.Relation.EQUAL_TO), scoreDocs);
  }

  private static void rescoreSegment(
      LeafReaderContext segment,
      ScoreDoc[] scoreDocs,
      int start,
      int end,
      String fieldName,
      float[] queryVector,
      BitSetProducer parentBitSetProducer)
      throws IOException {

    FieldInfo fieldInfo = segment.reader().getFieldInfos().fieldInfo(fieldName);
    if (fieldInfo == null) {
      return;
    }

    VectorSimilarityFunction similarityFunction = fieldInfo.getVectorSimilarityFunction();
    BitSet parentBitSet = parentBitSetProducer.getBitSet(segment);
    if (parentBitSet == null) {
      return;
    }

    for (int i = start; i < end; i++) {
      int localParentDoc = scoreDocs[i].doc - segment.docBase;
      int prevParent = (localParentDoc > 0) ? parentBitSet.prevSetBit(localParentDoc - 1) : -1;
      int firstChild = prevParent + 1;

      if (firstChild >= localParentDoc) {
        continue;
      }

      FloatVectorValues childVectors = segment.reader().getFloatVectorValues(fieldName);
      if (childVectors == null) {
        continue;
      }

      KnnVectorValues.DocIndexIterator vectorIterator = childVectors.iterator();
      @Var float scoreSum = 0;
      @Var int childCount = 0;
      @Var int docId = firstChild;

      while (docId < localParentDoc) {
        int found = vectorIterator.advance(docId);
        if (found == NO_MORE_DOCS || found >= localParentDoc) {
          break;
        }
        scoreSum +=
            similarityFunction.compare(
                queryVector, childVectors.vectorValue(vectorIterator.index()));
        childCount++;
        docId = found + 1;
      }

      if (childCount > 0) {
        scoreDocs[i].score = scoreSum / childCount;
      }
    }
  }

  @VisibleForTesting
  static Optional<WrappedToParentBlockJoinQuery> extractBlockJoinQuery(Query query) {
    if (query instanceof WrappedToParentBlockJoinQuery bjq) {
      return Optional.of(bjq);
    }
    if (query instanceof BooleanQuery bq) {
      for (BooleanClause clause : bq.clauses()) {
        if (clause.query() instanceof WrappedToParentBlockJoinQuery bjq) {
          return Optional.of(bjq);
        }
      }
    }
    return Optional.empty();
  }

  private static ScoreDoc[] deepCopy(ScoreDoc[] original) {
    ScoreDoc[] copy = new ScoreDoc[original.length];
    for (int i = 0; i < original.length; i++) {
      copy[i] = new ScoreDoc(original[i].doc, original[i].score, original[i].shardIndex);
    }
    return copy;
  }
}
