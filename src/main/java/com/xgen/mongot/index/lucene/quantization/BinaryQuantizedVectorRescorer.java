package com.xgen.mongot.index.lucene.quantization;

import static org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.index.lucene.explain.knn.InstrumentableKnnFloatVectorQuery;
import com.xgen.mongot.index.lucene.explain.tracing.Explain;
import com.xgen.mongot.index.query.operators.ApproximateVectorSearchCriteria;
import com.xgen.mongot.util.concurrent.NamedExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TaskExecutor;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.util.quantization.QuantizedVectorsReader;

/**
 * Performs two-stage rescoring of {@link TopDocs} provided after HNSW graph traversal based on
 * binary quantized vectors.
 */
public class BinaryQuantizedVectorRescorer {

  private final Optional<NamedExecutorService> executor;

  public BinaryQuantizedVectorRescorer(Optional<NamedExecutorService> executor) {
    this.executor = executor;
  }

  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  public TopDocs rescore(
      IndexSearcher indexSearcher,
      TopDocs topDocs,
      ApproximateVectorSearchCriteria searchCriteria,
      KnnFloatVectorQuery luceneQuery)
      throws IOException {

    if (topDocs.scoreDocs.length == 0) {
      return topDocs;
    }

    // Stage 1 - rescore float query against the oversampled dequantized binary vectors
    ApproximateRescorer approximateRescorer = new ApproximateRescorer(luceneQuery);
    TopDocs approximatelyRescored =
        approximateRescorer.rescore(indexSearcher, topDocs, searchCriteria.numCandidates());

    // Stage 2 - rescore float query against full fidelity vectors for the limited subset of docs
    FullFidelityRescorer fullFidelityRescorer =
        new FullFidelityRescorer(luceneQuery, this.executor);

    // Use geometric mean between the limit and numCandidates to get a number in between with a bias
    // towards the lower one (limit). Using a larger number improves recall, but affects latency
    // as accessing full fidelity vectors could result in page faults
    int fullFidelityLimit =
        (int) Math.sqrt(searchCriteria.limit() * searchCriteria.numCandidates());
    TopDocs fullFidelityRescoredDocs =
        fullFidelityRescorer.rescore(indexSearcher, approximatelyRescored, fullFidelityLimit);

    if (fullFidelityRescoredDocs.scoreDocs.length > searchCriteria.limit()) {
      // Truncate the final result result to limit
      fullFidelityRescoredDocs.scoreDocs =
          Arrays.copyOf(fullFidelityRescoredDocs.scoreDocs, searchCriteria.limit());
    }

    if (Explain.isEnabled()
        && luceneQuery instanceof InstrumentableKnnFloatVectorQuery instrumentableQuery) {
      instrumentableQuery.examineResultsAfterRescoring(fullFidelityRescoredDocs);
    }

    return fullFidelityRescoredDocs;
  }

  private static void scoreSegment(
      LeafReaderContext segment, VectorScorer scorer, ScoreDoc[] scoreDocs, int start, int end)
      throws IOException {

    for (int i = start; i < end; i++) {
      int target = scoreDocs[i].doc - segment.docBase;
      if (scorer.iterator().advance(target) == NO_MORE_DOCS) {
        throw new IllegalArgumentException("Doc " + scoreDocs[i].doc + " doesn't have a vector");
      }

      scoreDocs[i].score = scorer.score();
    }
  }

  private static FieldInfo getFieldInfo(LeafReader context, KnnFloatVectorQuery query) {
    return context.getFieldInfos().fieldInfo(query.getField());
  }

  private static TopDocs deepCopy(TopDocs topDocs, int topN) {
    ScoreDoc[] arrayCopy = new ScoreDoc[Math.min(topDocs.scoreDocs.length, topN)];
    for (int i = 0; i < arrayCopy.length; ++i) {
      ScoreDoc s = topDocs.scoreDocs[i];
      arrayCopy[i] = new ScoreDoc(s.doc, s.score, s.shardIndex);
    }
    return new TopDocs(new TotalHits(arrayCopy.length, TotalHits.Relation.EQUAL_TO), arrayCopy);
  }

  @VisibleForTesting
  static class ApproximateRescorer extends Rescorer {

    private static final FluentLogger flogger = FluentLogger.forEnclosingClass();

    private final KnnFloatVectorQuery query;

    public ApproximateRescorer(KnnFloatVectorQuery query) {
      this.query = query;
    }

