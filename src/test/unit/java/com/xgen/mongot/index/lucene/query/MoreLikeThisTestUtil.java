package com.xgen.mongot.index.lucene.query;

import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.util.FieldPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;

public class MoreLikeThisTestUtil {
  private static String stringField(String fieldName) {
    return FieldName.TypeField.STRING.getLuceneFieldName(
        FieldPath.parse(fieldName), Optional.empty());
  }

  private static String multiField(String fieldName, String multiName) {
    return FieldName.MultiField.getLuceneFieldName(
        FieldPath.parse(fieldName), multiName, Optional.empty());
  }

  static Map<String, String> withLuceneMultiField(String fieldName, String multiName, String text) {
    Map<String, String> ret = new HashMap<>();
    ret.put(multiField(fieldName, multiName), text);
    return ret;
  }

  static Map<String, String> withLuceneStringFields(Map<String, String> doc) {
    Map<String, String> ret = new HashMap<>();
    for (var field : doc.entrySet()) {
      ret.put(stringField(field.getKey()), field.getValue());
    }
    return ret;
  }

  static TermQuery termQuery(String fieldName, String value) {
    return new TermQuery(new Term(stringField(fieldName), value));
  }

  static TermQuery termQueryForMulti(String fieldName, String multiName, String value) {
    return new TermQuery(new Term(multiField(fieldName, multiName), value));
  }

  static BooleanQuery buildBooleanQuery(TermQuery... termQueries) {
    var builder = new BooleanQuery.Builder();
    for (TermQuery termQuery : termQueries) {
      builder.add(new BooleanClause(termQuery, BooleanClause.Occur.SHOULD));
    }
    return builder.build();
  }

  private static List<Term> getTermsInSortedOrder(BooleanQuery bq) {
    return bq.clauses().stream()
        .map(
            clause -> {
              Assert.assertEquals(BooleanClause.Occur.SHOULD, clause.occur());
              var tq = (TermQuery) clause.query();
              return tq.getTerm();
            })
        .sorted() // Terms are sortable.
        .collect(Collectors.toList());
  }

  static void compareBQs(BooleanQuery expected, Query actual) {
    BooleanQuery actualBq = (BooleanQuery) actual;
    var expectedTerms = getTermsInSortedOrder(expected);
    var actualTerms = getTermsInSortedOrder(actualBq);

    if (!expectedTerms.equals(actualTerms)) {
      Assert.fail(
          "Different boolean queries:\n  Expected:" + expectedTerms + "\n  Actual:" + actualTerms);
    }
  }
}
