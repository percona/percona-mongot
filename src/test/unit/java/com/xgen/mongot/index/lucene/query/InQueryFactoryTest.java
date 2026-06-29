package com.xgen.mongot.index.lucene.query;

import static com.google.common.truth.Truth.assertThat;

import com.xgen.mongot.featureflag.Feature;
import com.xgen.mongot.featureflag.FeatureFlags;
import com.xgen.mongot.featureflag.dynamic.DynamicFeatureFlags;
import com.xgen.mongot.index.definition.DocumentFieldDefinition;
import com.xgen.mongot.index.lucene.util.AnalyzedText;
import com.xgen.mongot.index.lucene.util.LuceneDoubleConversionUtils;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.testing.mongot.index.definition.DocumentFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.FieldDefinitionBuilder;
import com.xgen.testing.mongot.index.definition.TokenFieldDefinitionBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;

public class InQueryFactoryTest {

  @Test
  public void testBoolean() throws Exception {
    var operator = OperatorBuilder.in().path("a").booleans(List.of(true, false)).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("$type:boolean/a", "T")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("$type:boolean/a", "F")), BooleanClause.Occur.SHOULD)
                .build());
    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void testObjectId() throws Exception {
    var objectIds =
        List.of(new ObjectId("507f1f77bcf86cd799439011"), new ObjectId("507f1f77bcf86cd799439012"));
    var operator = OperatorBuilder.in().path("a").objectIds(objectIds).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(
                    new TermQuery(
                        new Term("$type:objectId/a", new BytesRef(objectIds.get(0).toByteArray()))),
                    BooleanClause.Occur.SHOULD)
                .add(
                    new TermQuery(
                        new Term("$type:objectId/a", new BytesRef(objectIds.get(1).toByteArray()))),
                    BooleanClause.Occur.SHOULD)
                .build());
    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void testLong() throws Exception {

    List<Long> longs = List.of(1L, 2L, 3L);
    List<Long> doubles =
        longs.stream().map(LuceneDoubleConversionUtils::toLong).collect(Collectors.toList());
    long[] longArr = longs.stream().mapToLong(Long::longValue).toArray();
    long[] doubleArr = doubles.stream().mapToLong(Long::longValue).toArray();

    var operator = OperatorBuilder.in().path("a").longs(longs).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongPoint.newSetQuery("$type:int64/a", longArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:int64Multiple/a", longs),
                    BooleanClause.Occur.SHOULD)
                .add(LongPoint.newSetQuery("$type:double/a", doubleArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:doubleMultiple/a", doubles),
                    BooleanClause.Occur.SHOULD)
                .build());

    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void testLongWithIndexOrDocValuesQueryEnabled() throws Exception {
    FeatureFlags featureFlags =
        FeatureFlags.withDefaults()
            .enable(Feature.INDEX_OR_DOC_VALUES_QUERY_FOR_IN_OPERATOR)
            .enable(Feature.NEW_EMBEDDED_SEARCH_CAPABILITIES)
            .build();
    List<Long> longs = List.of(1L, 2L, 3L);
    List<Long> doubles =
        longs.stream().map(LuceneDoubleConversionUtils::toLong).collect(Collectors.toList());
    long[] longArr = longs.stream().mapToLong(Long::longValue).toArray();
    long[] doubleArr = doubles.stream().mapToLong(Long::longValue).toArray();

    var operator = OperatorBuilder.in().path("a").longs(longs).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongField.newSetQuery("$type:int64/a", longArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:int64Multiple/a", longs),
                    BooleanClause.Occur.SHOULD)
                .add(LongField.newSetQuery("$type:double/a", doubleArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:doubleMultiple/a", doubles),
                    BooleanClause.Occur.SHOULD)
                .build());

    LuceneSearchTranslation.gated(featureFlags).assertTranslatedTo(operator, expected);
  }

  @Test
  public void testDouble() throws Exception {

    List<Long> doubles =
        Stream.of(1.123, 2.345, 3.531)
            .map(LuceneDoubleConversionUtils::toLong)
            .collect(Collectors.toList());
    List<Long> longs =
        Stream.of(1.123, 2.345, 3.531).map(Double::longValue).collect(Collectors.toList());
    long[] longArr = longs.stream().mapToLong(Long::longValue).toArray();
    long[] doubleArr = doubles.stream().mapToLong(Long::longValue).toArray();

    var operator = OperatorBuilder.in().path("a").doubles(List.of(1.123, 2.345, 3.531)).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongPoint.newSetQuery("$type:int64/a", longArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:int64Multiple/a", longs),
                    BooleanClause.Occur.SHOULD)
                .add(LongPoint.newSetQuery("$type:double/a", doubleArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:doubleMultiple/a", doubles),
                    BooleanClause.Occur.SHOULD)
                .build());

    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void testDoubleWithIndexOrDocValuesQueryEnabled() throws Exception {
    FeatureFlags featureFlags =
        FeatureFlags.withDefaults()
            .enable(Feature.NEW_EMBEDDED_SEARCH_CAPABILITIES)
            .enable(Feature.INDEX_OR_DOC_VALUES_QUERY_FOR_IN_OPERATOR)
            .build();
    List<Long> doubles =
        Stream.of(1.123, 2.345, 3.531)
            .map(LuceneDoubleConversionUtils::toLong)
            .collect(Collectors.toList());
    List<Long> longs =
        Stream.of(1.123, 2.345, 3.531).map(Double::longValue).collect(Collectors.toList());
    long[] longArr = longs.stream().mapToLong(Long::longValue).toArray();
    long[] doubleArr = doubles.stream().mapToLong(Long::longValue).toArray();

    var operator = OperatorBuilder.in().path("a").doubles(List.of(1.123, 2.345, 3.531)).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongField.newSetQuery("$type:int64/a", longArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:int64Multiple/a", longs),
                    BooleanClause.Occur.SHOULD)
                .add(LongField.newSetQuery("$type:double/a", doubleArr), BooleanClause.Occur.SHOULD)
                .add(
                    LongPoint.newSetQuery("$type:doubleMultiple/a", doubles),
                    BooleanClause.Occur.SHOULD)
                .build());

    LuceneSearchTranslation.gated(featureFlags).assertTranslatedTo(operator, expected);
  }

  @Test
  public void testDate() throws Exception {
    var dates = List.of(new Date(1), new Date(2), new Date(3));
    var translatedDates = dates.stream().map(Date::getTime).collect(Collectors.toList());
    var operator = OperatorBuilder.in().path("a").dates(dates).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongPoint.newSetQuery("$type:date/a", translatedDates), Occur.SHOULD)
                .add(LongPoint.newSetQuery("$type:dateMultiple/a", translatedDates), Occur.SHOULD)
                .build());

    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void testDateV2() throws Exception {
    var dates = List.of(new Date(1), new Date(2), new Date(3));
    long[] translatedDates = dates.stream().mapToLong(Date::getTime).toArray();
    var operator = OperatorBuilder.in().path("a").dates(dates).build();
    var expected =
        new ConstantScoreQuery(
            new BooleanQuery.Builder()
                .add(LongField.newSetQuery("$type:dateV2/a", translatedDates), Occur.SHOULD)
                .build());

    LuceneSearchTranslation.gatedWithDynamicFeatureFlags(DynamicFeatureFlags.NUMERIC_V2_SEMANTICS)
        .assertTranslatedTo(operator, expected);
  }

  @Test
  public void testToken() throws Exception {
    var strings = List.of("a", "b", "c");
    var field = "$type:token/a";
    List<BytesRef> analyzedStrings =
        strings.stream()
            .map(
                value -> {
                  try {
                    return AnalyzedText.applyTokenFieldTypeNormalizer(
                        field, new StandardAnalyzer(), value);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .map(BytesRef::new)
            .collect(Collectors.toList());
    var operator = OperatorBuilder.in().path("a").strings(strings).build();
    var expected =
        new IndexOrDocValuesQuery(
            new TermInSetQuery(field, analyzedStrings),
            SortedSetDocValuesField.newSlowSetQuery(field, analyzedStrings));

    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);

    DocumentFieldDefinition mappings =
        DocumentFieldDefinitionBuilder.builder()
            .field(
                "a",
                FieldDefinitionBuilder.builder()
                    .token(TokenFieldDefinitionBuilder.builder().build())
                    .build())
            .build();

    LuceneSearchTranslation.mapped(mappings).assertTranslatedTo(operator, expected);
  }

  @Test
  public void testUuid() throws Exception {
    var uuidStrings =
        List.of(
            "00000000-0000-0000-0000-000000000000",
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222");
    var field = "$type:uuid/a";

    var operator = OperatorBuilder.in().path("a").uuidStrings(uuidStrings).build();

    List<BytesRef> uuids = uuidStrings.stream().map(BytesRef::new).collect(Collectors.toList());
    var expected =
        new IndexOrDocValuesQuery(
            new TermInSetQuery(field, uuids),
            SortedSetDocValuesField.newSlowSetQuery(field, uuids));

    LuceneSearchTranslation.get().assertTranslatedTo(operator, expected);
  }

  @Test
  public void stringQuery_pathNotIndexedAsToken_throwsInvalidQueryException() {
    var strings = List.of("multi token input", "another multi token");
    var operator = OperatorBuilder.in().path("a").strings(strings).build();

    InvalidQueryException exception =
        Assert.assertThrows(
            InvalidQueryException.class, () -> LuceneSearchTranslation.get().translate(operator));

    assertThat(exception.getMessage()).contains("Path 'a' needs to be indexed as token");
  }
}
