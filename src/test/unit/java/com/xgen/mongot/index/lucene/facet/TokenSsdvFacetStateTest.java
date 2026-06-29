package com.xgen.mongot.index.lucene.facet;

import static com.google.common.truth.Truth.assertThat;

import com.google.errorprone.annotations.Var;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;

public class TokenSsdvFacetStateTest {

  @Test
  public void testSimple() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 1, 1, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      var facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();

      var ordRange = facetState.get().getOrdRange("foo");
      assertThat(ordRange.start()).isEqualTo(0);
      assertThat(ordRange.end()).isEqualTo(0);
    }
  }

  @Test
  public void testMultipleCardinality() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      var facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();

      var ordRange = facetState.get().getOrdRange("foo");
      assertThat(ordRange.start()).isEqualTo(0);
      assertThat(ordRange.end()).isEqualTo(2);
    }
  }

  @Test
  public void testSingleSegment() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      Optional<TokenSsdvFacetState> facetState =
          TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      SortedSetDocValues docValues = facetState.get().getDocValues();
      assertThat(docValues.getValueCount()).isEqualTo(3);
      // single segment index should not create ordinal map
      assertThat(facetState.get().ordinalMap).isEmpty();
    }
  }

  @Test
  public void testFieldNotIndexed() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      writer.addDocument(new Document());
      writer.commit();
      IndexReader reader = DirectoryReader.open(directory);
      Optional<TokenSsdvFacetState> facetState =
          TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isEmpty();
    }
  }

  @Test
  public void testMultipleSegments() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      // 3 commits -> 3 segments
      addAndCommitDocuments(writer, 3, 3, Optional.empty());
      addAndCommitDocuments(writer, 5, 5, Optional.of(3));
      addAndCommitDocuments(writer, 2, 2, Optional.of(8));

      IndexReader reader = DirectoryReader.open(directory);
      Optional<TokenSsdvFacetState> facetState =
          TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();

      SortedSetDocValues docValues = facetState.get().getDocValues();
      assertThat(docValues.getValueCount()).isEqualTo(10);
      // multiple segments should create MultiSortedSetDocValues and global ordinal map
      assertThat(docValues instanceof MultiDocValues.MultiSortedSetDocValues).isTrue();
      assertThat(((MultiDocValues.MultiSortedSetDocValues) docValues).values.length).isEqualTo(3);
      assertThat(facetState.get().ordinalMap).isPresent();
      assertThat(facetState.get().getOrdRange("foo").end()).isEqualTo(9);
    }
  }

  @Test
  public void testGetSize() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());
      @Var IndexReader reader = DirectoryReader.open(directory);
      @Var
      Optional<TokenSsdvFacetState> facetState =
          TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      assertThat(facetState.get().getSize()).isEqualTo(3);

      addAndCommitDocuments(writer, 5, 5, Optional.of(3));
      reader = DirectoryReader.open(directory);
      facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      assertThat(facetState.get().getSize()).isEqualTo(8);

      addAndCommitDocuments(writer, 2, 2, Optional.of(8));
      reader = DirectoryReader.open(directory);
      facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      assertThat(facetState.get().getSize()).isEqualTo(10);

      // Add 2 documents with non-unique values
      addAndCommitDocuments(writer, 2, 2, Optional.of(0));
      reader = DirectoryReader.open(directory);
      facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      assertThat(facetState.get().getSize()).isEqualTo(10);
    }
  }

  @Test
  public void testGetFacetsConfig() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 1, 1, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      var facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();
      FacetsConfig facetsConfig = facetState.get().getFacetsConfig();
      assertThat(facetsConfig.isDimConfigured("foo")).isTrue();
      var dimConfig = facetsConfig.getDimConfig("foo");
      assertThat(dimConfig.multiValued).isTrue();
      assertThat(dimConfig.requireDimCount).isFalse();
      assertThat(dimConfig.indexFieldName).isEqualTo("foo");
      assertThat(dimConfig.drillDownTermsIndexing)
          .isEqualTo(FacetsConfig.DrillDownTermsIndexing.ALL);
    }
  }

  @Test
  public void testGetOrdRange() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 2, 2, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      var facetState = TokenSsdvFacetState.create(reader, "foo", Optional.empty());
      assertThat(facetState).isPresent();

      var ordRange = facetState.get().getOrdRange("foo");
      assertThat(ordRange.start()).isEqualTo(0);
      assertThat(ordRange.end()).isEqualTo(1);

      Assert.assertThrows(
          IllegalArgumentException.class, () -> facetState.get().getOrdRange("invalidDim"));
    }
  }

  @Test
  public void testGetPrefixToOrdRange() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());

      IndexReader reader = DirectoryReader.open(directory);
      Optional<TokenSsdvFacetState> facetState =
          TokenSsdvFacetState.create(reader, "foo", Optional.empty());

      // single segment index should not create ordinal map
      assertThat(facetState).isPresent();
      var prefixToOrdRange = facetState.get().getPrefixToOrdRange();
      assertThat(prefixToOrdRange).containsKey("foo");
      assertThat(prefixToOrdRange.keySet().size()).isEqualTo(1);
      assertThat(prefixToOrdRange.get("foo").start()).isEqualTo(0);
      assertThat(prefixToOrdRange.get("foo").end()).isEqualTo(2);
    }
  }

  @Test
  public void testMaxCardinalityThrowsCardinalityException() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());
      IndexReader reader = DirectoryReader.open(directory);

      Assert.assertThrows(
          TokenFacetsCardinalityLimitExceededException.class,
          () -> TokenSsdvFacetState.create(reader, "foo", Optional.of(2)));
    }
  }

  @Test
  public void testEqualsMaxCardinalityDoesNotThrowCardinalityException() throws Exception {
    try (var directory = new ByteBuffersDirectory();
        var writer = new IndexWriter(directory, new IndexWriterConfig())) {
      addAndCommitDocuments(writer, 3, 3, Optional.empty());
      IndexReader reader = DirectoryReader.open(directory);

      TokenSsdvFacetState.create(reader, "foo", Optional.of(3));
    }
  }

  private static void addAndCommitDocuments(
      IndexWriter writer, int numDocs, int cardinality, Optional<Integer> cardinalityOffset)
      throws Exception {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(
          new SortedSetDocValuesField(
              "foo",
              new BytesRef(
                  String.format("facet%d", i % cardinality + cardinalityOffset.orElse(0)))));
      writer.addDocument(doc);
    }
    writer.commit();
  }
}
