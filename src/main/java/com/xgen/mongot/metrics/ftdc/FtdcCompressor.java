package com.xgen.mongot.metrics.ftdc;

import static com.xgen.mongot.util.Check.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Var;
import com.xgen.mongot.util.Check;
import com.xgen.mongot.util.bson.ByteUtils;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bson.BsonBinary;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;

/** Serialize and compress metric chunks. */
public class FtdcCompressor {
  /** Compresses an FTDC metric chunk to a bson document. */
  @VisibleForTesting
  public static BsonDocument compressChunk(MetricChunk chunk) {
    byte[] uncompressed = encodeChunk(chunk.schema(), chunk.metrics());

    byte[] compressed = ZlibUtil.zlibCompress(uncompressed);

    // the length of the encoded chunk followed by the compressed encoded chunk.
    byte[] data =
        new BinaryWriter()
            .writeInt32LittleEndian(uncompressed.length)
            .write(compressed)
            .asByteArray();

    return new BsonDocument()
        .append("_id", new BsonDateTime(chunk.epochTime()))
        .append("type", DocumentType.METRIC_CHUNK_TYPE)
        .append("data", new BsonBinary(data));
  }

  private static byte[] encodeChunk(
      BsonDocument schema, LinkedHashMap<String, List<Long>> metrics) {
    BinaryWriter writer = new BinaryWriter();

    writer.writeDocument(schema);

    writeDataDimensions(writer, metrics);

    encodeSamples(writer, metrics);

    return writer.asByteArray();
  }

  private static void writeDataDimensions(
      BinaryWriter writer, LinkedHashMap<String, List<Long>> metrics) {
    // write #metrics and #deltas in little endian
    writer.writeInt32LittleEndian(metrics.size());

    // the number of deltas is 1 less than the number of samples
    int deltaLength = numberOfSamples(metrics) - 1;
    writer.writeInt32LittleEndian(deltaLength);
  }

  private static int numberOfSamples(Map<String, List<Long>> metrics) {
    Optional<List<Long>> metricSamples = metrics.values().stream().findFirst();

    checkState(metricSamples.isPresent(), "metric chunk must contain at least one metric");
    // It is possible to compress 1 sample, the delta array will be empty.
    checkState(metricSamples.get().size() > 0, "metric chunk must contain at least one sample");

    return metricSamples.get().size();
  }

  private static void encodeSamples(
      BinaryWriter writer, LinkedHashMap<String, List<Long>> metrics) {
    // the samples of each metric are delta encoded, zero run encoded, then long-packed
    for (List<Long> values : metrics.values()) {
      // The order of keys in metrics is consistent with the schema, so iterating through the keys
      // is enough.
      List<Long> deltas = deltaEncoding(values);
      List<Long> zeroEncoded = encodeZeroRuns(deltas);

      writer.packLongs(zeroEncoded);
    }
  }

  /** Encodes as deltas, note that the returned list will be smaller by 1 element. */
  private static List<Long> deltaEncoding(List<Long> values) {
    Check.checkState(values.size() > 0, "tried to compress an empty sample");

    return IntStream.range(1, values.size())
        .mapToObj(i -> values.get(i) - values.get(i - 1))
        .collect(Collectors.toList());
  }

  /**
   * encodes sequences of zeroes as: ...0, #zeroes - 1...
   *
   * <p>for instance: [1, 0, 0, 0, 7] will be encoded to [1, 0, 2, 7].
   */
  private static List<Long> encodeZeroRuns(List<Long> numbers) {
    @Var var zeroRunLen = 0;
    ArrayList<Long> encoded = new ArrayList<>();

    for (Long value : numbers) {
      if (value == 0) {
        zeroRunLen++;

      } else {
        if (zeroRunLen > 0) {
          // finish the zero run from before
          encoded.add(0L);
          encoded.add(zeroRunLen - 1L);
          zeroRunLen = 0;
        }

        encoded.add(value);
      }
    }

    // numbers ended with a zero
    if (zeroRunLen > 0) {
      encoded.add(0L);
      encoded.add(zeroRunLen - 1L);
    }

    return encoded;
  }

  static class BinaryWriter {
    private final ByteArrayOutputStream buffer;

    BinaryWriter() {
      this.buffer = new ByteArrayOutputStream();
    }

    private byte[] asByteArray() {
      return this.buffer.toByteArray();
    }

    private BinaryWriter writeDocument(BsonDocument document) {
      var schemaBytes = ByteUtils.toByteArray(document);
      return write(schemaBytes);
    }

    private BinaryWriter writeInt32LittleEndian(int n) {
      // use a ByteBuffer to convert integers to bytes
      byte[] bytes = new byte[4];
      ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(n);
      return write(bytes);
    }

    private BinaryWriter write(byte[] bytes) {
      this.buffer.write(bytes, 0, bytes.length);
      return this;
    }

    private BinaryWriter packLongs(List<Long> zeroEncoded) {
      for (Long l : zeroEncoded) {
        LongPacker.packInto(l, this.buffer);
      }
      return this;
    }
  }
}
