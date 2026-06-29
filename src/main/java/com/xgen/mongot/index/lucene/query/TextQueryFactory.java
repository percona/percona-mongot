package com.xgen.mongot.index.lucene.query;

import static com.xgen.mongot.index.lucene.field.FieldName.getLuceneFieldNameForStringPath;

import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.lucene.query.context.SearchQueryFactoryContext;
import com.xgen.mongot.index.lucene.query.util.BooleanComposer;
import com.xgen.mongot.index.lucene.query.util.SafeQueryBuilder;
import com.xgen.mongot.index.lucene.query.util.SafeTermAutomatonQueryWrapper;
import com.xgen.mongot.index.lucene.query.util.SynonymQueryUtil;
import com.xgen.mongot.index.lucene.util.AnalyzedText;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.operators.FuzzyOption;
import com.xgen.mongot.index.query.operators.TextOperator;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.functionalinterfaces.CheckedFunction;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.Term;
import org.apache.lucene.sandbox.search.TokenStreamToTermAutomatonQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;

class TextQueryFactory {

  enum MatchCriteria {
    // matches documents containing any of the query terms
    ANY(Optional.of(BooleanClause.Occur.SHOULD)),
    // matches documents containing all of the query terms
    ALL(Optional.of(BooleanClause.Occur.MUST)),
    // current synonyms behavior, matches documents containing the query as a phrase.
    // TODO(CLOUDP-266504): Remove this.
    PHRASE_SLOP_0(Optional.empty());

    final Optional<BooleanClause.Occur> occur;

    MatchCriteria(Optional<BooleanClause.Occur> occur) {
      this.occur = occur;
    }

    private Optional<BooleanClause.Occur> getOccur() {
      return this.occur;
    }

    private static MatchCriteria fromTextOperatorMatchCriteria(
        TextOperator.MatchCriteria matchCriteria) {
      return switch (matchCriteria) {
        case ALL -> ALL;
        case ANY -> ANY;
      };
    }
  }

  private static final int SYNONYMS_EXACT_MATCH_BOOST_VALUE = 2;
  // Dismax tiebreaker scores additional non-max matches with the tiebreaker value as the multiplier
  // for the score
  private static final float SYNONYMS_DISMAX_TIEBREAKER_VALUE = 0.1f;

  private static final Query MATCH_NO_DOCS_QUERY =
      new MatchNoDocsQuery("Query analysis produced no terms");

  private final SearchQueryFactoryContext queryFactoryContext;
  private final boolean enableTextOperatorNewSynonymsSyntax;

  TextQueryFactory(
      SearchQueryFactoryContext queryFactoryContext, boolean enableTextOperatorNewSynonymsSyntax) {
    this.queryFactoryContext = queryFactoryContext;
    this.enableTextOperatorNewSynonymsSyntax = enableTextOperatorNewSynonymsSyntax;
  }

  Query fromText(TextOperator operator, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    return unscored(operator, singleQueryContext);
  }

  private MatchCriteria getMatchCriteria(TextOperator operator) {
    return operator
        .matchCriteria()
        .map(MatchCriteria::fromTextOperatorMatchCriteria)
        .orElseGet(
            () ->
                // MatchCriteria not explicitly specified.
                // If new syntax feature flag is disabled, and we have synonym query, default to
                // PHRASE_SLOP_0
                // otherwise default to ANY
                !this.enableTextOperatorNewSynonymsSyntax && operator.synonyms().isPresent()
                    ? MatchCriteria.PHRASE_SLOP_0
                    : MatchCriteria.ANY);
  }

  private Query unscored(TextOperator operator, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    MatchCriteria matchCriteria = getMatchCriteria(operator);
    CheckedFunction<StringPathQuery, Query, InvalidQueryException> buildSingle;
    if (operator.fuzzy().isPresent()) {
      buildSingle =
          pq -> createFuzzyQuery(pq, operator.fuzzy().get(), matchCriteria, singleQueryContext);
    } else if (operator.synonyms().isPresent()) {
      buildSingle =
          pq ->
              createSynonymQuery(pq, operator.synonyms().get(), matchCriteria, singleQueryContext);
    } else {
      // Text operator without fuzzy or synonyms specified.
      buildSingle = pq -> createQuery(pq, matchCriteria, singleQueryContext);
    }

    return BooleanComposer.StreamUtils.from(
            StringPathQuery.resolveAndProduct(
                singleQueryContext.getIndexReader(),
                singleQueryContext.getEmbeddedRoot(),
                operator.paths(),
                operator.query()))
        .mapChecked(buildSingle, BooleanClause.Occur.SHOULD);
  }

