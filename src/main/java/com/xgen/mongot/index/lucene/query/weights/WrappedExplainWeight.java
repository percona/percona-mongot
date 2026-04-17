package com.xgen.mongot.index.lucene.query.weights;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.index.lucene.query.util.WrappedToParentBlockJoinQuery;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.sandbox.search.TermAutomatonQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseWeight;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

/**
 * {@link WrappedExplainWeight} wraps a {@link Weight} to produce score details. It performs the
 * following:
 *
 * <ul>
 *   <li>Provides a default placeholder {@link Explanation} for weights that do not have {@link
 *       Weight#explain(LeafReaderContext, int)} implemented, or return null in their implementation
 *       (for example, see {@link TermAutomatonQuery.TermAutomatonWeight#explain(LeafReaderContext,
 *       int)}, which is used for score details on synonym queries.)
 *   <li>Validates the score of the generated {@link Explanation}.
 *   <li>Allows subclasses to customize the generated {@link Explanation}'s description.
 * </ul>
 */
public class WrappedExplainWeight extends Weight {
  private final Weight weight;

  WrappedExplainWeight(Weight weight) {
    super(weight.getQuery());
    this.weight = weight;
  }

  public static WrappedExplainWeight create(
      Query query, IndexSearcher searcher, ScoreMode mode, float boost) throws IOException {
    try {
      return wrapWeight(searcher, query.createWeight(searcher, mode, boost), query);
    } catch (IOException | UnsupportedOperationException e) {
      // used if Query#createWeight is not implemented
      return new WrappedExplainWeight(new EmptyWeight(query));
    }
  }

  private static WrappedExplainWeight wrapWeight(
      IndexSearcher searcher, Weight weight, Query luceneQuery) {
    if (luceneQuery instanceof TermQuery) {
      return new SafeTermWeight(weight, searcher.getSimilarity());
    } else if (luceneQuery instanceof WrappedToParentBlockJoinQuery) {
      return new SafeBlockJoinWeight(weight);
    } else if (luceneQuery instanceof BoostQuery) {
      return wrapWeight(searcher, weight, ((BoostQuery) luceneQuery).getQuery());
    } else if (luceneQuery instanceof ConstantScoreQuery) {
      return wrapWeight(searcher, weight, ((ConstantScoreQuery) luceneQuery).getQuery());
    } else if (luceneQuery instanceof FunctionScoreQuery) {
      return wrapWeight(searcher, weight, ((FunctionScoreQuery) luceneQuery).getWrappedQuery());
    } else if (weight instanceof PhraseWeight) {
      return new SafePhraseWeight((PhraseWeight) weight, searcher.getSimilarity());
    }
    return new WrappedExplainWeight(weight);
  }

  @Override
  public Explanation explain(LeafReaderContext context, int doc) throws IOException {
    try {
      Optional<Explanation> explanation = rewriteExplanation(context, doc);
      String placeholderDescription = this.parentQuery.toString();

      if (explanation.isPresent()) {
        return validateExplanation(context, doc, explanation.get());
      }

      // No explanation available, or score found in the explanation was inaccurate; create
      // placeholder explanation
      Optional<Scorer> scorer = Optional.ofNullable(scorer(context));
      if (scorer.isPresent() && scorer.get().iterator().advance(doc) == doc) {
        return Explanation.match(scorer.get().score(), placeholderDescription);
      }
      return Explanation.noMatch(placeholderDescription);
    } catch (IOException ignored) {
      return Explanation.noMatch(
          String.format("Could not generate explanation on query %s", this.parentQuery.toString()));
    }
  }

  /**
   * If a generated {@link Explanation} exists, rewrite its description if a subclass has overridden
   * the appropriate rewrite helper method.
   */
  @VisibleForTesting
  Optional<Explanation> rewriteExplanation(LeafReaderContext context, int doc) throws IOException {
    Optional<Explanation> innerExplanation = Optional.ofNullable(this.weight.explain(context, doc));
    // Some weights may return null in their document explain implementation
    if (innerExplanation.isEmpty()) {
      return innerExplanation;
    }

    Explanation explanation = innerExplanation.get();
    if (explanation.isMatch()) {
      return matchRewrite()
          .map(description -> changeExplanationDescription(explanation, description))
          .or(() -> innerExplanation);
    }
    return noMatchRewrite()
        .map(description -> changeExplanationDescription(explanation, description))
        .or(() -> innerExplanation);
  }

  /**
   * Validates that the score found in the generated {@link Explanation} is accurate. If it's not,
   * we replace the generated {@link Explanation} with a new {@link Explanation} containing the
   * correct score and the query name as a placeholder description.
   */
  @VisibleForTesting
  Explanation validateExplanation(LeafReaderContext context, int doc, Explanation explanation)
      throws IOException {
    Optional<Scorer> scorer = Optional.ofNullable(scorer(context));

    if (scorer.isPresent()
        && scorer.get().iterator().advance(doc) == doc
        && Float.compare(scorer.get().score(), explanation.getValue().floatValue()) != 0) {
      // Use a placeholder explanation if there is a mismatch between the scorer's score and the
      // generated explain output's score value
      return Explanation.match(scorer.get().score(), this.parentQuery.getClass().getSimpleName());
    }
    return explanation;
  }

  private static Explanation changeExplanationDescription(
      Explanation explanation, String description) {
    if (explanation.isMatch()) {
      return Explanation.match(explanation.getValue(), description, explanation.getDetails());
    }
    return Explanation.noMatch(description, explanation.getDetails());
  }

  Optional<String> matchRewrite() {
    return Optional.empty();
  }

  Optional<String> noMatchRewrite() {
    return Optional.empty();
  }

  @Override
  @Nullable
  public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
    return this.weight.scorerSupplier(context);
  }

  @Override
  public boolean isCacheable(LeafReaderContext ctx) {
    return this.weight.isCacheable(ctx);
  }
}
