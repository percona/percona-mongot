package com.xgen.mongot.index.lucene.query.weights;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.junit.Assert;
import org.junit.Test;

public class WrappedExplainWeightTest {
  @Test
  public void testNullScorerProducesNoMatchExplanation() throws IOException {
    Query query = mock(Query.class);
    when(query.toString()).thenReturn("my-query");

    Explanation expected = Explanation.noMatch("my-query");
    Explanation actual =
        new WrappedExplainWeight(new MockWeight(null, null, query)).explain(null, 0);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testValidateExplanationScoreMismatch() throws IOException {
    Query query = mock(Query.class);
    when(query.toString()).thenReturn("my-query");

    Scorer scorer = mock(Scorer.class);
    DocIdSetIterator docIdSetIterator = mock(DocIdSetIterator.class);
    when(scorer.iterator()).thenReturn(docIdSetIterator);
    when(docIdSetIterator.advance(0)).thenReturn(0);
    when(scorer.score()).thenReturn(5f);

    Explanation expected = Explanation.match(5f, query.getClass().getSimpleName());
    Explanation actual =
        new WrappedExplainWeight(new MockWeight(null, scorer, query))
            .validateExplanation(null, 0, Explanation.match(10f, "unused"));
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testNullExplanationWithScorerAndTrueDocAdvance() throws IOException {
    Query query = mock(Query.class);
    when(query.toString()).thenReturn("my-query");

    Scorer scorer = mock(Scorer.class);
    DocIdSetIterator docIdSetIterator = mock(DocIdSetIterator.class);
    when(scorer.iterator()).thenReturn(docIdSetIterator);
    when(docIdSetIterator.advance(0)).thenReturn(0);
    when(scorer.score()).thenReturn(10f);

    Explanation expected = Explanation.match(10f, "my-query");
    Explanation actual =
        new WrappedExplainWeight(new MockWeight(null, scorer, query)).explain(null, 0);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testNullExplanationWithScorerAndFalseDocAdvance() throws IOException {
    Query query = mock(Query.class);
    when(query.toString()).thenReturn("my-query");

    Scorer scorer = mock(Scorer.class);
    DocIdSetIterator docIdSetIterator = mock(DocIdSetIterator.class);
    when(scorer.iterator()).thenReturn(docIdSetIterator);
    // Advance to the wrong doc
    when(docIdSetIterator.advance(0)).thenReturn(1);

    Explanation expected = Explanation.noMatch("my-query");
    Explanation actual =
        new WrappedExplainWeight(new MockWeight(null, scorer, query)).explain(null, 0);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testWrapSafeTermWeight() throws IOException {
    var wrappedExplainWeight =
        WrappedExplainWeight.create(
            new TermQuery(new Term("a", "unused")),
            new IndexSearcher(new MultiReader()),
            ScoreMode.COMPLETE,
            0f);
    assertThat(wrappedExplainWeight).isInstanceOf(SafeTermWeight.class);
  }

  @Test
  public void testEmptyWeight() throws IOException {
    Query query = mock(Query.class);
    when(query.toString()).thenReturn("my-query");
    when(query.createWeight(any(IndexSearcher.class), any(ScoreMode.class), anyFloat()))
        .thenThrow(IOException.class);

    Explanation expected =
        Explanation.match(
            Float.NaN,
            "query my-query may or may not have matched this document, but an explanation"
                + " could not be generated over it");
    Explanation actual =
        WrappedExplainWeight.create(
                query, new IndexSearcher(new MultiReader()), ScoreMode.COMPLETE, 0f)
            .explain(null, 0);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testRewriteExplanationMatch() throws IOException {
    WrappedExplainWeight rewritingWeight =
        new WrappedExplainWeight(new MockWeight(Explanation.match(5f, "unused"), null, null)) {
          @Override
          Optional<String> matchRewrite() {
            return Optional.of("foo");
          }
        };
    Optional<Explanation> expected = Optional.of(Explanation.match(5f, "foo"));
    Optional<Explanation> actual = rewritingWeight.rewriteExplanation(null, 0);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testRewriteExplanationNoMatch() throws IOException {
    WrappedExplainWeight rewritingWeight =
        new WrappedExplainWeight(new MockWeight(Explanation.noMatch("unused"), null, null)) {
          @Override
          Optional<String> noMatchRewrite() {
            return Optional.of("foo");
          }
        };
    Optional<Explanation> expected = Optional.of(Explanation.noMatch("foo"));
    Optional<Explanation> actual = rewritingWeight.rewriteExplanation(null, 0);
    Assert.assertEquals(expected, actual);
  }

  private static class MockWeight extends Weight {
    private final Explanation explanation;
    private final Scorer scorer;

    private MockWeight(Explanation explanation, Scorer scorer, Query query) {
      super(query);
      this.explanation = explanation;
      this.scorer = scorer;
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc) throws IOException {
      return this.explanation;
    }

    @Override
    public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
      return new ScorerSupplier() {
        @Override
        public Scorer get(long leadCost) throws IOException {
          return MockWeight.this.scorer;
        }

        @Override
        public long cost() {
          return 0;
        }
      };
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
      return false;
    }
  }
}