  private Query createQuery(
      StringPathQuery pq, MatchCriteria matchCriteria, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    SafeQueryBuilder builder =
        this.queryFactoryContext.safeQueryBuilder(
            pq.getPath(), singleQueryContext.getEmbeddedRoot());

    // matchCriteria != PhraseSlop0 must have present occur
    BooleanClause.Occur occur = Check.isPresent(matchCriteria.occur, "occur");
    return builder
        .createBooleanQuery(
            getLuceneFieldNameForStringPath(pq.getPath(), singleQueryContext.getEmbeddedRoot()),
            pq.getQuery(),
            occur)
        .orElse(MATCH_NO_DOCS_QUERY);
  }

  private Query createFuzzyQuery(
      StringPathQuery pq,
      FuzzyOption fuzzyOption,
      MatchCriteria matchCriteria,
      SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    List<String> tokens;
    // First analyze the text
    try {
      tokens =
          AnalyzedText.applyAnalyzer(
              this.queryFactoryContext.getAnalyzer(
                  pq.getPath(), singleQueryContext.getEmbeddedRoot()),
              pq.getPath(),
              pq.getQuery(),
              singleQueryContext.getEmbeddedRoot());
    } catch (IOException e) {
      throw new InvalidQueryException(e.getMessage());
    }

    if (tokens.isEmpty()) {
      return MATCH_NO_DOCS_QUERY;
    }

    // Now build a fuzzy query per token
    String field =
        getLuceneFieldNameForStringPath(pq.getPath(), singleQueryContext.getEmbeddedRoot());

    if (tokens.size() == 1) {
      return new FuzzyQuery(
          new Term(field, tokens.get(0)),
          fuzzyOption.maxEdits(),
          fuzzyOption.prefixLength(),
          fuzzyOption.maxExpansions(),
          FuzzyQuery.defaultTranspositions);
    }

    // non-synonym query matchCriteria must have occur present
    BooleanClause.Occur occur = Check.isPresent(matchCriteria.getOccur(), "occur");
    return tokens.stream()
        .map(
            token ->
                new FuzzyQuery(
                    new Term(field, token),
                    fuzzyOption.maxEdits(),
                    fuzzyOption.prefixLength(),
                    fuzzyOption.maxExpansions(),
                    FuzzyQuery.defaultTranspositions))
        .collect(BooleanComposer.collector(occur));
  }

  private Query createSynonymQuery(
      StringPathQuery pq,
      String synonymMappingName,
      MatchCriteria matchCriteria,
      SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    if (matchCriteria == MatchCriteria.PHRASE_SLOP_0) {
      return createDeprecatedTermAutomatonSynonymQuery(pq, synonymMappingName, singleQueryContext);
    }
    // matchCriteria != PhraseSlop0 must have present occur
    BooleanClause.Occur occur = Check.isPresent(matchCriteria.getOccur(), "occur");

    SafeQueryBuilder synonymQueryBuilder =
        this.queryFactoryContext.synonymQueryBuilder(
            pq.getPath(), singleQueryContext.getEmbeddedRoot(), synonymMappingName);

    Optional<Query> synonymQuery =
        synonymQueryBuilder
            .createBooleanQuery(
                getLuceneFieldNameForStringPath(pq.getPath(), singleQueryContext.getEmbeddedRoot()),
                pq.getQuery(),
                occur)
            .map(TextQueryFactory::rewriteBooleanQueryToDismaxForSynonymTerm);

    if (synonymQuery.isEmpty()) {
      return MATCH_NO_DOCS_QUERY;
    }

    SafeQueryBuilder originalQueryBuilder =
        this.queryFactoryContext.safeQueryBuilder(
            pq.getPath(), singleQueryContext.getEmbeddedRoot());

    Optional<Query> exactMatchQuery =
        originalQueryBuilder.createBooleanQuery(
            getLuceneFieldNameForStringPath(pq.getPath(), singleQueryContext.getEmbeddedRoot()),
            pq.getQuery(),
            occur);

    // If the underlying synonym query contains Lucene PhraseQuery or MultiPhraseQuery, check that
    // the queried path is indexed with position information.
    if (SafeQueryBuilder.containsPhraseQuery(synonymQuery)) {
      this.queryFactoryContext
          .getQueryTimeMappingChecks()
          .validateStringFieldIsIndexedWithPositionInfo(
              pq.getPath(),
              singleQueryContext.getEmbeddedRoot(),
              String.format(
                  "field %s not indexed as string with indexOptions=offset or "
                      + "indexOptions=positions, which is required for text operator queries "
                      + "using synonyms",
                  pq.getPath()));
    }
    // boost "exact match" (docs that would've matched without synonyms) above synonym matches
    // see exact details of boost in SynonymQueryUtil::boostExactMatchSynonymQuery
    return SynonymQueryUtil.boostExactMatchSynonymQuery(
        synonymQuery.get(),
        // exact match query should always be present if synonyms query is present
        Check.isPresent(exactMatchQuery, "exactMatchQuery"),
        SYNONYMS_EXACT_MATCH_BOOST_VALUE);
  }

