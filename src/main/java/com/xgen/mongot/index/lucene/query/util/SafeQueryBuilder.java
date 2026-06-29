package com.xgen.mongot.index.lucene.query.util;

import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.QueryBuilder;

/**
 * This is a null safe adapter for lucene's QueryBuilder. Lucene's QueryBuilder may return nulls
 * when queryText analysis produces no tokens.
 */
public class SafeQueryBuilder {

  private final QueryBuilder wrapped;

  public SafeQueryBuilder(Analyzer analyzer) {
    this.wrapped = new QueryBuilder(analyzer);
  }

  public static SafeQueryBuilder createSynonymsQueryBuilder(Analyzer analyzer) {
    var queryBuilder = new SafeQueryBuilder(analyzer);
    queryBuilder.wrapped.setAutoGenerateMultiTermSynonymsPhraseQuery(true);
    return queryBuilder;
  }

  public Optional<Query> createBooleanQuery(String field, String queryText) {
    return Optional.ofNullable(this.wrapped.createBooleanQuery(field, queryText));
  }

  public Optional<Query> createBooleanQuery(
      String field, String queryText, BooleanClause.Occur occur) {
    return Optional.ofNullable(this.wrapped.createBooleanQuery(field, queryText, occur));
  }

  public Optional<Query> createPhraseQuery(String field, String queryText, int phraseSlop) {
    return Optional.ofNullable(this.wrapped.createPhraseQuery(field, queryText, phraseSlop));
  }

  /**
   * Checks whether a query returned by {@link SafeQueryBuilder} has top-level or underlying {@link
   * PhraseQuery} or {@link MultiPhraseQuery}. This method may need to be updated if the {@link
   * QueryBuilder} API changes.
   */
  public static boolean containsPhraseQuery(Optional<Query> query) {
    return query
        .map(
            value ->
                switch (value) {
                  case BooleanQuery booleanQuery ->
                      booleanQuery.clauses().stream()
                          .anyMatch(clause -> containsPhraseQuery(Optional.of(clause.query())));
                  case PhraseQuery phraseQuery -> true;
                  case MultiPhraseQuery multiPhraseQuery -> true;
                  case DisjunctionMaxQuery disjunctionMaxQuery ->
                      disjunctionMaxQuery.getDisjuncts().stream()
                          .anyMatch(q -> containsPhraseQuery(Optional.of(q)));
                  default -> false;
                })
        .orElse(false);
  }
}
