package com.xgen.testing.mongot.mock.index;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.xgen.mongot.index.ReaderClosedException;
import com.xgen.mongot.index.VectorSearchResult;
import com.xgen.mongot.index.query.InvalidQueryException;
import com.xgen.mongot.index.query.MaterializedVectorSearchQuery;
import com.xgen.mongot.index.query.QueryExecutionContext;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bson.BsonArray;
import org.bson.BsonInt32;

public class VectorIndexReader {

  public static com.xgen.mongot.index.VectorIndexReader mockIndexReader() {
    return mockIndexReader(Optional.empty());
  }

  public static com.xgen.mongot.index.VectorIndexReader mockIndexReader(int numResults) {
    return mockIndexReader(Optional.of(numResults));
  }

  public static com.xgen.mongot.index.VectorIndexReader mockIndexReader(
      Optional<Integer> numResults) {
    var indexReader = mock(com.xgen.mongot.index.VectorIndexReader.class);

    try {
      lenient()
          .when(
              indexReader.query(
                  any(MaterializedVectorSearchQuery.class), any(QueryExecutionContext.class)))
          .thenAnswer(ignored -> generateResults(numResults));
    } catch (IOException | InvalidQueryException | ReaderClosedException e) {
      throw new RuntimeException(e);
    }

    return indexReader;
  }

  private static BsonArray generateResults(Optional<Integer> numResults) {
    var array = new BsonArray();

    if (numResults.isEmpty()) {
      return array;
    }

    for (int i = 0; i < numResults.get(); i++) {
      array.add(
          new VectorSearchResult(
                  new BsonInt32(i), ThreadLocalRandom.current().nextFloat(), Optional.empty())
              .toRawBson());
    }

    return array;
  }
}