  private static Query rewriteBooleanQueryToDismaxForSynonymTerm(Query synonymQuery) {
    if (!(synonymQuery instanceof BooleanQuery)) {
      return synonymQuery;
    }

    BooleanQuery booleanQuery = (BooleanQuery) synonymQuery;
    return booleanQuery.clauses().stream()
        .map(
            booleanClause -> {
              if (booleanClause.query() instanceof BooleanQuery) {
                var clauses = ((BooleanQuery) booleanClause.query()).clauses();
                // normalize scores of queries by taking the dismax of the matching synonym query to
                // ensure that the synonym score does not overwhelm the score of
                // non-synonym terms
                //
                // Example:
                // Synonyms: ["fast", "quick"]
                // Query: "fast car"
                // Docs: ["fast quick", "fast car"]
                // In a boolean query, these two would score equivalently, since scores of synonyms
                // are added. We use the dismax query here such that the exact match will score
                // higher, since only the highest score of "fast" and "quick" are applied.
                return new BooleanClause(
                    new DisjunctionMaxQuery(
                        clauses.stream().map(BooleanClause::query).collect(Collectors.toList()),
                        SYNONYMS_DISMAX_TIEBREAKER_VALUE),
                    booleanClause.occur());
              }
              return booleanClause;
            })
        .collect(BooleanComposer.collector(clause -> clause));
  }

  private Query createDeprecatedTermAutomatonSynonymQuery(
      StringPathQuery pq, String synonymMappingName, SingleQueryContext singleQueryContext)
      throws InvalidQueryException {
    Analyzer analyzer =
        this.queryFactoryContext.getSynonymAnalyzer(
            synonymMappingName, pq.getPath(), singleQueryContext.getEmbeddedRoot());

    try (TokenStream queryGraph =
        analyzer.tokenStream(
            FieldName.getLuceneFieldNameForStringPath(
                pq.getPath(), singleQueryContext.getEmbeddedRoot()),
            pq.getQuery())) {
      TokenStreamToTermAutomatonQuery termAutomatonQuery = new TokenStreamToTermAutomatonQuery();
      // Do not preserve position increments. Enabling position increments disallows creation of
      // queries from token streams with holes in them
      termAutomatonQuery.setPreservePositionIncrements(false);

      return InvalidQueryException.wrapIfThrows(
          () ->
              SafeTermAutomatonQueryWrapper.create(
                  termAutomatonQuery.toQuery(
                      FieldName.getLuceneFieldNameForStringPath(
                          pq.getPath(), singleQueryContext.getEmbeddedRoot()),
                      queryGraph)),
          IOException.class);
    } catch (IllegalArgumentException | IOException e) {
      // The synonym analyzer yielded an empty tokenstream since the synonym mapping was empty, so
      // just create an equivalent text operator query without the synonyms option.
      if ("fst must be non-null".equals(e.getMessage())) {
        // maintain deprecated logic of rewriting these queries with default matchCriteria.any
        return createQuery(pq, MatchCriteria.ANY, singleQueryContext);
      }
      throw new InvalidQueryException(
          String.format("unknown error while processing synonyms: %s", e.getMessage()));
    } catch (IndexOutOfBoundsException e) {
      // If a queryGraph analyzes to an empty string/no tokens, it explodes in an unfortunate way.
      // Wrap that explosion, and check that it is the message we're expecting - then throw a more
      // informative exception.
      if (StringUtils.startsWith(e.getMessage(), "Index -1 out")) {
        throw new InvalidQueryException(
            "query analyzed to zero tokens; query must contain non stop words");
      }
      throw new InvalidQueryException(
          String.format("unknown error processing synonyms: %s", e.getMessage()));
    }
  }
}
