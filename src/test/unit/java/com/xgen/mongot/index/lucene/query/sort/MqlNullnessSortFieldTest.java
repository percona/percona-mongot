package com.xgen.mongot.index.lucene.query.sort;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.xgen.mongot.index.query.sort.MongotSortField;
import com.xgen.mongot.index.query.sort.NullEmptySortPosition;
import com.xgen.mongot.index.query.sort.SortOrder;
import com.xgen.mongot.index.query.sort.UserFieldSortOptions;
import com.xgen.mongot.util.BsonUtils;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.util.RandomSegmentingIndexWriter;
import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.bson.BsonInt64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link MqlNullnessSortField}, which wraps the nullness sort comparator in {@link
 * com.xgen.mongot.index.lucene.query.sort.comparator.FieldComparatorBsonWrapper} so that
 * FieldDoc.fields[] produces BsonInt64 instead of raw Long.
 */
public class MqlNullnessSortFieldTest {

  private Directory directory;
  private IndexSearcher searcher;

  private static final String NULLNESS_FIELD = "$meta/nullness/field";
  private static final long NULLNESS_FIELD_PRESENT = 0L;

  private static Document createPresentDoc() {
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField(NULLNESS_FIELD, NULLNESS_FIELD_PRESENT));
    return doc;
  }

  private static Document createMissingDoc() {
    return new Document();
  }

  @Before
  public void setup() throws IOException {
    this.directory = new ByteBuffersDirectory();

    ImmutableList<Document> docs =
        ImmutableList.of(
            createPresentDoc(), createPresentDoc(), createMissingDoc(), createMissingDoc());

    try (var writer = new RandomSegmentingIndexWriter(this.directory)) {
      writer.addDocuments(docs);
    }

    DirectoryReader reader = DirectoryReader.open(this.directory);
    this.searcher = new IndexSearcher(reader);
  }

  @After
  public void cleanup() throws IOException {
    this.searcher.getIndexReader().close();
    this.directory.close();
  }

  @Test
  public void sortAscNullsLowest() throws IOException {
    SortField sortField = createNullnessSortField(UserFieldSortOptions.DEFAULT_ASC);
    Object[] values = runSortQuery(sortField);

    assertThat(values)
        .asList()
        .containsExactly(BsonUtils.MIN_KEY, BsonUtils.MIN_KEY, new BsonInt64(0L), new BsonInt64(0L))
        .inOrder();
  }

  @Test
  public void sortAscNullsHighest() throws IOException {
    SortField sortField =
        createNullnessSortField(
            new UserFieldSortOptions(SortOrder.ASC, NullEmptySortPosition.HIGHEST));
    Object[] values = runSortQuery(sortField);

    assertThat(values)
        .asList()
        .containsExactly(new BsonInt64(0L), new BsonInt64(0L), BsonUtils.MAX_KEY, BsonUtils.MAX_KEY)
        .inOrder();
  }

  @Test
  public void sortAscSearchAfterNullsLowest() throws IOException {
    SortField sortField = createNullnessSortField(UserFieldSortOptions.DEFAULT_ASC);
    Query query = new MatchAllDocsQuery();
    Sort sort = new Sort(sortField);

    TopDocs topDocs = this.searcher.search(query, 2, sort);

    Object[] values = Arrays.stream(topDocs.scoreDocs).map(s -> ((FieldDoc) s).fields[0]).toArray();
    assertThat(values).asList().containsExactly(BsonUtils.MIN_KEY, BsonUtils.MIN_KEY).inOrder();

    TopDocs afterDocs =
        this.searcher.searchAfter(
            topDocs.scoreDocs[topDocs.scoreDocs.length - 1], query, Integer.MAX_VALUE, sort, false);

    Object[] afterValues =
        Arrays.stream(afterDocs.scoreDocs).map(s -> ((FieldDoc) s).fields[0]).toArray();

    assertThat(afterValues)
        .asList()
        .containsExactly(new BsonInt64(0L), new BsonInt64(0L))
        .inOrder();
  }

  @Test
  public void sortAscSearchAfterNullsHighest() throws IOException {
    SortField sortField =
        createNullnessSortField(
            new UserFieldSortOptions(SortOrder.ASC, NullEmptySortPosition.HIGHEST));
    Query query = new MatchAllDocsQuery();
    Sort sort = new Sort(sortField);

    TopDocs topDocs = this.searcher.search(query, 2, sort);

    Object[] values = Arrays.stream(topDocs.scoreDocs).map(s -> ((FieldDoc) s).fields[0]).toArray();
    assertThat(values).asList().containsExactly(new BsonInt64(0L), new BsonInt64(0L)).inOrder();

    TopDocs afterDocs =
        this.searcher.searchAfter(
            topDocs.scoreDocs[topDocs.scoreDocs.length - 1], query, Integer.MAX_VALUE, sort, false);

    Object[] afterValues =
        Arrays.stream(afterDocs.scoreDocs).map(s -> ((FieldDoc) s).fields[0]).toArray();

    assertThat(afterValues)
        .asList()
        .containsExactly(BsonUtils.MAX_KEY, BsonUtils.MAX_KEY)
        .inOrder();
  }

  @Test
  public void fieldDocProducesBsonInt64NotRawLong() throws IOException {
    SortField sortField = createNullnessSortField(UserFieldSortOptions.DEFAULT_ASC);
    Query query = new MatchAllDocsQuery();
    Sort sort = new Sort(sortField);

    TopDocs topDocs = this.searcher.search(query, Integer.MAX_VALUE, sort);

    for (var scoreDoc : topDocs.scoreDocs) {
      Object field = ((FieldDoc) scoreDoc).fields[0];
      assertThat(field).isNotInstanceOf(Long.class);
      assertThat(field).isInstanceOf(org.bson.BsonValue.class);
    }
  }

  @Test
  public void equalsAcrossSerializationBoundary() throws IOException {
    SortField original = createNullnessSortField(UserFieldSortOptions.DEFAULT_ASC);
    assertThat(original).isInstanceOf(MqlNullnessSortField.class);

    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig();
      config.setIndexSort(new Sort(original));
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        Document doc = new Document();
        doc.add(new SortedNumericDocValuesField(NULLNESS_FIELD, NULLNESS_FIELD_PRESENT));
        writer.addDocument(doc);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Sort reloaded = reader.leaves().get(0).reader().getMetaData().sort();
        SortField deserialized = reloaded.getSort()[0];

        assertThat(deserialized).isNotInstanceOf(MqlNullnessSortField.class);
        assertThat(deserialized).isInstanceOf(SortedNumericSortField.class);

        assertThat(original.equals(deserialized)).isTrue();
      }
    }
  }

  @Test
  public void equalsAcrossSerializationBoundary_sortedSetSortField() throws IOException {
    MongotSortField mongotSortField =
        new MongotSortField(FieldPath.newRoot("name"), UserFieldSortOptions.DEFAULT_ASC);
    SortField original =
        MqlSortedSetSortField.stringSort(
            com.xgen.mongot.index.lucene.field.FieldName.TypeField.TOKEN,
            mongotSortField,
            true,
            java.util.Optional.empty(),
            true);
    assertThat(original).isInstanceOf(MqlSortedSetSortField.class);

    String luceneFieldName =
        com.xgen.mongot.index.lucene.field.FieldName.TypeField.TOKEN.getLuceneFieldName(
            FieldPath.newRoot("name"), java.util.Optional.empty());

    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig config = new IndexWriterConfig();
      config.setIndexSort(new Sort(original));
      try (IndexWriter writer = new IndexWriter(dir, config)) {
        Document doc = new Document();
        doc.add(
            new org.apache.lucene.document.SortedSetDocValuesField(
                luceneFieldName, new org.apache.lucene.util.BytesRef("hello")));
        writer.addDocument(doc);
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Sort reloaded = reader.leaves().get(0).reader().getMetaData().sort();
        SortField deserialized = reloaded.getSort()[0];

        assertThat(deserialized).isNotInstanceOf(MqlSortedSetSortField.class);
        assertThat(deserialized).isInstanceOf(org.apache.lucene.search.SortedSetSortField.class);

        assertThat(original.equals(deserialized)).isTrue();
      }
    }
  }

  private static SortField createNullnessSortField(UserFieldSortOptions options) {
    FieldPath nullnessPath = FieldPath.newRoot(NULLNESS_FIELD);
    MongotSortField mongotSortField = new MongotSortField(nullnessPath, options);
    return LuceneSortFactory.createNullnessSortField(mongotSortField);
  }

  private Object[] runSortQuery(SortField sortField) throws IOException {
    Query query = new MatchAllDocsQuery();
    Sort sort = new Sort(sortField);

    TopDocs topDocs = this.searcher.search(query, Integer.MAX_VALUE, sort);

    return Arrays.stream(topDocs.scoreDocs).map(s -> ((FieldDoc) s).fields[0]).toArray();
  }
}
