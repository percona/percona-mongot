package com.xgen.mongot.index.lucene.explain.profiler;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ProfileScorerTest {

  private static IndexWriter writer;
  private static IndexReader reader;
  private static IndexSearcher searcher;

  @BeforeClass
  public static void setUpClass() throws IOException {
    Directory directory = new ByteBuffersDirectory();
    ProfileScorerTest.writer = new IndexWriter(directory, new IndexWriterConfig());

    Document document = new Document();
    document.add(new StringField("foo", "a", Field.Store.NO));
    writer.addDocument(document);
    writer.commit();

    ProfileScorerTest.reader = DirectoryReader.open(directory);
    ProfileScorerTest.searcher = new IndexSearcher(reader);
  }

  private static Scorer createScorer() throws Exception {
    Query query = new TermQuery(new Term("foo", "a"));
    Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE, 1.0f);

    var contextLeaves = searcher.getTopReaderContext().leaves();
    Assert.assertEquals("should have one leaf", 1, contextLeaves.size());
    return weight.scorer(contextLeaves.get(0));
  }

  private static Scorer wrapScorerWithConstant(Scorer scorer) throws IOException {
    return new ConstantScoreScorer(
        scorer.score(),
        ScoreMode.COMPLETE,
        new TwoPhaseIterator(scorer.iterator()) {
          @Override
          public boolean matches() throws IOException {
            return false;
          }

          @Override
          public float matchCost() {
            return 0;
          }
        });
  }

  @Test
  public void testIteratorAdvance() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    checkIteratorAdvance(scorer.iterator(), timings);
  }

  @Test
  public void testIteratorNextDoc() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    checkIteratorNextDoc(scorer.iterator(), timings);
  }

  @Test
  public void testIteratorDocId() throws Exception {
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, ExplainTimings.builder().build());

    checkIteratorDocId(scorer.iterator());
  }

  @Test
  public void testTwoPhaseIteratorApproximationAdvance() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = wrapScorerWithConstant(createScorer());
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    checkIteratorAdvance(scorer.twoPhaseIterator().approximation(), timings);
  }

  @Test
  public void testTwoPhaseIteratorApproximationNextDoc() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = wrapScorerWithConstant(createScorer());
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    checkIteratorNextDoc(scorer.twoPhaseIterator().approximation(), timings);
  }

  @Test
  public void testTwoPhaseIteratorApproximationDocId() throws Exception {
    ExplainTimings timings = ExplainTimings.builder().build();
    Scorer wrappedScorer = wrapScorerWithConstant(createScorer());
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    checkIteratorDocId(scorer.twoPhaseIterator().approximation());
  }

  @Test
  public void testTwoPhaseIteratorMatches() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = wrapScorerWithConstant(createScorer());
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);
    TwoPhaseIterator twoPhaseIterator = scorer.twoPhaseIterator();
    DocIdSetIterator approximation = twoPhaseIterator.approximation();
    while (approximation.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      twoPhaseIterator.matches();
    }
    verify(timings, times(2)).split(ExplainTimings.Type.NEXT_DOC);
    verify(timings).split(ExplainTimings.Type.MATCH);
    verifyNoMoreInteractions(timings);
  }

  @Test
  public void testAdvanceShallow() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    scorer.advanceShallow(0);
    verify(timings).split(ExplainTimings.Type.SHALLOW_ADVANCE);
    verifyNoMoreInteractions(timings);
  }

  @Test
  public void testGetMaxScore() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    // getMaxScore returns the maximum score that documents between the last target docID that the
    // scorer was advanceShallow'ed to and upTo, inclusive. advanceShallow to the first (only) docID
    // before calling getMaxScore.
    scorer.advanceShallow(0);
    scorer.getMaxScore(1);
    verify(timings).split(ExplainTimings.Type.SHALLOW_ADVANCE);
    verify(timings).split(ExplainTimings.Type.COMPUTE_MAX_SCORE);
    verifyNoMoreInteractions(timings);
  }

  @Test
  public void testSetMinCompetitiveScore() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    scorer.setMinCompetitiveScore(0.5f);
    verify(timings).split(ExplainTimings.Type.SET_MIN_COMPETITIVE_SCORE);
    verifyNoMoreInteractions(timings);
  }

  @Test
  public void testScore() throws Exception {
    ExplainTimings timings = spy(ExplainTimings.builder().build());
    Scorer wrappedScorer = createScorer();
    ProfileScorer scorer = new ProfileScorer(wrappedScorer, timings);

    // score returns the score of the current document matching the query. Advance to the first
    // document before calling score.
    scorer.iterator().nextDoc();
    scorer.score();
    verify(timings).split(ExplainTimings.Type.NEXT_DOC);
    verify(timings).split(ExplainTimings.Type.SCORE);
    verifyNoMoreInteractions(timings);
  }

  private static void checkIteratorAdvance(DocIdSetIterator iterator, ExplainTimings timings)
      throws Exception {
    Assert.assertEquals("first docId should be 0", 0, iterator.advance(0));
    Assert.assertEquals(
        "second docId should be DocIdSetIterator.NO_MORE_DOCS",
        DocIdSetIterator.NO_MORE_DOCS,
        iterator.advance(1));

    verify(timings, times(2)).split(ExplainTimings.Type.ADVANCE);
    verifyNoMoreInteractions(timings);
  }

  private static void checkIteratorNextDoc(DocIdSetIterator iterator, ExplainTimings timings)
      throws Exception {
    Assert.assertEquals("first docId should be 0", 0, iterator.nextDoc());
    Assert.assertEquals(
        "second docId should be DocIdSetIterator.NO_MORE_DOCS",
        DocIdSetIterator.NO_MORE_DOCS,
        iterator.nextDoc());

    verify(timings, times(2)).split(ExplainTimings.Type.NEXT_DOC);
    verifyNoMoreInteractions(timings);
  }

  private static void checkIteratorDocId(DocIdSetIterator iterator) throws Exception {
    Assert.assertEquals("docID should be -1 before nextDoc", -1, iterator.docID());
    var firstDoc = iterator.nextDoc();
    Assert.assertEquals("docID should be same as nextDoc", firstDoc, iterator.docID());
    var secondDoc = iterator.nextDoc();
    Assert.assertEquals("docID should be same as nextDoc", secondDoc, iterator.docID());
  }
}
