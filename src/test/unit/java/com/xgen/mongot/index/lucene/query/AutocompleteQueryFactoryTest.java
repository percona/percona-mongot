package com.xgen.mongot.index.lucene.query;

import static org.mockito.Mockito.mock;

import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.index.IndexMetricsUpdater;
import com.xgen.mongot.index.analyzer.AnalyzerRegistry;
import com.xgen.mongot.index.analyzer.definition.CustomAnalyzerDefinition;
import com.xgen.mongot.index.analyzer.wrapper.LuceneAnalyzer;
import com.xgen.mongot.index.analyzer.wrapper.QueryAnalyzerWrapper;
import com.xgen.mongot.index.definition.AutocompleteFieldDefinition;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.lucene.query.context.SearchQueryFactoryContext;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.operators.AutocompleteOperator;
import com.xgen.mongot.index.version.IndexFormatVersion;
import com.xgen.testing.mongot.index.analyzer.AnalyzerRegistryBuilder;
import com.xgen.testing.mongot.index.analyzer.custom.TokenFilterDefinitionBuilder;
import com.xgen.testing.mongot.index.analyzer.custom.TokenizerDefinitionBuilder;
import com.xgen.testing.mongot.index.analyzer.definition.CustomAnalyzerDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.AutocompleteFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.lucene.synonym.SynonymRegistryBuilder;
import com.xgen.testing.mongot.index.query.operators.AutocompleteOperatorBuilder;
import com.xgen.testing.mongot.index.query.operators.FuzzyOptionBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.automaton.LevenshteinAutomata;
import org.junit.Assert;
import org.junit.Test;

public class AutocompleteQueryFactoryTest {

