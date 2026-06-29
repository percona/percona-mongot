package com.xgen.mongot.index.lucene.query.custom;

import static com.xgen.mongot.util.bson.FloatVector.OriginalType.NATIVE;

import com.xgen.mongot.util.bson.Vector;
import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BulkScorer;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.VectorUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ExactVectorSearchQueryTest {

  private static final String vectorFieldName = "vector";
  private static final String bitOrByteVectorFieldName = "bit_byte_vector";
  private static final String filterFieldName = "filter";
  private static final String matchFilterValue = "match";
  private static final BooleanQuery filterQuery =
      new BooleanQuery.Builder()
          .add(
              new ConstantScoreQuery(new TermQuery(new Term(filterFieldName, matchFilterValue))),
              BooleanClause.Occur.MUST)
          .build();

  private static IndexWriter writer;
  private static IndexReader reader;
  private static IndexSearcher searcher;

  @BeforeClass
  public static void setUpClass() throws IOException {
    Directory directory = new ByteBuffersDirectory();
    writer = new IndexWriter(directory, new IndexWriterConfig());

    Document document0 = new Document();
    document0.add(new KnnFloatVectorField(vectorFieldName, new float[] {1, 2, 3, 4}));
    document0.add(
        new KnnByteVectorField(
            bitOrByteVectorFieldName,
            new byte[] {(byte) 0x11, (byte) 0xC3, (byte) 0xF0, (byte) 0xFF}));
    document0.add(new StringField(filterFieldName, matchFilterValue, Field.Store.NO));
    writer.addDocument(document0);

    Document document1 = new Document();
    document1.add(new KnnFloatVectorField(vectorFieldName, new float[] {5, 6, 7, 8}));
    document1.add(
        new KnnByteVectorField(
            bitOrByteVectorFieldName,
            new byte[] {(byte) 0x2C, (byte) 0x0F, (byte) 0x23, (byte) 0x00}));
    document1.add(new StringField(filterFieldName, "no match", Field.Store.NO));
    writer.addDocument(document1);

    Document noVectorDocument = new Document();
    noVectorDocument.add(new StringField(filterFieldName, "no match", Field.Store.NO));
    writer.addDocument(noVectorDocument);

    writer.commit();

    reader = DirectoryReader.open(directory);
    searcher = new IndexSearcher(reader);
  }

  @AfterClass
  public static void tearDownClass() throws IOException {
    reader.close();
    writer.close();
  }

  @Test
  public void testSimpleQuerySearch() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName,
            Vector.fromFloats(target, NATIVE),
            similarityFunction,
            new FieldExistsQuery(vectorFieldName));

    ScoreDoc[] scoreDocs = searcher.search(query, new TopScoreDocCollectorManager(2, 2)).scoreDocs;
    Assert.assertEquals(2, scoreDocs.length);

    // document 1 has a higher dot product similarity score to target, so it will appear first
    float expectedScore1 = similarityFunction.compare(new float[] {5, 6, 7, 8}, target);
    Assert.assertEquals(1, scoreDocs[0].doc);
    Assert.assertEquals(expectedScore1, scoreDocs[0].score, 0.00001);

    float expectedScore0 = similarityFunction.compare(new float[] {1, 2, 3, 4}, target);
    Assert.assertEquals(0, scoreDocs[1].doc);
    Assert.assertEquals(expectedScore0, scoreDocs[1].score, 0.00001);
  }

  @Test
  public void testSimpleQueryExplanation() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName,
            Vector.fromFloats(target, NATIVE),
            similarityFunction,
            new FieldExistsQuery(vectorFieldName));

    // both documents match the filter (MatchAll) query
    Assert.assertTrue(searcher.explain(query, 0).isMatch());
    Assert.assertTrue(searcher.explain(query, 1).isMatch());
  }

  @Test
  public void testFilteredQuerySimple() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName, Vector.fromFloats(target, NATIVE), similarityFunction, filterQuery);

    ScoreDoc[] scoreDocs = searcher.search(query, new TopScoreDocCollectorManager(2, 2)).scoreDocs;
    Assert.assertEquals(1, scoreDocs.length);

    // Document 1 does not match query, so does not appear here
    float expectedScore0 = similarityFunction.compare(new float[] {1, 2, 3, 4}, target);
    Assert.assertEquals(0, scoreDocs[0].doc);
    Assert.assertEquals(expectedScore0, scoreDocs[0].score, 0.00001);
  }

  @Test
  public void testFilteredQueryExplanation() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName, Vector.fromFloats(target, NATIVE), similarityFunction, filterQuery);

    Assert.assertTrue(searcher.explain(query, 0).isMatch());
    Assert.assertFalse(searcher.explain(query, 1).isMatch());
  }

  @Test
  public void testScorer() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName,
            Vector.fromFloats(target, NATIVE),
            similarityFunction,
            new FieldExistsQuery(vectorFieldName));

    Weight weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1.0f);
    LeafReaderContext context = reader.getContext().leaves().get(0);

    ScorerSupplier supplier = weight.scorerSupplier(context);
    Scorer scorer = supplier.get(2L);
    Assert.assertEquals(ExactVectorSearchQuery.ExactVectorSearchScorer.class, scorer.getClass());

    DocIdSetIterator iter = scorer.iterator();
    iter.nextDoc();
    float expectedScore0 = similarityFunction.compare(new float[] {1, 2, 3, 4}, target);
    Assert.assertEquals(expectedScore0, scorer.score(), 0.00001);
    iter.nextDoc();
    float expectedScore1 = similarityFunction.compare(new float[] {5, 6, 7, 8}, target);
    Assert.assertEquals(expectedScore1, scorer.score(), 0.00001);
  }

  @Test
  public void testScorerWithByteVector() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
    byte[] target = new byte[] {(byte) 0x01, (byte) 0x1A, (byte) 0xFF, (byte) 0x00};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            bitOrByteVectorFieldName,
            Vector.fromBytes(target),
            similarityFunction,
            new FieldExistsQuery(bitOrByteVectorFieldName));

    Weight weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1.0f);
    LeafReaderContext context = reader.getContext().leaves().get(0);

    ScorerSupplier supplier = weight.scorerSupplier(context);
    Scorer scorer = supplier.get(2L);
    Assert.assertEquals(ExactVectorSearchQuery.ExactVectorSearchScorer.class, scorer.getClass());

    DocIdSetIterator iter = scorer.iterator();
    iter.nextDoc();
    float expectedScore0 =
        similarityFunction.compare(
            new byte[] {(byte) 0x11, (byte) 0xC3, (byte) 0xF0, (byte) 0xFF}, target);
    Assert.assertEquals(expectedScore0, scorer.score(), 0.00001);
    iter.nextDoc();
    float expectedScore1 =
        similarityFunction.compare(
            new byte[] {(byte) 0x2C, (byte) 0x0F, (byte) 0x23, (byte) 0x00}, target);
    Assert.assertEquals(expectedScore1, scorer.score(), 0.00001);
  }

  @Test
  public void testScorerWithBitVector() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.EUCLIDEAN;
    byte[] target = new byte[] {(byte) 0x01, (byte) 0x1A, (byte) 0xFF, (byte) 0x00};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            bitOrByteVectorFieldName,
            Vector.fromBits(target),
            similarityFunction,
            new FieldExistsQuery(bitOrByteVectorFieldName));

    Weight weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1.0f);
    LeafReaderContext context = reader.getContext().leaves().get(0);

    ScorerSupplier supplier = weight.scorerSupplier(context);
    Scorer scorer = supplier.get(2L);
    Assert.assertEquals(ExactVectorSearchQuery.ExactVectorSearchScorer.class, scorer.getClass());

    DocIdSetIterator iter = scorer.iterator();
    iter.nextDoc();
    float expectedScore0 =
        expectedSimilarityScoreForBitVectors(
            new byte[] {(byte) 0x11, (byte) 0xC3, (byte) 0xF0, (byte) 0xFF}, target);
    Assert.assertEquals(expectedScore0, scorer.score(), 0.00001);
    iter.nextDoc();
    float expectedScore1 =
        expectedSimilarityScoreForBitVectors(
            new byte[] {(byte) 0x2C, (byte) 0x0F, (byte) 0x23, (byte) 0x00}, target);
    Assert.assertEquals(expectedScore1, scorer.score(), 0.00001);
  }

  @Test
  public void testBulkScorer() throws IOException {
    VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.EUCLIDEAN;
    float[] target = new float[] {1, 1, 1, 1};
    ExactVectorSearchQuery query =
        new ExactVectorSearchQuery(
            vectorFieldName,
            Vector.fromFloats(target, NATIVE),
            similarityFunction,
            new FieldExistsQuery(vectorFieldName));

    Weight weight = searcher.createWeight(query, ScoreMode.COMPLETE, 1.0f);
    LeafReaderContext context = reader.getContext().leaves().get(0);

    TopScoreDocCollector collector = new TopScoreDocCollectorManager(2, 2).newCollector();
    collector.setWeight(weight);
    LeafCollector leafCollector = collector.getLeafCollector(context);

    BulkScorer bulkScorer = weight.bulkScorer(context);
    bulkScorer.score(leafCollector, null, 0, DocIdSetIterator.NO_MORE_DOCS);

    ScoreDoc[] scoreDocs = collector.topDocs().scoreDocs;
    Assert.assertEquals(2, scoreDocs.length);

    float expectedScore0 = similarityFunction.compare(new float[] {1, 2, 3, 4}, target);
    Assert.assertEquals(expectedScore0, scoreDocs[0].score, 0.00001);

    float expectedScore1 = similarityFunction.compare(new float[] {5, 6, 7, 8}, target);
    Assert.assertEquals(expectedScore1, scoreDocs[1].score, 0.00001);
  }

  private float expectedSimilarityScoreForBitVectors(byte[] v1, byte[] v2) {
    int xorBitCount = VectorUtil.xorBitCount(v1, v2);
    int dimensions = v1.length * 8;
    return (dimensions - xorBitCount) / (float) dimensions;
  }
}