    @Override
    public TopDocs rescore(IndexSearcher searcher, TopDocs topDocs, int topN) throws IOException {
      float[] queryVector = this.query.getTargetCopy();

      // truncate topDocs to topN
      TopDocs topDocsCopy = deepCopy(topDocs, topN);
      int limit = topDocsCopy.scoreDocs.length;

      // sort for the sequential access
      Arrays.sort(topDocsCopy.scoreDocs, Comparator.comparingInt(hit -> hit.doc));
      List<LeafReaderContext> segments = searcher.getIndexReader().leaves();

      @Var int i = 0;
      while (i < limit) {

        int start = i;
        LeafReaderContext segment =
            segments.get(ReaderUtil.subIndex(topDocsCopy.scoreDocs[i].doc, segments));
        int maxDoc = segment.docBase + segment.reader().maxDoc();

        for (i = i + 1; i < limit; i++) {
          if (topDocsCopy.scoreDocs[i].doc >= maxDoc) {
            break;
          }
        }
        int end = i;

        Optional<QuantizedVectorsReader> vectorsReader = getVectorsReader(segment.reader());

        if (vectorsReader.isEmpty()) {
          flogger.atWarning().atMostEvery(1, TimeUnit.MINUTES).log(
              "Skipped rescoring because vectors reader was not found for the segment");
          return topDocs; // skip re-scoring if vectors reader was not found
        }

        if (vectorsReader.get().getQuantizedVectorValues(this.query.getField()) == null) {
          flogger.atWarning().atMostEvery(1, TimeUnit.MINUTES).log(
              "Skipped rescoring because quantized vector values for %s were null",
              this.query.getField());
          return topDocs; // skip re-scoring if vector values were not found
        }

        DequantizedVectorValues vectorValues =
            new DequantizedVectorValues(
                vectorsReader.get().getQuantizedVectorValues(this.query.getField()),
                getFieldInfo(segment.reader(), this.query).getVectorSimilarityFunction());

        scoreSegment(segment, vectorValues.scorer(queryVector), topDocsCopy.scoreDocs, start, end);
      }

      // sort by score
      Arrays.sort(topDocsCopy.scoreDocs, (a, b) -> -Float.compare(a.score, b.score));

      return topDocsCopy;
    }

    @Override
    public Explanation explain(
        IndexSearcher searcher, Explanation firstPassExplanation, int docID) {
      throw new UnsupportedOperationException();
    }

    private Optional<QuantizedVectorsReader> getVectorsReader(LeafReader reader) {

      if (!(reader instanceof CodecReader)) {
        return Optional.empty();
      }

      @Var KnnVectorsReader vectorsReader = ((CodecReader) reader).getVectorReader();

      if (vectorsReader instanceof PerFieldKnnVectorsFormat.FieldsReader) {
        vectorsReader =
            ((PerFieldKnnVectorsFormat.FieldsReader) vectorsReader)
                .getFieldReader(this.query.getField());
      }

      if (vectorsReader instanceof QuantizedVectorsReader) {
        return Optional.of((QuantizedVectorsReader) vectorsReader);
      }

      return Optional.empty();
    }
  }

  @VisibleForTesting
  static class FullFidelityRescorer extends Rescorer {

    private final KnnFloatVectorQuery query;
    private final Optional<TaskExecutor> executor;

    public FullFidelityRescorer(
        KnnFloatVectorQuery query, Optional<NamedExecutorService> executorService) {
      this.query = query;
      this.executor = executorService.map(TaskExecutor::new);
    }

    @Override
    public TopDocs rescore(IndexSearcher searcher, TopDocs topDocs, int topN) throws IOException {

      // truncate topDocs to the specified limit
      TopDocs topDocsCopy = deepCopy(topDocs, topN);
      int limit = topDocsCopy.scoreDocs.length;
      // sort for the sequential access
      Arrays.sort(topDocsCopy.scoreDocs, Comparator.comparingInt(hit -> hit.doc));

      List<LeafReaderContext> segments = searcher.getIndexReader().leaves();
      List<Callable<Void>> tasks = new ArrayList<>();

      @Var int i = 0;

      while (i < limit) {

        int start = i;
        LeafReaderContext segment =
            segments.get(ReaderUtil.subIndex(topDocsCopy.scoreDocs[i].doc, segments));
        int maxDoc = segment.docBase + segment.reader().maxDoc();

        for (i = i + 1; i < limit; i++) {
          if (topDocsCopy.scoreDocs[i].doc >= maxDoc) {
            break;
          }
        }
        int end = i;

        VectorScorer scorer = createScorer(segment.reader());

        tasks.add(
            () -> {
              scoreSegment(segment, scorer, topDocsCopy.scoreDocs, start, end);
              return null;
            });
      }

      if (this.executor.isPresent()) {
        // execute tasks and wait for completion. if exceptions happen,
        // they are accumulated across tasks and thrown and the end
        this.executor.get().invokeAll(tasks);
      } else {
        invokeAllSequentially(tasks);
      }

      // sort by score
      Arrays.sort(topDocsCopy.scoreDocs, (a, b) -> -Float.compare(a.score, b.score));

      return topDocsCopy;
    }

    @Override
    public Explanation explain(
        IndexSearcher searcher, Explanation firstPassExplanation, int docID) {
      throw new UnsupportedOperationException();
    }

    private VectorScorer createScorer(LeafReader leafReader) throws IOException {
      FloatVectorValues vectorValues = leafReader.getFloatVectorValues(this.query.getField());
      FloatVectorValues copy = vectorValues.copy();
      FloatVectorValues.DocIndexIterator iterator = copy.iterator();

      VectorSimilarityFunction similarityFunction =
          getFieldInfo(leafReader, this.query).getVectorSimilarityFunction();
      float[] target = this.query.getTargetCopy();
      return new VectorScorer() {

        @Override
        public float score() throws IOException {
          return similarityFunction.compare(target, copy.vectorValue(iterator.index()));
        }

        @Override
        public DocIdSetIterator iterator() {
          return iterator;
        }
      };
    }

    private void invokeAllSequentially(List<Callable<Void>> tasks) throws IOException {
      for (Callable<Void> task : tasks) {
        try {
          task.call();
        } catch (IOException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
