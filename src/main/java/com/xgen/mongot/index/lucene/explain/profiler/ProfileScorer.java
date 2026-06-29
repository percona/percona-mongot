package com.xgen.mongot.index.lucene.explain.profiler;

import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings;
import com.xgen.mongot.index.lucene.explain.timing.ExplainTimings.Type;
import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.jetbrains.annotations.Nullable;

/**
 * Instruments a {@link Scorer} to profile methods related to scoring. Responsible for populating
 * {@code ADVANCE}, {@code NEXT_DOC}, {@code MATCH}, {@code SHALLOW_ADVANCE}, {@code
 * COMPUTE_MAX_SCORE}, {@code SET_MIN_COMPETITIVE_SCORE}, and {@code SCORE} timings in {@link
 * ExplainTimings}.
 *
 * <p>In lucene, {@code Scorer}s are typically created by {@link Weight}s. In our case, an explain
 * command instantiates {@code ProfileScorer} from {@link ProfileWeight}.
 */
class ProfileScorer extends Scorer {
  private final Scorer scorer;
  private final ExplainTimings timings;

  /** Constructs a ProfileScorer. */
  protected ProfileScorer(Scorer scorer, ExplainTimings timings) {
    this.scorer = scorer;
    this.timings = timings;
  }

  @Override
  public DocIdSetIterator iterator() {
    return ProfileDocIdSetIterator.create(this.scorer.iterator(), this.timings);
  }

  @Override
  @Nullable
  public TwoPhaseIterator twoPhaseIterator() {
    TwoPhaseIterator twoPhaseIterator = this.scorer.twoPhaseIterator();
    if (twoPhaseIterator == null) {
      return null;
    }

    DocIdSetIterator approximation =
        ProfileDocIdSetIterator.create(twoPhaseIterator.approximation(), this.timings);
    ProfileScorer profileScorer = this;

    return new TwoPhaseIterator(approximation) {
      @Override
      public boolean matches() throws IOException {
        try (var ignored = profileScorer.timings.split(Type.MATCH)) {
          return twoPhaseIterator.matches();
        }
      }

      @Override
      public float matchCost() {
        return twoPhaseIterator.matchCost();
      }
    };
  }

  @Override
  public int advanceShallow(int target) throws IOException {
    try (var ignored = this.timings.split(Type.SHALLOW_ADVANCE)) {
      return this.scorer.advanceShallow(target);
    }
  }

  @Override
  public float getMaxScore(int upTo) throws IOException {
    try (var ignored = this.timings.split(Type.COMPUTE_MAX_SCORE)) {
      return this.scorer.getMaxScore(upTo);
    }
  }

  @Override
  public void setMinCompetitiveScore(float minScore) throws IOException {
    try (var ignored = this.timings.split(Type.SET_MIN_COMPETITIVE_SCORE)) {
      this.scorer.setMinCompetitiveScore(minScore);
    }
  }

  @Override
  public float score() throws IOException {
    try (var ignored = this.timings.split(Type.SCORE)) {
      return this.scorer.score();
    }
  }

  @Override
  public int docID() {
    return this.scorer.docID();
  }

  @Override
  public Collection<ChildScorable> getChildren() throws IOException {
    return this.scorer.getChildren();
  }
}
