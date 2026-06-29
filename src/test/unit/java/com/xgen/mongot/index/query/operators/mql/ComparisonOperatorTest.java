package com.xgen.mongot.index.query.operators.mql;

import static com.xgen.testing.BsonDeserializationTestSuite.fromDocument;

import com.xgen.mongot.index.query.operators.value.NullValue;
import com.xgen.mongot.util.bson.parser.BsonParseException;
import com.xgen.testing.BsonDeserializationTestSuite;
import com.xgen.testing.BsonSerializationTestSuite;
import com.xgen.testing.mongot.index.query.operators.mql.MqlFilterOperatorBuilder;
import com.xgen.testing.mongot.index.query.operators.mql.ValueBuilder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses(
    value = {
      ComparisonOperatorTest.DeserializationTest.class,
      ComparisonOperatorTest.SerializationTest.class
    })
public class ComparisonOperatorTest {

  @RunWith(Parameterized.class)
  public static class DeserializationTest {
    private static final String SUITE_NAME = "comparison-operator-deserialization";
    private static final BsonDeserializationTestSuite<ComparisonOperator> TEST_SUITE =
        fromDocument(ClauseTests.RESOURCES_PATH, SUITE_NAME, ComparisonOperator::fromBson);

    private final BsonDeserializationTestSuite.TestSpecWrapper<ComparisonOperator> testSpec;

    public DeserializationTest(
        BsonDeserializationTestSuite.TestSpecWrapper<ComparisonOperator> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonDeserializationTestSuite.TestSpecWrapper<ComparisonOperator>> data()
        throws BsonParseException {
      return TEST_SUITE.withExamples(gt(), gte(), lt(), lte(), eq(), eqNull(), ne(), in(), nin());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> gt()
        throws BsonParseException {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "gt", MqlFilterOperatorBuilder.gt().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> gte()
        throws BsonParseException {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "gte", MqlFilterOperatorBuilder.gte().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> lt()
        throws BsonParseException {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "lt", MqlFilterOperatorBuilder.lt().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> lte()
        throws BsonParseException {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "lte", MqlFilterOperatorBuilder.lte().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> eq() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "eq", MqlFilterOperatorBuilder.eq().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> eqNull() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "eq-null", MqlFilterOperatorBuilder.eq().value(new NullValue()).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> ne() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "ne", MqlFilterOperatorBuilder.ne().value(ValueBuilder.intNumber(1)).build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> in() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "in",
          MqlFilterOperatorBuilder.in()
              .values(
                  Arrays.asList(
                      ValueBuilder.intNumber(1),
                      ValueBuilder.intNumber(2),
                      ValueBuilder.intNumber(3),
                      ValueBuilder.intNumber(4)))
              .build());
    }

    private static BsonDeserializationTestSuite.ValidSpec<ComparisonOperator> nin() {
      return BsonDeserializationTestSuite.TestSpec.valid(
          "nin",
          MqlFilterOperatorBuilder.nin()
              .values(
                  Arrays.asList(
                      ValueBuilder.intNumber(1),
                      ValueBuilder.intNumber(2),
                      ValueBuilder.intNumber(3),
                      ValueBuilder.intNumber(4)))
              .build());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }
  }

  @RunWith(Parameterized.class)
  public static class SerializationTest {
    private static final String SUITE_NAME = "comparison-operator-serialization";
    private static final BsonSerializationTestSuite<ComparisonOperator> TEST_SUITE =
        BsonSerializationTestSuite.fromEncodable(ClauseTests.RESOURCES_PATH, SUITE_NAME);

    private final BsonSerializationTestSuite.TestSpec<ComparisonOperator> testSpec;

    public SerializationTest(BsonSerializationTestSuite.TestSpec<ComparisonOperator> testSpec) {
      this.testSpec = testSpec;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<BsonSerializationTestSuite.TestSpec<ComparisonOperator>> data()
        throws BsonParseException {
      return Arrays.asList(gt(), gte(), lt(), lte(), eq(), ne(), in(), nin());
    }

    @Test
    public void runTest() throws Exception {
      TEST_SUITE.runTest(this.testSpec);
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> gt()
        throws BsonParseException {
      return BsonSerializationTestSuite.TestSpec.create(
          "gt", MqlFilterOperatorBuilder.gt().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> gte()
        throws BsonParseException {
      return BsonSerializationTestSuite.TestSpec.create(
          "gte", MqlFilterOperatorBuilder.gte().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> lt()
        throws BsonParseException {
      return BsonSerializationTestSuite.TestSpec.create(
          "lt", MqlFilterOperatorBuilder.lt().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> lte()
        throws BsonParseException {
      return BsonSerializationTestSuite.TestSpec.create(
          "lte", MqlFilterOperatorBuilder.lte().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> eq() {
      return BsonSerializationTestSuite.TestSpec.create(
          "eq", MqlFilterOperatorBuilder.eq().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> ne() {
      return BsonSerializationTestSuite.TestSpec.create(
          "ne", MqlFilterOperatorBuilder.ne().value(ValueBuilder.doubleNumber(1)).build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> in() {
      return BsonSerializationTestSuite.TestSpec.create(
          "in",
          MqlFilterOperatorBuilder.in()
              .values(
                  Arrays.asList(
                      ValueBuilder.doubleNumber(1),
                      ValueBuilder.doubleNumber(2),
                      ValueBuilder.doubleNumber(3),
                      ValueBuilder.doubleNumber(4)))
              .build());
    }

    private static BsonSerializationTestSuite.TestSpec<ComparisonOperator> nin() {
      return BsonSerializationTestSuite.TestSpec.create(
          "nin",
          MqlFilterOperatorBuilder.nin()
              .values(
                  Arrays.asList(
                      ValueBuilder.doubleNumber(1),
                      ValueBuilder.doubleNumber(2),
                      ValueBuilder.doubleNumber(3),
                      ValueBuilder.doubleNumber(4)))
              .build());
    }
  }
}
