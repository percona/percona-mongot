package com.xgen.mongot.index.lucene.codec;

import static com.google.common.truth.Truth.assertThat;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.KNN_BIT;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.KNN_BYTE;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.KNN_F32_Q1;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.KNN_F32_Q7;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.KNN_VECTOR;
import static com.xgen.mongot.index.lucene.field.FieldName.TypeField.NUMBER_INT64;
import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_BEAM_WIDTH;
import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat.DEFAULT_MAX_CONN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.xgen.mongot.index.definition.IndexDefinition;
import com.xgen.mongot.index.definition.VectorFieldSpecification;
import com.xgen.mongot.index.definition.VectorIndexingAlgorithm;
import com.xgen.mongot.index.definition.VectorSimilarity;
import com.xgen.mongot.index.definition.quantization.VectorQuantization;
import com.xgen.mongot.index.lucene.codec.flat.Float32AndByteFlatVectorsFormat;
import com.xgen.mongot.index.lucene.field.FieldName;
import com.xgen.mongot.index.lucene.quantization.Mongot01042HnswBinaryQuantizedVectorsFormat;
import com.xgen.mongot.index.lucene.quantization.Mongot01042HnswBitVectorsFormat;
import com.xgen.mongot.index.lucene.vector.Lucene99NativeHnswVectorsFormat;
import com.xgen.mongot.util.FieldPath;
import com.xgen.testing.mongot.mock.index.SearchIndex;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.backward_codecs.lucene99.Lucene94FieldInfosFormatV1;
import org.apache.lucene.backward_codecs.lucene99.Lucene99PostingsFormat;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.bloom.BloomFilteringPostingsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90CompoundFormat;
import org.apache.lucene.codecs.lucene90.Lucene90DocValuesFormat;
import org.apache.lucene.codecs.lucene90.Lucene90LiveDocsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90NormsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90PointsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90TermVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99SegmentInfoFormat;
import org.apache.lucene.codecs.perfield.PerFieldDocValuesFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class LuceneCodecTest {

  public record TestSetup(
      FieldName.TypeField typeField,
      Class<? extends KnnVectorsFormat> expectedFormatClass,
      int maxEdges,
      int numEdgeCandidates) {}

  @DataPoints
  public static final List<TestSetup> data =
      List.of(
          new TestSetup(KNN_VECTOR, Lucene99HnswVectorsFormat.class, 32, 200),
          new TestSetup(KNN_BYTE, Lucene99NativeHnswVectorsFormat.class, 128, 100),
          new TestSetup(KNN_BIT, Mongot01042HnswBitVectorsFormat.class, 512, 1000),
          new TestSetup(KNN_F32_Q7, Lucene99HnswScalarQuantizedVectorsFormat.class, 2, 30),
          new TestSetup(KNN_F32_Q1, Mongot01042HnswBinaryQuantizedVectorsFormat.class, 49, 66));

  @Theory
  public void getKnnVectorsFormatForField_withProvidedSetup_producesExpectedFormat(TestSetup setup)
      throws NoSuchFieldException, IllegalAccessException {

    FieldPath path = FieldPath.parse("vector");
    VectorFieldSpecification specification =
        createVectorFieldDefinition(setup.maxEdges(), setup.numEdgeCandidates());

    LuceneCodec codec = new LuceneCodec(Map.of(path, specification));
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();
    KnnVectorsFormat vectorsFormat =
        format.getKnnVectorsFormatForField(
            setup.typeField().getLuceneFieldName(path, Optional.empty()));

    Assert.assertTrue(setup.expectedFormatClass().isInstance(vectorsFormat));
    VectorFieldSpecification.HnswOptions hnswOptions =
        getKnnFieldConfig(setup.expectedFormatClass(), vectorsFormat);
    Assert.assertEquals(setup.maxEdges(), hnswOptions.maxEdges());
    Assert.assertEquals(setup.numEdgeCandidates(), hnswOptions.numEdgeCandidates());
  }

  @Test
  public void getKnnVectorsFormatForField_withWrongType_producesDefaultFormat() {
    FieldPath path = FieldPath.parse("vector");
    VectorFieldSpecification specification = createVectorFieldDefinition(48, 566);

    LuceneCodec codec = new LuceneCodec(Map.of(path, specification));
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();

    assertThrows(
        IllegalStateException.class,
        () ->
            format.getKnnVectorsFormatForField(
                NUMBER_INT64.getLuceneFieldName(path, Optional.empty())));
  }

  @Test
  public void getKnnVectorsFormatForField_withUnknownPath_producesDefaultFormat()
      throws NoSuchFieldException, IllegalAccessException {
    FieldPath path = FieldPath.parse("vector");

    LuceneCodec codec = new LuceneCodec();
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();
    KnnVectorsFormat vectorsFormat =
        format.getKnnVectorsFormatForField(KNN_VECTOR.getLuceneFieldName(path, Optional.empty()));

    Assert.assertTrue(vectorsFormat instanceof Lucene99HnswVectorsFormat);
    VectorFieldSpecification.HnswOptions hnswOptions =
        getKnnFieldConfig(Lucene99HnswVectorsFormat.class, vectorsFormat);
    Assert.assertEquals(DEFAULT_MAX_CONN, hnswOptions.maxEdges());
    Assert.assertEquals(DEFAULT_BEAM_WIDTH, hnswOptions.numEdgeCandidates());
  }

  @Test
  public void getKnnVectorsFormatForField_withUnresolvedTypeField_producesDefaultFormat() {
    FieldPath path = FieldPath.parse("vector");
    VectorFieldSpecification specification = createVectorFieldDefinition(48, 566);

    LuceneCodec codec = new LuceneCodec(Map.of(path, specification));
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();

    assertThrows(AssertionError.class, () -> format.getKnnVectorsFormatForField(path.toString()));
  }

  private VectorFieldSpecification createVectorFieldDefinition(
      int maxEdges, int numEdgeCandidates) {
    return new VectorFieldSpecification(
        1024,
        VectorSimilarity.DOT_PRODUCT,
        VectorQuantization.NONE,
        new VectorIndexingAlgorithm.HnswIndexingAlgorithm(
            new VectorFieldSpecification.HnswOptions(maxEdges, numEdgeCandidates)));
  }

  private VectorFieldSpecification.HnswOptions getKnnFieldConfig(
      Class<? extends KnnVectorsFormat> knnFormatClass, KnnVectorsFormat format)
      throws NoSuchFieldException, IllegalAccessException {

    Field maxConnField = knnFormatClass.getDeclaredField("maxConn");
    maxConnField.setAccessible(true);
    int maxConn = (int) maxConnField.get(format);

    Field beamWidthField = knnFormatClass.getDeclaredField("beamWidth");
    beamWidthField.setAccessible(true);
    int beamWidth = (int) beamWidthField.get(format);

    return new VectorFieldSpecification.HnswOptions(maxConn, beamWidth);
  }

  @Test
  public void
      getKnnVectorsFormatForField_withFlatAlgo_producesFloat32AndByteFlatFormat_forKnnVector() {
    FieldPath path = FieldPath.parse("vector");
    VectorFieldSpecification specification = createFlatVectorFieldDefinition();

    LuceneCodec codec = new LuceneCodec(Map.of(path, specification));
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();
    KnnVectorsFormat vectorsFormat =
        format.getKnnVectorsFormatForField(KNN_VECTOR.getLuceneFieldName(path, Optional.empty()));

    Assert.assertTrue(vectorsFormat instanceof Float32AndByteFlatVectorsFormat);
  }

  @Test
  public void
      getKnnVectorsFormatForField_withFlatAlgo_producesFloat32AndByteFlatFormat_forKnnByte() {
    FieldPath path = FieldPath.parse("vector");
    VectorFieldSpecification specification = createFlatVectorFieldDefinition();

    LuceneCodec codec = new LuceneCodec(Map.of(path, specification));
    PerFieldKnnVectorsFormat format = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();
    KnnVectorsFormat vectorsFormat =
        format.getKnnVectorsFormatForField(KNN_BYTE.getLuceneFieldName(path, Optional.empty()));

    Assert.assertTrue(vectorsFormat instanceof Float32AndByteFlatVectorsFormat);
  }

  /**
   * Checks every subformat of LuceneCodec to its expected implementation. This is an intentional
   * change-detector test: it should break whenever a Lucene upgrade changes a codec subformat.
   * Breaking this test means a codec subformat changed. This could break forward compatibility that
   * old subformats cannot read segments written by newer versions, which means rollbacks will
   * require reindexing.
   *
   * <p>For the per-field formats (postings, docValues, knnVectors), we check both the wrapper class
   * and its registered name (written into segment metadata), as well as the underlying per-field
   * delegate class. For the remaining formats, we check the implementation class.
   */
  @Test
  public void subformatNames_defaultCodec_matchExpectedLucene99Formats() {
    LuceneCodec codec = new LuceneCodec();

    assertEquals("Lucene99", codec.getName());

    // Per-field formats: check wrapper class, registered name, and underlying delegate.
    // The registered name is written into FieldInfo attributes and must stay stable across
    // upgrades.
    assertThat(codec.postingsFormat()).isInstanceOf(PerFieldPostingsFormat.class);
    PerFieldPostingsFormat postingsFormat = (PerFieldPostingsFormat) codec.postingsFormat();
    assertEquals("PerField40", postingsFormat.getName());
    assertThat(postingsFormat.getPostingsFormatForField("any"))
        .isInstanceOf(Lucene99PostingsFormat.class);

    assertThat(codec.docValuesFormat()).isInstanceOf(PerFieldDocValuesFormat.class);
    PerFieldDocValuesFormat docValuesFormat = (PerFieldDocValuesFormat) codec.docValuesFormat();
    assertEquals("PerFieldDV40", docValuesFormat.getName());
    assertThat(docValuesFormat.getDocValuesFormatForField("any"))
        .isInstanceOf(Lucene90DocValuesFormat.class);

    // knnVectorsFormat requires a typed field name because LuceneCodec.getKnnVectorsFormatForField
    // parses the type prefix to resolve the format.
    assertThat(codec.knnVectorsFormat()).isInstanceOf(PerFieldKnnVectorsFormat.class);
    PerFieldKnnVectorsFormat knnVectorsFormat = (PerFieldKnnVectorsFormat) codec.knnVectorsFormat();
    assertEquals("PerFieldVectors90", knnVectorsFormat.getName());
    assertThat(
            knnVectorsFormat.getKnnVectorsFormatForField(
                KNN_VECTOR.getLuceneFieldName(FieldPath.parse("any"), Optional.empty())))
        .isInstanceOf(Lucene99HnswVectorsFormat.class);

    // Remaining formats delegated from Lucene99Codec (except storedFieldsFormat, which LuceneCodec
    // overrides with its own LuceneStoredFieldsFormat).
    assertThat(codec.storedFieldsFormat()).isInstanceOf(LuceneStoredFieldsFormat.class);
    assertThat(codec.fieldInfosFormat()).isInstanceOf(Lucene94FieldInfosFormatV1.class);
    assertThat(codec.segmentInfoFormat()).isInstanceOf(Lucene99SegmentInfoFormat.class);
    assertThat(codec.termVectorsFormat()).isInstanceOf(Lucene90TermVectorsFormat.class);
    assertThat(codec.liveDocsFormat()).isInstanceOf(Lucene90LiveDocsFormat.class);
    assertThat(codec.compoundFormat()).isInstanceOf(Lucene90CompoundFormat.class);
    assertThat(codec.normsFormat()).isInstanceOf(Lucene90NormsFormat.class);
    assertThat(codec.pointsFormat()).isInstanceOf(Lucene90PointsFormat.class);
  }

  @Test
  public void postingsFormat_idField_bloomFilterEnabled_returnsBloomFilterPostingsFormat() {
    testPostingsFormat(
        true, FieldName.MetaField.ID.getLuceneFieldName(), BloomFilteringPostingsFormat.class);
  }

  @Test
  public void postingsFormat_idField_bloomFilterDisabled_returnsDefaultPostingsFormat() {
    testPostingsFormat(
        false, FieldName.MetaField.ID.getLuceneFieldName(), Lucene99PostingsFormat.class);
  }

  @Test
  public void postingsFormat_nonIdField_bloomFilterEnabled_returnsDefaultPostingsFormat() {
    testPostingsFormat(true, "someOtherField", Lucene99PostingsFormat.class);
  }

  @Test
  public void postingsFormat_idField_dynamicToggle_switchesFormatAtRuntime() {
    AtomicBoolean bloomEnabled = new AtomicBoolean(false);
    LuceneCodec codec =
        LuceneCodec.Factory.forIndexWithBloomFilter(Map.of(), bloomEnabled::get, Optional.empty());
    PerFieldPostingsFormat format = (PerFieldPostingsFormat) codec.postingsFormat();
    String idField = FieldName.MetaField.ID.getLuceneFieldName();

    assertThat(format.getPostingsFormatForField(idField))
        .isInstanceOf(Lucene99PostingsFormat.class);

    bloomEnabled.set(true);
    assertThat(format.getPostingsFormatForField(idField))
        .isInstanceOf(BloomFilteringPostingsFormat.class);

    bloomEnabled.set(false);
    assertThat(format.getPostingsFormatForField(idField))
        .isInstanceOf(Lucene99PostingsFormat.class);
  }

  /** NOTE - Critical to performance */
  @Test
  public void postingsFormat_bloomFilterDelegate_reusesPostingsFormatFromDelegateCodec()
      throws NoSuchFieldException, IllegalAccessException {
    LuceneCodec codec =
        LuceneCodec.Factory.forIndexWithBloomFilter(Map.of(), () -> true, Optional.empty());
    PerFieldPostingsFormat format = (PerFieldPostingsFormat) codec.postingsFormat();
    String idField = FieldName.MetaField.ID.getLuceneFieldName();

    PostingsFormat bloomFormat = format.getPostingsFormatForField(idField);
    assertThat(bloomFormat).isInstanceOf(BloomFilteringPostingsFormat.class);

    Field delegateField =
        BloomFilteringPostingsFormat.class.getDeclaredField("delegatePostingsFormat");
    delegateField.setAccessible(true);
    PostingsFormat bloomDelegate = (PostingsFormat) delegateField.get(bloomFormat);

    PostingsFormat defaultFormat = format.getPostingsFormatForField("someOtherField");

    assertThat(bloomDelegate).isSameInstanceAs(defaultFormat);
  }

  @Test
  public void postingsFormat_idField_withMetricsUpdater_incrementsBloomPostingCounter() {
    var metricsUpdater = SearchIndex.mockIndexingMetricsUpdater(IndexDefinition.Type.SEARCH);
    LuceneCodec codec =
        LuceneCodec.Factory.forIndexWithBloomFilter(
            Map.of(), () -> true, Optional.of(metricsUpdater));
    PerFieldPostingsFormat format = (PerFieldPostingsFormat) codec.postingsFormat();
    String idField = FieldName.MetaField.ID.getLuceneFieldName();

    assertThat(metricsUpdater.getBloomFilterIdPostingCreatedCounter().count()).isEqualTo(0.0);
    PostingsFormat bloomFormat = format.getPostingsFormatForField(idField);
    assertThat(bloomFormat).isInstanceOf(BloomFilteringPostingsFormat.class);
    assertThat(metricsUpdater.getBloomFilterIdPostingCreatedCounter().count()).isEqualTo(1.0);
    assertEquals(BloomFilteringPostingsFormat.BLOOM_CODEC_NAME, bloomFormat.getName());
  }

  @Test
  public void postingsFormat_idField_bloomDisabled_withMetrics_incrementsLucene99PostingCounter() {
    var metricsUpdater = SearchIndex.mockIndexingMetricsUpdater(IndexDefinition.Type.SEARCH);
    LuceneCodec codec =
        LuceneCodec.Factory.forIndexWithBloomFilter(
            Map.of(), () -> false, Optional.of(metricsUpdater));
    PerFieldPostingsFormat format = (PerFieldPostingsFormat) codec.postingsFormat();
    String idField = FieldName.MetaField.ID.getLuceneFieldName();

    assertThat(metricsUpdater.getLucene99IdPostingCreatedCounter().count()).isEqualTo(0.0);
    PostingsFormat defaultFormat = format.getPostingsFormatForField(idField);
    assertThat(defaultFormat).isInstanceOf(Lucene99PostingsFormat.class);
    assertThat(metricsUpdater.getLucene99IdPostingCreatedCounter().count()).isEqualTo(1.0);
    assertThat(defaultFormat).isNotInstanceOf(BloomFilteringPostingsFormat.class);
    assertThat(defaultFormat.getName()).isNotEqualTo(BloomFilteringPostingsFormat.BLOOM_CODEC_NAME);
  }

  private static void testPostingsFormat(
      boolean isBloomEnabled, String fieldName, Class<? extends PostingsFormat> expectedType) {
    LuceneCodec codec =
        LuceneCodec.Factory.forIndexWithBloomFilter(
            Map.of(), () -> isBloomEnabled, Optional.empty());
    PerFieldPostingsFormat format = (PerFieldPostingsFormat) codec.postingsFormat();

    PostingsFormat idFormat = format.getPostingsFormatForField(fieldName);

    assertThat(idFormat).isInstanceOf(expectedType);
  }

  private VectorFieldSpecification createFlatVectorFieldDefinition() {
    return new VectorFieldSpecification(
        1024,
        VectorSimilarity.DOT_PRODUCT,
        VectorQuantization.NONE,
        new VectorIndexingAlgorithm.FlatIndexingAlgorithm());
  }
}
