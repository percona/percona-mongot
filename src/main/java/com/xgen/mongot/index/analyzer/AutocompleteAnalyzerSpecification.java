package com.xgen.mongot.index.analyzer;

import com.google.common.annotations.VisibleForTesting;
import com.xgen.mongot.index.definition.AutocompleteFieldDefinition;
import com.xgen.mongot.util.Check;
import java.util.Objects;

/**
 * An {@link AutocompleteAnalyzerSpecification} describes how an AutocompleteAnalyzer behaves. Two
 * instances that {@link AutocompleteAnalyzerSpecification#equals(Object)} each other will create
 * equivalent analyzers.
 */
class AutocompleteAnalyzerSpecification {
  private final int minGrams;
  private final int maxGrams;
  private final boolean foldDiacritics;
  private final TokenizationStrategy tokenizationStrategy;
  private final AnalyzerContainer baseAnalyzerContainer;

  public enum TokenizationStrategy {
    EDGE_GRAM,
    NGRAM,
    RIGHT_EDGE_GRAM
  }

  @VisibleForTesting
  AutocompleteAnalyzerSpecification(
      int minGrams,
      int maxGrams,
      boolean foldDiacritics,
      TokenizationStrategy strategy,
      AnalyzerContainer baseAnalyzerContainer) {
    this.minGrams = minGrams;
    this.maxGrams = maxGrams;
    this.foldDiacritics = foldDiacritics;
    this.tokenizationStrategy = strategy;
    this.baseAnalyzerContainer = baseAnalyzerContainer;
  }

  static AutocompleteAnalyzerSpecification create(
      AutocompleteFieldDefinition autocompleteFieldDefinition,
      AnalyzerContainer analyzerContainer) {
    Check.checkState(
        TokenStreamTypeProvider.getTokenStreamType(analyzerContainer.definition())
            .map(type -> !type.isGraph())
            .orElse(false),
        "analyzer must not produce graph tokens");

    return new AutocompleteAnalyzerSpecification(
        autocompleteFieldDefinition.getMinGrams(),
        autocompleteFieldDefinition.getMaxGrams(),
        autocompleteFieldDefinition.isFoldDiacritics(),
        getAnalyzerTokenizationStrategy(autocompleteFieldDefinition),
        analyzerContainer);
  }

  /** Analyzer tokenization strategy from Field Definition tokenization specification. */
  private static TokenizationStrategy getAnalyzerTokenizationStrategy(
      AutocompleteFieldDefinition fieldDefinition) {
    return switch (fieldDefinition.getTokenization()) {
      case EDGE_GRAM -> TokenizationStrategy.EDGE_GRAM;
      case N_GRAM -> TokenizationStrategy.NGRAM;
      case RIGHT_EDGE_GRAM -> TokenizationStrategy.RIGHT_EDGE_GRAM;
    };
  }

  int getMinGrams() {
    return this.minGrams;
  }

  int getMaxGrams() {
    return this.maxGrams;
  }

  boolean isFoldDiacritics() {
    return this.foldDiacritics;
  }

  TokenizationStrategy getTokenizationStrategy() {
    return this.tokenizationStrategy;
  }

  AnalyzerContainer getBaseAnalyzerContainer() {
    return this.baseAnalyzerContainer;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof AutocompleteAnalyzerSpecification other)) {
      return false;
    }
    return this.minGrams == other.minGrams
        && this.maxGrams == other.maxGrams
        && this.foldDiacritics == other.foldDiacritics
        && this.tokenizationStrategy == other.tokenizationStrategy
        && Objects.equals(this.baseAnalyzerContainer, other.baseAnalyzerContainer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.minGrams,
        this.maxGrams,
        this.foldDiacritics,
        this.tokenizationStrategy,
        this.baseAnalyzerContainer);
  }
}