  @Test
  public void testSimple() throws InvalidQueryException {
    var factory = queryFactory(3, 10, true);
    var operator = OperatorBuilder.autocomplete().path("description").query("pizza").build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "pizza")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "pizza")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testTruncates() throws InvalidQueryException {
    var factory = queryFactory(2, 3, true);
    var operator = OperatorBuilder.autocomplete().path("description").query("pizza").build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "piz")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "pizza")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testKeepsExactWhenTooShort() throws InvalidQueryException {
    var factory = queryFactory(10, 20, true);
    var operator = OperatorBuilder.autocomplete().path("description").query("pizza").build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "pizza")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "pizza")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testStripsDiacritics() throws InvalidQueryException {
    var factory = queryFactory(2, 7, true);
    var operator = OperatorBuilder.autocomplete().path("description").query("Résumé").build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "resume")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "Résumé")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testIgnoresDiacriticsAlwaysLowersCase() throws InvalidQueryException {
    var factory = queryFactory(2, 7, false);
    var operator = OperatorBuilder.autocomplete().path("description").query("Résumé").build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "résumé")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "Résumé")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testFuzzyFoldDiacritics() throws InvalidQueryException {
    var factory = queryFactory(2, 7, true);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("Résumé")
            .fuzzy(FuzzyOptionBuilder.builder().maxEdits(1).maxExpansions(100).build())
            .build();

    var expected =
        new BooleanQuery.Builder()
            .add(
                new AutomatonQuery(
                    new Term("$type:autocomplete/description", "resume"),
                    new LevenshteinAutomata("resume", true).toAutomaton(1)),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "Résumé")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testFuzzyDoNotFoldDiacritics() throws InvalidQueryException {
    var factory = queryFactory(2, 7, false);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("Résumé")
            .fuzzy(FuzzyOptionBuilder.builder().maxEdits(1).maxExpansions(100).build())
            .build();

    var expected =
        new BooleanQuery.Builder()
            .add(
                new AutomatonQuery(
                    new Term("$type:autocomplete/description", "résumé"),
                    new LevenshteinAutomata("résumé", true).toAutomaton(1)),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "Résumé")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testFuzzyPrefixLength() throws InvalidQueryException {
    var factory = queryFactory(2, 7, false);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("Résumé")
            .fuzzy(FuzzyOptionBuilder.builder().maxEdits(1).prefixLength(3).build())
            .build();

    var expected =
        new BooleanQuery.Builder()
            .add(
                new AutomatonQuery(
                    new Term("$type:autocomplete/description", "résumé"),
                    new LevenshteinAutomata("umé", true).toAutomaton(1, "rés")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "Résumé")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test(expected = InvalidQueryException.class)
  public void testThrowsWhenAutocompleteFieldNotDefined() throws InvalidQueryException {
    SearchIndexDefinition definition = SearchIndexDefinitionBuilder.VALID_INDEX;
    QueryAnalyzerWrapper analyzer =
        LuceneAnalyzer.queryAnalyzer(definition, AnalyzerRegistryBuilder.empty());
    var factory =
        new AutocompleteQueryFactory(
            new SearchQueryFactoryContext(
                AnalyzerRegistryBuilder.empty(),
                analyzer,
                definition.createFieldDefinitionResolver(IndexFormatVersion.CURRENT),
                SynonymRegistryBuilder.empty(),
                new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory()),
                FeatureFlags.getDefault()));

    var operator = OperatorBuilder.autocomplete().path("description").query("pizza").build();

    test(factory, operator, new MatchAllDocsQuery());
  }

  @Test(expected = InvalidQueryException.class)
  public void testThrowsWhenFuzzyPrefixGtMaxGrams() throws InvalidQueryException {
    var factory = queryFactory(2, 5, true);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("pizza")
            .fuzzy(AutocompleteOperatorBuilder.fuzzyBuilder().prefixLength(10).build())
            .build();

    test(factory, operator, new MatchAllDocsQuery());
  }

  @Test
  public void testExpandsTermsDefault() throws InvalidQueryException {
    var factory = queryFactory(10, 20, true);
    var operator = OperatorBuilder.autocomplete().path("description").query("pizza parlor").build();

    Function<String, BooleanClause> autocompleteClauseGenerator =
        (String text) ->
            new BooleanClause(
                new TermQuery(new Term("$type:autocomplete/description", text)),
                BooleanClause.Occur.SHOULD);
    Function<String, BooleanClause> exactMatchClauseGenerator =
        (String text) ->
            new BooleanClause(
                new TermQuery(new Term("$type:string/description", text)),
                BooleanClause.Occur.SHOULD);

    var expected =
        new BooleanQuery.Builder()
            .add(
                new BooleanQuery.Builder()
                    .add(autocompleteClauseGenerator.apply("pizza"))
                    .add(autocompleteClauseGenerator.apply("parlor"))
                    .add(autocompleteClauseGenerator.apply("pizza parlor"))
                    .build(),
                BooleanClause.Occur.MUST)
            .add(exactMatchClauseGenerator.apply("pizza parlor"))
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testDoesNotExpandTermsWhenConfigured() throws InvalidQueryException {
    var factory = queryFactory(10, 20, true);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("pizza parlor")
            .tokenOrder(AutocompleteOperator.TokenOrder.SEQUENTIAL)
            .build();
    var expected =
        new BooleanQuery.Builder()
            .add(
                new TermQuery(new Term("$type:autocomplete/description", "pizza parlor")),
                BooleanClause.Occur.MUST)
            .add(
                new TermQuery(new Term("$type:string/description", "pizza parlor")),
                BooleanClause.Occur.SHOULD)
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  @Test
  public void testExpandsFuzzyTermsDefault() throws InvalidQueryException {
    var factory = queryFactory(10, 20, true);
    var operator =
        OperatorBuilder.autocomplete()
            .path("description")
            .query("pizza parlor")
            .fuzzy(FuzzyOptionBuilder.builder().prefixLength(1).maxEdits(2).build())
            .build();

    Function<String, BooleanClause> autocompleteClauseGenerator =
        (String text) ->
            new BooleanClause(
                new AutomatonQuery(
                    new Term("$type:autocomplete/description", text),
                    new LevenshteinAutomata(text.substring(1), true)
                        .toAutomaton(2, text.substring(0, 1))),
                BooleanClause.Occur.SHOULD);
    Function<String, BooleanClause> exactMatchClauseGenerator =
        (String text) ->
            new BooleanClause(
                new TermQuery(new Term("$type:string/description", text)),
                BooleanClause.Occur.SHOULD);

    var expected =
        new BooleanQuery.Builder()
            .add(
                new BooleanQuery.Builder()
                    .add(autocompleteClauseGenerator.apply("pizza"))
                    .add(autocompleteClauseGenerator.apply("parlor"))
                    .add(autocompleteClauseGenerator.apply("pizza parlor"))
                    .build(),
                BooleanClause.Occur.MUST)
            .add(exactMatchClauseGenerator.apply("pizza parlor"))
            .setMinimumNumberShouldMatch(0)
            .build();

    test(factory, operator, expected);
  }

  private void test(AutocompleteQueryFactory factory, AutocompleteOperator operator, Query expected)
      throws InvalidQueryException {
    var actual =
        factory.fromCompletion(
            operator, SingleQueryContext.createQueryRoot(mock(LeafReader.class)));

    Assert.assertEquals("Autocomplete lucene query: ", expected, actual);
  }

  @Test
  public void testThrowsWhenAllTokensAreStopwords() throws Exception {
    var factory = queryFactoryWithStopwordAnalyzer(4, 10, true, List.of("el", "la", "los", "las"));
    var operator = OperatorBuilder.autocomplete().path("title").query("El").build();

    var exception =
        Assert.assertThrows(
            InvalidQueryException.class,
            () ->
                factory.fromCompletion(
                    operator, SingleQueryContext.createQueryRoot(mock(LeafReader.class))));

    Assert.assertTrue(
        "exception message should mention stop word filters",
        exception.getMessage().contains("stop word filters"));
  }

  @Test
  public void testThrowsWhenAllTokensAreStopwordsSequential() throws Exception {
    var factory = queryFactoryWithStopwordAnalyzer(4, 10, true, List.of("el", "la", "los", "las"));
    var operator =
        OperatorBuilder.autocomplete()
            .path("title")
            .query("El")
            .tokenOrder(AutocompleteOperator.TokenOrder.SEQUENTIAL)
            .build();

    var exception =
        Assert.assertThrows(
            InvalidQueryException.class,
            () ->
                factory.fromCompletion(
                    operator, SingleQueryContext.createQueryRoot(mock(LeafReader.class))));

    Assert.assertTrue(
        "exception message should mention stop word filters",
        exception.getMessage().contains("stop word filters"));
  }

  @Test
  public void testPartialStopwordRemovalStillProducesQuery() throws Exception {
    var factory = queryFactoryWithStopwordAnalyzer(4, 10, true, List.of("el", "la", "los", "las"));
    var operator = OperatorBuilder.autocomplete().path("title").query("El Pollo").build();

    var actual =
        factory.fromCompletion(
            operator, SingleQueryContext.createQueryRoot(mock(LeafReader.class)));

    Assert.assertNotNull("query should be produced when some tokens survive analysis", actual);
  }

  private static AutocompleteQueryFactory queryFactory(
      int minGrams, int maxGrams, boolean foldDiacritics) throws InvalidQueryException {
    var indexDefinition =
        SearchIndexDefinitionBuilder.builder()
            .defaultMetadata()
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .field(
                        "description",
                        FieldDefinitionBuilder.builder()
                            .autocomplete(
                                AutocompleteFieldDefinitionBuilder.builder()
                                    .minGrams(minGrams)
                                    .maxGrams(maxGrams)
                                    .foldDiacritics(foldDiacritics)
                                    .tokenizationStrategy(
                                        AutocompleteFieldDefinition.TokenizationStrategy.EDGE_GRAM)
                                    .build())
                            .build())
                    .build())
            .analyzerName("lucene.keyword")
            .build();
    return new AutocompleteQueryFactory(
        new SearchQueryFactoryContext(
            AnalyzerRegistryBuilder.empty(),
            LuceneAnalyzer.queryAnalyzer(indexDefinition, AnalyzerRegistryBuilder.empty()),
            indexDefinition.createFieldDefinitionResolver(IndexFormatVersion.CURRENT),
            SynonymRegistryBuilder.empty(),
            new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory()),
            FeatureFlags.getDefault()));
  }

  private static AutocompleteQueryFactory queryFactoryWithStopwordAnalyzer(
      int minGrams, int maxGrams, boolean foldDiacritics, List<String> stopwords) throws Exception {
    String analyzerName = "custom_stopwords";
    CustomAnalyzerDefinition customAnalyzerDef =
        CustomAnalyzerDefinitionBuilder.builder(
                analyzerName, TokenizerDefinitionBuilder.StandardTokenizer.builder().build())
            .tokenFilter(TokenFilterDefinitionBuilder.LowercaseTokenFilter.builder().build())
            .tokenFilter(TokenFilterDefinitionBuilder.AsciiFoldingTokenFilter.builder().build())
            .tokenFilter(
                TokenFilterDefinitionBuilder.StopwordTokenFilter.builder()
                    .tokens(stopwords)
                    .build())
            .build();

    AnalyzerRegistry registry = AnalyzerRegistry.factory().create(List.of(customAnalyzerDef), true);

    var indexDefinition =
        SearchIndexDefinitionBuilder.builder()
            .defaultMetadata()
            .mappings(
                DocumentFieldDefinitionBuilder.builder()
                    .field(
                        "title",
                        FieldDefinitionBuilder.builder()
                            .autocomplete(
                                AutocompleteFieldDefinitionBuilder.builder()
                                    .minGrams(minGrams)
                                    .maxGrams(maxGrams)
                                    .foldDiacritics(foldDiacritics)
                                    .tokenizationStrategy(
                                        AutocompleteFieldDefinition.TokenizationStrategy.N_GRAM)
                                    .analyzer(analyzerName)
                                    .build())
                            .build())
                    .build())
            .analyzers(List.of(customAnalyzerDef))
            .analyzerName(analyzerName)
            .build();

    return new AutocompleteQueryFactory(
        new SearchQueryFactoryContext(
            registry,
            LuceneAnalyzer.queryAnalyzer(indexDefinition, registry),
            indexDefinition.createFieldDefinitionResolver(IndexFormatVersion.CURRENT),
            SynonymRegistryBuilder.empty(),
            new IndexMetricsUpdater.QueryingMetricsUpdater(SearchIndex.mockMetricsFactory()),
            FeatureFlags.getDefault()));
  }
}
