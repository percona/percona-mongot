package com.xgen.mongot.index.lucene.query.util;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.Assert;
import org.junit.Test;

public class RescoringTest {

  private static final Map<String, DoubleValuesSource> ABC_FIELD_MAPPINGS =
      mappingsFrom("a", "b", "c");

  @Test
  public void testValuesMapAsExpected() {
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("a", DoubleValuesSource.constant(2));
    bindings.add("b", DoubleValuesSource.constant(3));

    Expression expression = expressionFrom("a + b");

    Query expected =
        new FunctionScoreQuery(
            new MatchNoDocsQuery("no op query"), expression.getDoubleValuesSource(bindings));

    Query result =
        Rescoring.rewriteScore(
            new MatchNoDocsQuery("no op query"),
            expression,
            Map.of("a", DoubleValuesSource.constant(2), "b", DoubleValuesSource.constant(3)));

    Assert.assertEquals("result should be same as expected", expected, result);
  }

  @Test
  public void testBasicArithmetic() throws Exception {
    Document doc1 = abcDocument(1, 1, 2, 3); // 9
    Document doc2 = abcDocument(2, 1, 1, 1); // 2
    Document doc3 = abcDocument(3, 10, -10, 1000000); // 0
    Document doc4 = abcDocument(4, -1, -1, 1); // 0 -- scores will not go negative
    List<Document> documents = List.of(doc1, doc2, doc3, doc4);

    Expression expr = expressionFrom("(a + b) * c");

    try (Directory directory = new ByteBuffersDirectory()) {
      writeDocs(directory, documents);

      IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
      Query query = Rescoring.rewriteScore(new MatchAllDocsQuery(), expr, ABC_FIELD_MAPPINGS);
      TopDocs topDocs = searcher.search(query, 10);
      IntUnaryOperator idGetter = idForTopDocsIdx(searcher, topDocs);

      Assert.assertEquals("should return four docs", 4L, topDocs.totalHits.value());

      Assert.assertEquals("first document has _id=1", 1, idGetter.applyAsInt(0));
      Assert.assertEquals("doc1 should score to value of six", 9, topDocs.scoreDocs[0].score, 0);

      Assert.assertEquals("second document has _id=2", 2, idGetter.applyAsInt(1));
      Assert.assertEquals("doc2 should score to value of one", 2, topDocs.scoreDocs[1].score, 0);

      Assert.assertEquals("third document has _id=3", 3, idGetter.applyAsInt(2));
      Assert.assertEquals("doc3 should score to value of zero", 0, topDocs.scoreDocs[2].score, 0);

      Assert.assertEquals("fourth document has _id=4", 4, idGetter.applyAsInt(3));
      Assert.assertEquals("doc4 should score to value of zero", 0, topDocs.scoreDocs[3].score, 0);
    }
  }

  @Test
  public void testRepresentativeExpression() throws Exception {
    Document doc1 = abcDocument(1, 95, 100, 10); // .6666
    Document doc2 = abcDocument(1, 10, 100, 10); // .1
    Document doc3 = abcDocument(1, 1000L, 100, 98765); // 0

    Expression expr = expressionFrom("c/(c + (b - a))"); // a = value, b = origin, c = pivot

    test(doc1, expr, ABC_FIELD_MAPPINGS, 10.0 / (10.0 + (100.0 - 95.0)));
    test(doc2, expr, ABC_FIELD_MAPPINGS, 10.0 / (10.0 + (100.0 - 10.0)));
    test(doc3, expr, ABC_FIELD_MAPPINGS, 98765.0 / (98765.0 + (100.0 - 1000.0)));
  }

