package com.xgen.mongot.index.lucene.query;

import com.xgen.mongot.index.definition.AutocompleteFieldDefinition;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.lucene.query.context.SearchQueryFactoryContext;
import com.xgen.mongot.index.lucene.query.util.BooleanComposer;
import com.xgen.mongot.index.lucene.query.util.SafeQueryBuilder;
import com.xgen.mongot.index.lucene.util.AnalyzedText;
import com.xgen.mongot.index.path.string.StringFieldPath;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.operators.AutocompleteOperator;
import com.xgen.mongot.index.query.operators.FuzzyOption;
import com.xgen.mongot.util.FieldPath;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.LevenshteinAutomata;
import org.apache.lucene.util.automaton.Operations;

class AutocompleteQueryFactory {
  // Setting this to true allows the levenshtein automata to count transpositions as an edit, which
  // is desired.
  private static final boolean FUZZY_WITH_TRANSPOSITIONS = true;

  private final SearchQueryFactoryContext context;

  AutocompleteQueryFactory(SearchQueryFactoryContext context) {
    this.context = context;
  }

  /**
   * Create an autocomplete query, boosting exact matches if a field is also indexed as a string.
   *
   * <p>Accomplish this by creating a boolean query with this logic:
   *
   * <ul>
   *   <li>must: (should: match any query clause to indexed autocomplete fragment,
   *       minimumShouldMatch: 1)
   *   <li>should: match any query clause to indexed string token, minimumShouldMatch: 0
   * </ul>
   */
  Query fromCompletion(AutocompleteOperator operator, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    return new BooleanQuery.Builder()
        .add(autocompleteDisjunction(operator, singleQueryContext), BooleanClause.Occur.MUST)
        .add(exactMatchBoostDisjunction(operator, singleQueryContext), BooleanClause.Occur.SHOULD)
        .setMinimumNumberShouldMatch(0)
        .build();
  }

  /** Create a boolean query with should clauses matching autocomplete-indexed fragments. */
  private Query autocompleteDisjunction(
      AutocompleteOperator operator, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    FieldPath fieldPath = operator.path();

    Optional<AutocompleteFieldDefinition> autocompleteFieldDefinition =
        this.context.getAutocompleteDefinition(fieldPath, singleQueryContext.getEmbeddedRoot());

    InvalidQueryException.validate(
        autocompleteFieldDefinition.isPresent(),
        "autocomplete index field definition not present at path %s",
        fieldPath);
    //noinspection OptionalGetWithoutIsPresent - this is ok because of above QPE.validate call.
    AutocompleteFieldDefinition autocompleteField = autocompleteFieldDefinition.get();

    Optional<FuzzyOption> fuzzyOption = operator.fuzzy();
    if (fuzzyOption.isPresent()) {
      if (autocompleteField.getMaxGrams() < fuzzyOption.get().prefixLength()) {
        throw new InvalidQueryException(
            "minimum fuzzy prefix length must be less than the maximum indexed token length");
      }
    }

    return BooleanComposer.StreamUtils.from(
            queryTokensOf(
                operator,
                singleQueryContext,
                this.context.getAutocompleteBaseAnalyzer(autocompleteField),
                this.context.getAutocompleteIndexAnalyzer(autocompleteField)))
        .map(
            queryFactoryFor(
                FieldName.TypeField.AUTOCOMPLETE.getLuceneFieldName(
                    fieldPath, singleQueryContext.getEmbeddedRoot()),
                fuzzyOption),
            BooleanClause.Occur.SHOULD);
  }

  Function<BytesRef, Query> queryFactoryFor(
      String luceneFieldPath, Optional<FuzzyOption> fuzzyOption) {
    if (fuzzyOption.isEmpty()) {
      return token -> queryFor(luceneFieldPath, token);
    }

    return token -> fuzzyQueryFor(luceneFieldPath, token, fuzzyOption.get());
  }

