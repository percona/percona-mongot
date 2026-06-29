package com.xgen.mongot.index.lucene.query.highlights;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.xgen.mongot.index.definition.DocumentFieldDefinition;
import com.xgen.mongot.index.definition.FieldDefinition;
import com.xgen.mongot.index.definition.SearchFieldDefinitionResolver;
import com.xgen.mongot.index.definition.SearchIndexDefinition;
import com.xgen.mongot.index.definition.StringFieldDefinition;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.path.string.StringPath;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.SearchQueryTimeMappingChecks;
import com.xgen.mongot.index.query.highlights.Highlight;
import com.xgen.mongot.index.query.highlights.UnresolvedHighlight;
import com.xgen.mongot.index.version.IndexFormatVersion;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.TestUtils;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.EmbeddedDocumentsFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.SearchIndexDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.StringFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.path.string.StringPathBuilder;
import com.xgen.testing.mongot.index.path.string.UnresolvedStringPathBuilder;
import com.xgen.testing.mongot.index.query.highlights.HighlightBuilder;
import com.xgen.testing.mongot.index.query.highlights.UnresolvedHighlightBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HighlightResolverTest {

  private static Directory directory;
  private static IndexWriter writer;
  private int counter;

  /** set up an index. */
  @Before
  public void setUp() throws IOException {
    TemporaryFolder temporaryFolder = TestUtils.getTempFolder();
    directory = new MMapDirectory(temporaryFolder.getRoot().toPath());
    writer = new IndexWriter(directory, new IndexWriterConfig());
    this.counter = 0;
  }

  @After
  public void tearDown() throws IOException {
    writer.close();
    directory.close();
  }

  @Test
  public void resolveHighlight_wildcardPath() throws Exception {
    var docs =
        List.of(
            stringFieldDocument("a", true, Optional.empty()),
            stringFieldDocument("b", false, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("a", stringField(true))
            .field("b", stringField(false))
            .build();
    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("*"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("*"))
                .query("unused")
                .build(),
            Optional.empty());
    Highlight expected = HighlightBuilder.builder().path("a").storedPath("a").build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_wildcardMultiPath() throws Exception {
    var docs =
        List.of(
            stringFieldDocument("title", true, Optional.empty()),
            stringFieldDocument("plot", true, Optional.empty()),
            stringFieldDocument("director", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", multiStringField("french", true))
            .field("plot", multiStringField("spanish", true))
            .field("director", multiStringField("french", true))
            .build();
    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("*"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("*"))
                .query("unused")
                .build(),
            Optional.empty());
    Highlight expected =
        HighlightBuilder.builder()
            .path("director")
            .storedPath("director")
            .path("title")
            .storedPath("title")
            .path("plot")
            .storedPath("plot")
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_nonWildcardPath() throws Exception {
    var docs =
        List.of(
            stringFieldDocument("alec", true, Optional.empty()),
            stringFieldDocument("alex", false, Optional.empty()),
            stringFieldDocument("bob", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("alec", stringField(true))
            .field("alex", stringField(false))
            .field("bob", stringField(true))
            .build();
    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
            .path("bob")
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
                .path("bob")
                .query("unused")
                .build(),
            Optional.empty());
    Highlight expected =
        HighlightBuilder.builder()
            .path("alec")
            .path("bob")
            .storedPath("alec")
            .storedPath("bob")
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_nonWildcardMultiPath() throws Exception {
    var docs =
        List.of(
            stringFieldDocument("title", true, Optional.empty()),
            stringFieldDocument("plot", true, Optional.empty()),
            stringFieldDocument("director", true, Optional.empty()),
            stringFieldDocument("actor", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", multiStringField("french", true))
            .field("plot", multiStringField("spanish", true))
            .field("director", multiStringField("french", false))
            .build();
    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .multi("title", "french")
            .multi("plot", "spanish")
            .multi("director", "french")
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text().path("title").query("unused").build(),
            Optional.empty());
    Highlight expected =
        HighlightBuilder.builder()
            .multi("title", "french")
            .storedPath("title")
            .multi("plot", "spanish")
            .storedPath("plot")
            .multi("director", "french")
            .storedMulti("director", "french")
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_WildcardAndNonWildcardMultiPath() throws Exception {
    var docs =
        List.of(
            stringFieldDocument("title", true, Optional.empty()),
            stringFieldDocument("plot", true, Optional.empty()),
            stringFieldDocument("director", true, Optional.empty()),
            stringFieldDocument("actor", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", multiStringField("french", true))
            .field("plot", multiStringField("spanish", true))
            .field("director", multiStringField("french", false))
            .build();
    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path("title")
            .multi("title", "french")
            .path("plot")
            .multi("plot", "spanish")
            .multi("director", "french")
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text().path("title").query("unused").build(),
            Optional.empty());
    Highlight expected =
        HighlightBuilder.builder()
            .path("title")
            .storedPath("title")
            .multi("title", "french")
            .storedPath("title")
            .path("plot")
            .storedPath("plot")
            .multi("plot", "spanish")
            .storedPath("plot")
            .multi("director", "french")
            .storedMulti("director", "french")
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void validateNonWildcardPath() throws InvalidQueryException {
    StringPath alec = StringPathBuilder.fieldPath("alec");
    StringPath alex = StringPathBuilder.fieldPath("alex");
    StringPath bob = StringPathBuilder.fieldPath("bob");

    Map<StringPath, String> queriedPathsToLuceneFieldNames =
        Map.of(
            alec,
            FieldName.getLuceneFieldNameForStringPath(alec, Optional.empty()),
            alex,
            FieldName.getLuceneFieldNameForStringPath(alex, Optional.empty()),
            bob,
            FieldName.getLuceneFieldNameForStringPath(bob, Optional.empty()));

    SearchQueryTimeMappingChecks queryTimeMappingChecks = mock(SearchQueryTimeMappingChecks.class);
    doThrow(InvalidQueryException.class)
        .when(queryTimeMappingChecks)
        .validatePathStringStorage(alex, Optional.empty());

    HighlightResolver highlightResolver = new HighlightResolver(queryTimeMappingChecks);

    Assert.assertEquals(
        "$type:string/alec",
        highlightResolver.validateNonWildcardPath(
            alec, queriedPathsToLuceneFieldNames, Optional.empty()));

    Assert.assertThrows(
        InvalidQueryException.class,
        () ->
            highlightResolver.validateNonWildcardPath(
                alex, queriedPathsToLuceneFieldNames, Optional.empty()));

    Assert.assertEquals(
        "$type:string/bob",
        highlightResolver.validateNonWildcardPath(
            bob, queriedPathsToLuceneFieldNames, Optional.empty()));
  }

  @Test
  public void validateWildcardPath() throws IOException {
    var docs =
        List.of(
            stringFieldDocument("alec", true, Optional.empty()),
            stringFieldDocument("alex", false, Optional.empty()),
            stringFieldDocument("bob", true, Optional.empty()));
    index(docs);

    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("alec", stringField(true))
            .field("alex", stringField(false))
            .field("bob", stringField(true))
            .build();

    StringPath alec = StringPathBuilder.fieldPath("alec");
    StringPath alex = StringPathBuilder.fieldPath("alex");
    StringPath bob = StringPathBuilder.fieldPath("bob");

    Map<StringPath, String> queriedPathsToLuceneFieldNames =
        Map.of(
            alec,
            FieldName.getLuceneFieldNameForStringPath(alec, Optional.empty()),
            alex,
            FieldName.getLuceneFieldNameForStringPath(alex, Optional.empty()),
            bob,
            FieldName.getLuceneFieldNameForStringPath(bob, Optional.empty()));

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Assert.assertEquals(
        Optional.of("$type:string/alec"),
        highlightResolver.validateWildcardPath(
            alec, queriedPathsToLuceneFieldNames, Optional.empty()));

    Assert.assertEquals(
        Optional.empty(),
        highlightResolver.validateWildcardPath(
            alex, queriedPathsToLuceneFieldNames, Optional.empty()));

    Assert.assertEquals(
        Optional.of("$type:string/bob"),
        highlightResolver.validateWildcardPath(
            bob, queriedPathsToLuceneFieldNames, Optional.empty()));
  }

  @Test
  public void getPathsToLuceneFieldNamesMap_textOperator() throws IOException {
    var docs =
        List.of(
            stringFieldDocument("alec", true, Optional.empty()),
            stringFieldDocument("alex", false, Optional.empty()),
            stringFieldDocument("bob", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);

    StringPath alec = StringPathBuilder.fieldPath("alec");
    StringPath alex = StringPathBuilder.fieldPath("alex");
    StringPath bob = StringPathBuilder.fieldPath("bob");

    Map<StringPath, String> expected =
        Map.of(
            alec,
            FieldName.getLuceneFieldNameForStringPath(alec, Optional.empty()),
            alex,
            FieldName.getLuceneFieldNameForStringPath(alex, Optional.empty()),
            bob,
            FieldName.getLuceneFieldNameForStringPath(bob, Optional.empty()));

    Map<StringPath, String> actual =
        HighlightResolver.getPathsToLuceneFieldNamesMap(
            reader,
            OperatorBuilder.text().path("alec").path("alex").path("bob").query("unused").build(),
            Optional.empty());

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void getPathsToLuceneFieldNamesMap_autocompleteOperator() {
    StringPath alec = StringPathBuilder.fieldPath("alec");

    Map<StringPath, String> expected =
        Map.of(
            alec,
            FieldName.TypeField.AUTOCOMPLETE.getLuceneFieldName(
                alec.asField().getValue(), Optional.empty()));

    Map<StringPath, String> actual =
        HighlightResolver.getPathsToLuceneFieldNamesMap(
            mock(LeafReader.class),
            OperatorBuilder.autocomplete().path("alec").query("unused").build(),
            Optional.empty());

    Assert.assertEquals(expected, actual);
  }

  /**
   * For a compound operator containing a phrase operator in one clause and an autocomplete operator
   * in another clause, with each operator querying over the same path, this test ensures that the
   * path is mapped to its string Lucene field name in the map since the autocomplete operator is
   * not the only operator querying over that path.
   */
  @Test
  public void getPathsToLuceneFieldNamesMap_compoundOperatorSamePath() throws IOException {
    var docs =
        List.of(
            autocompleteFieldDocument("alec"), stringFieldDocument("alec", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);

    StringPath alec = StringPathBuilder.fieldPath("alec");

    Map<StringPath, String> expected =
        Map.of(alec, FieldName.getLuceneFieldNameForStringPath(alec, Optional.empty()));

    Map<StringPath, String> actual =
        HighlightResolver.getPathsToLuceneFieldNamesMap(
            reader,
            OperatorBuilder.compound()
                .should(OperatorBuilder.autocomplete().path("alec").query("unused").build())
                .must(OperatorBuilder.phrase().path("alec").query("unused").build())
                .build(),
            Optional.empty());

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_wildcardDeDuplicated() throws Exception {
    var docs = List.of(stringFieldDocument("ab", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("ab", stringField(true))
            .build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
            .path(UnresolvedStringPathBuilder.wildcardPath("*b"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
                .path(UnresolvedStringPathBuilder.wildcardPath("*b"))
                .query("unused")
                .build(),
            Optional.empty());
    Highlight expected = HighlightBuilder.builder().path("ab").storedPath("ab").build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_WildcardAndNonWildcardNoDuplicates() throws Exception {
    var docs = List.of(stringFieldDocument("ab", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("ab", stringField(true))
            .build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
            .path(UnresolvedStringPathBuilder.fieldPath("ab"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("a*"))
                .path(UnresolvedStringPathBuilder.fieldPath("ab"))
                .query("unused")
                .build(),
            Optional.empty());
    Highlight expected = HighlightBuilder.builder().path("ab").storedPath("ab").build();
    Assert.assertEquals(expected.maxNumPassages(), actual.maxNumPassages());
    Assert.assertEquals(expected.maxCharsToExamine(), actual.maxCharsToExamine());
    Assert.assertEquals(expected.resolvedLuceneFieldNames(), actual.resolvedLuceneFieldNames());
    Assert.assertEquals(expected.storedLuceneFieldNameMap(), actual.storedLuceneFieldNameMap());
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void resolveHighlight_pathValidationFailure() throws IOException {
    var docs = List.of(stringFieldDocument("ab", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings = DocumentFieldDefinitionBuilder.builder().dynamic(false).build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.fieldPath("ab"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    TestUtils.assertThrows(
        "Highlights cannot be generated. Path: \"ab\" is not stored statically or "
            + "dynamically indexed.",
        InvalidQueryException.class,
        () ->
            highlightResolver.resolveHighlight(
                unresolved,
                reader,
                OperatorBuilder.text()
                    .path(UnresolvedStringPathBuilder.fieldPath("ab"))
                    .query("unused")
                    .build(),
                Optional.empty()));
  }

  @Test
  public void resolveHighlight_pathValidationFailureStringFieldNotStored() throws IOException {
    var docs = List.of(stringFieldDocument("ab", true, Optional.empty()));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("ab", stringField(false))
            .build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.fieldPath("ab"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    TestUtils.assertThrows(
        "Index definition specifies store:false",
        InvalidQueryException.class,
        () ->
            highlightResolver.resolveHighlight(
                unresolved,
                reader,
                OperatorBuilder.text()
                    .path(UnresolvedStringPathBuilder.fieldPath("ab"))
                    .query("unused")
                    .build(),
                Optional.empty()));
  }

  @Test
  public void testWildCardPathEmbedded() throws Exception {
    var embeddedRoot = FieldPath.parse("embedded");
    var docs =
        List.of(
            stringFieldDocument("embedded.a", true, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.b", false, Optional.of(embeddedRoot)));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field(
                "embedded",
                FieldDefinitionBuilder.builder()
                    .embeddedDocuments(
                        EmbeddedDocumentsFieldDefinitionBuilder.builder()
                            .field(
                                "a",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(true).build())
                                    .build())
                            .field(
                                "b",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(false).build())
                                    .build())
                            .build())
                    .build())
            .build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("*"))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("*"))
                .query("unused")
                .build(),
            Optional.of(FieldPath.parse("embedded")));

    Highlight expected =
        HighlightBuilder.builder()
            .path("embedded.a")
            .storedPath("embedded.a")
            .returnScope(FieldPath.parse("embedded"))
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testNonWildcardPathEmbedded() throws Exception {
    var embeddedRoot = FieldPath.newRoot("embedded");
    var docs =
        List.of(
            stringFieldDocument("embedded.alec", true, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.alex", false, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.bob", true, Optional.of(embeddedRoot)));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field(
                "embedded",
                FieldDefinitionBuilder.builder()
                    .embeddedDocuments(
                        EmbeddedDocumentsFieldDefinitionBuilder.builder()
                            .field(
                                "alec",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(true).build())
                                    .build())
                            .field(
                                "alex",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(false).build())
                                    .build())
                            .field(
                                "bob",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(true).build())
                                    .build())
                            .build())
                    .build())
            .build();

    UnresolvedHighlight unresolved =
        UnresolvedHighlightBuilder.builder()
            .path(UnresolvedStringPathBuilder.wildcardPath("embedded.a*"))
            .path("embedded.bob")
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Highlight actual =
        highlightResolver.resolveHighlight(
            unresolved,
            reader,
            OperatorBuilder.text()
                .path(UnresolvedStringPathBuilder.wildcardPath("embedded.a*"))
                .path("embedded.bob")
                .query("unused")
                .build(),
            Optional.of(embeddedRoot));

    Highlight expected =
        HighlightBuilder.builder()
            .path("embedded.alec")
            .storedPath("embedded.alec")
            .path("embedded.bob")
            .storedPath("embedded.bob")
            .returnScope(embeddedRoot)
            .build();
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testValidateResolvedWildcardPathEmbedded() throws IOException {
    var embeddedRoot = FieldPath.newRoot("embedded");
    var docs =
        List.of(
            stringFieldDocument("embedded.alec", true, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.alex", false, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.bob", true, Optional.of(embeddedRoot)));
    index(docs);

    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field(
                "embedded",
                FieldDefinitionBuilder.builder()
                    .embeddedDocuments(
                        EmbeddedDocumentsFieldDefinitionBuilder.builder()
                            .field(
                                "alec",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(true).build())
                                    .build())
                            .field(
                                "alex",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(false).build())
                                    .build())
                            .field(
                                "bob",
                                FieldDefinitionBuilder.builder()
                                    .string(
                                        StringFieldDefinitionBuilder.builder().store(true).build())
                                    .build())
                            .build())
                    .build())
            .build();

    StringPath alec = StringPathBuilder.fieldPath("embedded.alec");
    StringPath alex = StringPathBuilder.fieldPath("embedded.alex");
    StringPath bob = StringPathBuilder.fieldPath("embedded.bob");

    Map<StringPath, String> queriedPathsToLuceneFieldNames =
        Map.of(
            alec,
            FieldName.getLuceneFieldNameForStringPath(alec, Optional.of(embeddedRoot)),
            alex,
            FieldName.getLuceneFieldNameForStringPath(alex, Optional.of(embeddedRoot)),
            bob,
            FieldName.getLuceneFieldNameForStringPath(bob, Optional.of(embeddedRoot)));

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));

    Assert.assertEquals(
        Optional.of("$embedded:8/embedded/$type:string/embedded.alec"),
        highlightResolver.validateWildcardPath(
            alec, queriedPathsToLuceneFieldNames, Optional.of(embeddedRoot)));

    Assert.assertEquals(
        Optional.empty(),
        highlightResolver.validateWildcardPath(
            alex, queriedPathsToLuceneFieldNames, Optional.of(embeddedRoot)));

    Assert.assertEquals(
        Optional.of("$embedded:8/embedded/$type:string/embedded.bob"),
        highlightResolver.validateWildcardPath(
            bob, queriedPathsToLuceneFieldNames, Optional.of(embeddedRoot)));
  }

  @Test
  public void testGetPathsToLuceneFieldNamesMapTextOperatorEmbedded() throws IOException {
    var embeddedRoot = FieldPath.newRoot("embedded");
    var docs =
        List.of(
            stringFieldDocument("embedded.alec", true, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.alex", false, Optional.of(embeddedRoot)),
            stringFieldDocument("embedded.bob", true, Optional.of(embeddedRoot)));
    index(docs);

    IndexReader reader = DirectoryReader.open(writer);

    StringPath alec = StringPathBuilder.fieldPath("embedded.alec");
    StringPath alex = StringPathBuilder.fieldPath("embedded.alex");
    StringPath bob = StringPathBuilder.fieldPath("embedded.bob");

    Map<StringPath, String> expected =
        Map.of(
            alec,
            FieldName.getLuceneFieldNameForStringPath(alec, Optional.of(embeddedRoot)),
            alex,
            FieldName.getLuceneFieldNameForStringPath(alex, Optional.of(embeddedRoot)),
            bob,
            FieldName.getLuceneFieldNameForStringPath(bob, Optional.of(embeddedRoot)));

    Map<StringPath, String> actual =
        HighlightResolver.getPathsToLuceneFieldNamesMap(
            reader,
            OperatorBuilder.text()
                .path("embedded.alec")
                .path("embedded.alex")
                .path("embedded.bob")
                .query("unused")
                .build(),
            Optional.of(embeddedRoot));

    Assert.assertEquals(expected, actual);
  }

  SearchFieldDefinitionResolver createFieldDefinitionResolver(DocumentFieldDefinition mappings) {
    return createIndexDefinition(mappings)
        .createFieldDefinitionResolver(IndexFormatVersion.CURRENT);
  }

  @Test
  public void getStoredLuceneField_baseIsUnchanged() throws Exception {
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", stringField(true))
            .field("plot", stringField(true))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));
    Assert.assertEquals(
        "$type:string/title", highlightResolver.getStoredLuceneField("$type:string/title"));
    Assert.assertEquals(
        "$type:string/plot", highlightResolver.getStoredLuceneField("$type:string/plot"));
  }

  @Test
  public void getStoredLuceneField_multiToBase() throws Exception {
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", multiStringField("french", true))
            .field("plot", multiStringField("spanish", true))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));
    Assert.assertEquals(
        "$type:string/title", highlightResolver.getStoredLuceneField("$multi/title.french"));
    Assert.assertEquals(
        "$type:string/plot", highlightResolver.getStoredLuceneField("$multi/plot.spanish"));
  }

  @Test
  public void getStoredLuceneField_multiToBaseNotStored() throws Exception {
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field("title", multiStringField("french", false))
            .field("plot", multiStringField("spanish", false))
            .build();

    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));
    Assert.assertEquals(
        "$multi/title.french", highlightResolver.getStoredLuceneField("$multi/title.french"));
    Assert.assertEquals(
        "$multi/plot.spanish", highlightResolver.getStoredLuceneField("$multi/plot.spanish"));
  }

  @Test
  public void getStoredLuceneField_embeddedIsUnchanged() throws Exception {
    var mappings =
        DocumentFieldDefinitionBuilder.builder()
            .dynamic(false)
            .field(
                "test",
                FieldDefinitionBuilder.builder()
                    .embeddedDocuments(
                        EmbeddedDocumentsFieldDefinitionBuilder.builder()
                            .dynamic(false)
                            .field("title", stringField(true))
                            .field("plot", multiStringField("spanish", true))
                            .build())
                    .build())
            .build();
    HighlightResolver highlightResolver =
        HighlightResolver.create(createFieldDefinitionResolver(mappings));
    Assert.assertEquals(
        "$embedded:4/test/$type:string/title",
        highlightResolver.getStoredLuceneField("$embedded:4/test/$type:string/title"));
    Assert.assertEquals(
        "$embedded:4/test/$multi/plot.spanish",
        highlightResolver.getStoredLuceneField("$embedded:4/test/$multi/plot.spanish"));
  }

  private SearchIndexDefinition createIndexDefinition(DocumentFieldDefinition mappings) {
    return SearchIndexDefinitionBuilder.builder().defaultMetadata().mappings(mappings).build();
  }

  private void index(List<Document> docs) throws IOException {
    for (Document doc : docs) {
      int id = this.counter++;
      doc.add(new StoredField(FieldName.MetaField.ID.getLuceneFieldName(), id));
      writer.addDocument(doc);
    }

    writer.commit();
  }

  private Document stringFieldDocument(
      String field, boolean store, Optional<FieldPath> embeddedRoot) {
    Document doc = new Document();
    doc.add(
        new TextField(
            FieldName.TypeField.STRING.getLuceneFieldName(FieldPath.parse(field), embeddedRoot),
            "_",
            store ? Field.Store.YES : Field.Store.NO));
    return doc;
  }

  private Document autocompleteFieldDocument(String field) {
    Document doc = new Document();
    FieldType autocompleteFieldType = new FieldType();
    autocompleteFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    autocompleteFieldType.setTokenized(true);
    autocompleteFieldType.setStored(true);
    autocompleteFieldType.freeze();
    doc.add(new Field(field, "_", autocompleteFieldType));
    return doc;
  }

  private FieldDefinition stringField(boolean store) {
    return FieldDefinitionBuilder.builder()
        .string(StringFieldDefinitionBuilder.builder().store(store).build())
        .build();
  }

  private FieldDefinition multiStringField(String multi, boolean store) {
    StringFieldDefinition multiFieldDefinition =
        StringFieldDefinitionBuilder.builder().store(true).build();
    return FieldDefinitionBuilder.builder()
        .string(
            StringFieldDefinitionBuilder.builder()
                .store(store)
                .multi(multi, multiFieldDefinition)
                .build())
        .build();
  }
}