  @Test
  public void testJsFunctions() throws Exception {
    Document doc1 = abcDocument(1, 95, 100, 10); // .6666
    Document doc2 = abcDocument(1, 10, 100, 10); // .1
    Document doc3 = abcDocument(1, 1000L, 100, 98765); // 0

    Expression absExpr = expressionFrom("c/(c + abs(b - a))"); // a = value, b = origin, c = pivot

    test(doc1, absExpr, ABC_FIELD_MAPPINGS, 10.0 / (10.0 + Math.abs(100.0 - 95.0)));
    test(doc2, absExpr, ABC_FIELD_MAPPINGS, 10.0 / (10.0 + Math.abs(100.0 - 10.0)));
    test(doc3, absExpr, ABC_FIELD_MAPPINGS, 98765.0 / (98765.0 + Math.abs(100.0 - 1000.0)));

    Document doc4 = abcDocument(1, 3, 4, 2); // 5
    Document doc5 = abcDocument(1, 5, 12, 2); // 13
    Document doc6 = abcDocument(1, 1, 1, 2); // sqrt(2)

    Expression sqrtExp = expressionFrom("sqrt(pow(a, c) + pow(b, c))");

    test(doc4, sqrtExp, ABC_FIELD_MAPPINGS, 5);
    test(doc5, sqrtExp, ABC_FIELD_MAPPINGS, 13);
    test(doc6, sqrtExp, ABC_FIELD_MAPPINGS, Math.sqrt(2));
  }

  @Test
  public void testNearExactly() throws Exception {
    long pivot = 10L;
    long origin = 100L;

    Map<String, DoubleValuesSource> mappings =
        Map.of(
            "pivot",
            DoubleValuesSource.constant(pivot),
            "origin",
            DoubleValuesSource.constant(origin),
            "value",
            DoubleValuesSource.fromLongField("size"));

    Value value = new Value("size", 95);
    Document doc1 = documentFrom(1, value);

    Expression near = expressionFrom("pivot / (pivot + abs(origin - value))");

    test(doc1, near, mappings, 2.0 / 3.0);
  }

  private static void test(
      Document document,
      Expression expression,
      Map<String, DoubleValuesSource> mappings,
      double expectedScore)
      throws Exception {
    try (Directory directory = new ByteBuffersDirectory()) {
      writeDocs(directory, List.of(document));

      IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(directory));
      Query query = Rescoring.rewriteScore(new MatchAllDocsQuery(), expression, mappings);
      TopDocs topDocs = searcher.search(query, 10);

      Assert.assertEquals("should return one document", 1L, topDocs.totalHits.value());
      Assert.assertEquals(
          "document should have expected score", expectedScore, topDocs.scoreDocs[0].score, 1E-6);
    }
  }

  private IntUnaryOperator idForTopDocsIdx(IndexSearcher searcher, TopDocs topDocs) {
    return idx -> getId(searcher, topDocs, idx);
  }

  private static Integer getId(IndexSearcher searcher, TopDocs topDocs, int docIdx) {
    try {
      return searcher
          .storedFields()
          .document(topDocs.scoreDocs[docIdx].doc, Set.of("_id"))
          .getField("_id")
          .numericValue()
          .intValue();
    } catch (Exception e) {
      Assert.fail("searcher should not throw when finding _id");
      return 0;
    }
  }

  private static void writeDocs(Directory directory, List<Document> documents) throws Exception {
    try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
      for (Document document : documents) {
        writer.addDocument(document);
      }
      writer.commit();
    }
  }

  private static Expression expressionFrom(String exp) {
    try {
      return JavascriptCompiler.compile(exp);
    } catch (ParseException e) {
      throw new AssertionError("simple expression did not compile", e);
    }
  }

  static class Value {
    private final String path;
    private final long val;

    Value(String path, long val) {
      this.path = path;
      this.val = val;
    }

    LongPoint longPt() {
      return new LongPoint(this.path, this.val);
    }

    NumericDocValuesField docsField() {
      return new NumericDocValuesField(this.path, this.val);
    }
  }

  private static Document documentFrom(long id, Value... values) {
    Document document = new Document();
    for (Value val : values) {
      document.add(val.longPt());
      document.add(val.docsField());
    }
    document.add(new StoredField("_id", id));
    return document;
  }

  private static Document abcDocument(int id, long a, long b, long c) {
    return documentFrom(id, new Value("a", a), new Value("b", b), new Value("c", c));
  }

  private static Map<String, DoubleValuesSource> mappingsFrom(String... variables) {
    Map<String, DoubleValuesSource> mappings = new HashMap<>();

    for (String var : variables) {
      mappings.put(var, DoubleValuesSource.fromLongField(var));
    }
    return mappings;
  }
}