  /**
   * Gets a list of analyzed tokens from this query. First tokenizes using the base analyzer for
   * this autocomplete field, then normalizes with the analyzer used at index-time to apply
   * transformations like diacritic folding.
   *
   * <p>Normalization may produce zero tokens for a given input if the analyzer removes it (e.g. via
   * stopword filtering). Such tokens are excluded from the result. If all tokens are removed, an
   * {@link InvalidQueryException} is thrown.
   */
  private static List<BytesRef> queryTokensOf(
      AutocompleteOperator operator,
      SingleQueryContext singleQueryContext,
      Analyzer baseAnalyzer,
      Analyzer indexAnalyzer)
      throws InvalidQueryException {
    String luceneFieldPath =
        FieldName.TypeField.AUTOCOMPLETE.getLuceneFieldName(
            operator.path(), singleQueryContext.getEmbeddedRoot());

    // "Normalizing" a query string performs diacritic and case folding if they are configured in
    // the analyzer, and may also truncate the query string. Text is analyzed by the user-specified
    // "baseAnalyzer" first, then are normalized by the internal autocomplete "indexAnalyzer" using
    // this function. Normalization may yield zero tokens if the analyzer removes the input (e.g.
    // stopword filter), which is signaled by Lucene's Analyzer.normalize throwing
    // IllegalStateException with "got 0" in the message. The >1 token case ("got 2+") indicates a
    // genuine analyzer misconfiguration and is re-thrown.
    Function<String, Optional<BytesRef>> normalize =
        token -> {
          try {
            return Optional.of(indexAnalyzer.normalize(luceneFieldPath, token));
          } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("got 0")) {
              return Optional.empty();
            }
            throw e;
          }
        };

    List<BytesRef> tokens =
        switch (operator.tokenOrder()) {
          case ANY -> {
            try {
              yield ListUtils.union(
                      AnalyzedText.applyAnalyzer(
                          baseAnalyzer,
                          new StringFieldPath(operator.path()),
                          operator.query(),
                          singleQueryContext.getEmbeddedRoot()),
                      operator.query())
                  .stream()
                  .flatMap(token -> normalize.apply(token).stream())
                  .distinct()
                  .collect(Collectors.toList());
            } catch (IOException e) {
              throw new InvalidQueryException("error expanding query to order-agnostic form");
            }
          }

          case SEQUENTIAL ->
              operator.query().stream()
                  .flatMap(token -> normalize.apply(token).stream())
                  .distinct()
                  .collect(Collectors.toList());
        };

    if (tokens.isEmpty()) {
      throw new InvalidQueryException(
          "no search tokens produced for autocomplete query after analysis; "
              + "query tokens may have been removed by token filters such as stop word filters");
    }

    return tokens;
  }

  /**
   * Generate a simple query for completion. The "heavy lifting" for this query is done at
   * index-time, which lets this method generate fast queries for exact terms.
   */
  private static Query queryFor(String luceneFieldPath, BytesRef analyzedQueryToken) {
    return new TermQuery(new Term(luceneFieldPath, analyzedQueryToken));
  }

  private static Query fuzzyQueryFor(
      String luceneFieldPath, BytesRef queryBytes, FuzzyOption fuzzyOption) {
    String normalizedQueryString = queryBytes.utf8ToString();

    // If queryString is less than or equal to the minimum fuzzy prefix length, this query must be
    // an exact match.
    if (normalizedQueryString.length() <= fuzzyOption.prefixLength()) {
      return queryFor(luceneFieldPath, queryBytes);
    }

    String exactMatchPrefix = normalizedQueryString.substring(0, fuzzyOption.prefixLength());
    String suffix = normalizedQueryString.substring(fuzzyOption.prefixLength());

    LevenshteinAutomata distanceAutomaton =
        new LevenshteinAutomata(suffix, FUZZY_WITH_TRANSPOSITIONS);
    return new AutomatonQuery(
        new Term(luceneFieldPath, normalizedQueryString),
        // this should be a no-op, Levenshtein Automaton should already be determinized, but
        // wrapping for safety
        Operations.determinize(
            distanceAutomaton.toAutomaton(fuzzyOption.maxEdits(), exactMatchPrefix),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
  }

  /** Create a boolean query with should clauses matching text-indexed tokens. */
  private Query exactMatchBoostDisjunction(
      AutocompleteOperator operator, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    StringFieldPath stringPath = new StringFieldPath(operator.path());
    return BooleanComposer.StreamUtils.from(operator.query())
        .mapOptionalChecked(
            token ->
                exactMatchClauseFor(operator.tokenOrder(), stringPath, singleQueryContext)
                    .apply(
                        this.context.safeQueryBuilder(
                            stringPath, singleQueryContext.getEmbeddedRoot()),
                        token),
            BooleanClause.Occur.SHOULD);
  }

  private BiFunction<SafeQueryBuilder, String, Optional<Query>> exactMatchClauseFor(
      AutocompleteOperator.TokenOrder tokenOrder,
      StringFieldPath stringPath,
      SingleQueryContext singleQueryContext) {
    String luceneFieldName =
        FieldName.getLuceneFieldNameForStringPath(stringPath, singleQueryContext.getEmbeddedRoot());
    return switch (tokenOrder) {
      case ANY -> (queryBuilder, token) -> queryBuilder.createBooleanQuery(luceneFieldName, token);
      case SEQUENTIAL ->
          (queryBuilder, token) -> queryBuilder.createPhraseQuery(luceneFieldName, token, 0);
    };
  }
}
