package com.xgen.mongot.index.lucene.util;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.junit.Assert;
import org.junit.Test;

public class LuceneDocumentIdEncoderTest {

  private static final BsonValue TEST_ID = new BsonInt32(13);

  private static final byte[] EXPECTED_ENCODED_BYTES = {
    14, 0, 0, 0, 16, 95, 105, 100, 0, 13, 0, 0, 0, 0
  };

  @Test
  public void testEncodeDocumentId() {
    byte[] result = LuceneDocumentIdEncoder.encodeDocumentId(TEST_ID);
    Assert.assertArrayEquals(EXPECTED_ENCODED_BYTES, result);
  }

  @Test
  public void testDecodeDocumentId() {
    BsonValue decoded = LuceneDocumentIdEncoder.decodeDocumentId(EXPECTED_ENCODED_BYTES);
    Assert.assertTrue("decoded document had invalid document _id type", decoded.isInt32());
    Assert.assertEquals(
        "decoded document had invalid document _id",
        TEST_ID.asInt32().getValue(),
        decoded.asInt32().getValue());
  }

  @Test
  public void testDocumentIdTermAndFieldBytesMatch() {
    Term term = LuceneDocumentIdEncoder.documentIdTerm(EXPECTED_ENCODED_BYTES);
    IndexableField field = LuceneDocumentIdEncoder.documentIdField(EXPECTED_ENCODED_BYTES);

    Assert.assertTrue(term.bytes().bytesEquals(field.binaryValue()));
  }

  @Test
  public void testDocumentIdTermAndFieldFieldsMatch() {
    Term term = LuceneDocumentIdEncoder.documentIdTerm(EXPECTED_ENCODED_BYTES);
    IndexableField field = LuceneDocumentIdEncoder.documentIdField(EXPECTED_ENCODED_BYTES);

    Assert.assertEquals(term.field(), field.name());
  }

  @Test
  public void testDocumentIdFromLuceneDocument() {
    int expectedId = 13;
    byte[] idBytes = LuceneDocumentIdEncoder.encodeDocumentId(new BsonInt32(expectedId));

    Document doc = new Document();
    doc.add(LuceneDocumentIdEncoder.documentIdField(idBytes));

    BsonValue documentId = LuceneDocumentIdEncoder.documentIdFromLuceneDocument(doc);
    Assert.assertTrue("document had invalid document _id type", documentId.isInt32());
    Assert.assertEquals(
        "document had invalid document _id", expectedId, documentId.asInt32().getValue());
  }

  /**
   * Ensures that the Term from LuceneIndex.documentIdTerm can be used to search and find a document
   * that had the field produced by LuceneIndex.documentIdField.
   */
  @Test
  public void testDocumentIdQueryTermMatches() throws Exception {
    try (Directory directory = new ByteBuffersDirectory()) {
      // First add two documents with different $meta._id fields to the index.
      int bsonDoc1Id = 1;
      Document doc1 = new Document();
      byte[] id1 = LuceneDocumentIdEncoder.encodeDocumentId(new BsonInt32(bsonDoc1Id));
      doc1.add(LuceneDocumentIdEncoder.documentIdField(id1));

      BsonDocument bsonDoc2 = new BsonDocument().append("_id", new BsonInt32(2));
      Document doc2 = new Document();
      byte[] id2 = LuceneDocumentIdEncoder.encodeDocumentId(bsonDoc2);
      doc2.add(LuceneDocumentIdEncoder.documentIdField(id2));

      try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig())) {
        writer.addDocument(doc1);
        writer.addDocument(doc2);
        writer.commit();
      }

      // Then try to search for the first document.
      IndexReader reader = DirectoryReader.open(directory);
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query = new TermQuery(LuceneDocumentIdEncoder.documentIdTerm(id1));

      // Ensure that we found one document.
      TopDocs topDocs = searcher.search(query, 10);
      Assert.assertEquals(
          "expected to only find one document with the matching _id",
          1L,
          topDocs.totalHits.value());

      // Ensure that the document we found was the intended document.
      Document returned = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
      BsonValue documentId = LuceneDocumentIdEncoder.documentIdFromLuceneDocument(returned);
      Assert.assertTrue("document had invalid document _id type", documentId.isInt32());
      Assert.assertEquals(
          "document had invalid document _id", bsonDoc1Id, documentId.asInt32().getValue());
    }
  }
}
