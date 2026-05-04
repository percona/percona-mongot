package com.xgen.mongot.index.query;

import static com.xgen.testing.BsonDeserializationTestSuite.fromRootDocument;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.xgen.mongot.index.query.sort.SequenceToken;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.mongot.index.query.OperatorQueryBuilder;
import com.xgen.testing.mongot.index.query.operators.OperatorBuilder;
import org.apache.lucene.search.FieldDoc;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(value = {PaginationTest.DeserializationTest.class})
public class PaginationTest {
  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "pagination-deserialization";
    private static final BsonDeserializationTestSuite<SearchQuery> TEST_SUITE =
        fromRootDocument("src/test/unit/resources/index/query/", SUITE_NAME, SearchQuery::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<SearchQuery> testSpec;

    public DeserializationTest(BsonDeserializationTestSuite.TestSpecWrapper<SearchQuery> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<SearchQuery>> data() {
      return TEST_SUITE.withExamples(searchBefore(), searchAfter(), neitherAfterOrBefore());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonDeserializationTestSuite.ValidSpec<SearchQuery> searchBefore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "searchBefore is present",
          (query) -> {
            assertTrue(query.pagination().isPresent());
            assertEquals(Pagination.Type.SEARCH_BEFORE, query.pagination().get().type());
            assertNotNull(query.pagination().get().sequenceToken());

            BsonValue id = new BsonString("test");
            Assert.assertEquals(
                query,
                OperatorQueryBuilder.builder()
                    .searchBefore(SequenceToken.of(id, new FieldDoc(1, 2, new Object[] {2f})))
                    .operator(OperatorBuilder.text().path("title").query("godfather").build())
                    .returnStoredSource(false)
                    .build());
          });
    }

    private static BsonDeserializationTestSuite.ValidSpec<SearchQuery> searchAfter() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "searchAfter is present",
          (query) -> {
            assertTrue(query.pagination().isPresent());
            assertEquals(Pagination.Type.SEARCH_AFTER, query.pagination().get().type());
            assertNotNull(query.pagination().get().sequenceToken());
            BsonValue id = new BsonString("test");
            Assert.assertEquals(
                query,
                OperatorQueryBuilder.builder()
                    .searchAfter(SequenceToken.of(id, new FieldDoc(1, 2, new Object[] {2f})))
                    .operator(OperatorBuilder.text().path("title").query("godfather").build())
                    .returnStoredSource(false)
                    .build());
          });
    }

    private static BsonDeserializationTestSuite.ValidSpec<SearchQuery> neitherAfterOrBefore() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "neither searchBefore or searchAfter",
          (query) -> {
            assertFalse(query.pagination().isPresent());

            Assert.assertEquals(
                query,
                OperatorQueryBuilder.builder()
                    .operator(OperatorBuilder.text().path("title").query("godfather").build())
                    .returnStoredSource(false)
                    .build());
          });
    }
  }
}
