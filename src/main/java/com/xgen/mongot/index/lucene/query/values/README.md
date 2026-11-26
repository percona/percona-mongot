## Package Description

The `values` package contains subclasses of Lucene's [DoubleValuesSource](https://lucene.apache.org/core/8_1_1/core/org/apache/lucene/search/DoubleValuesSource.html) 
and [DoubleValues](https://lucene.apache.org/core/6_4_0/core/org/apache/lucene/search/DoubleValues.html) classes.
Each of the classes in the `values` package represents a type of [Function Score Expression](https://github.com/10gen/mongot-public-preview/blob/master/docs/syntax/query.md#expressions).

## How Undefined Behavior is Handled

An expression evaluates to **undefined** if it evaluates to `Double.NaN` or `Double.NEGATIVE_INFINITY`.

The `DoubleValues` returned from `getValues()` implements `DoubleValues::doubleValue`, which is where the expression of interest 
is evaluated on a per-document basis during scoring. `DoubleValues::doubleValue` can return a non-finite `double`, and is also
where the propagation of undefined occurs.

Once the top-level expression's `DoubleValues::doubleValue` is evaluated, the scorer, which is a Lucene `FunctionScoreWeight`,
 [converts](https://github.com/apache/lucene/blob/8b68bc744fa4a53e7981277bfb1a1c8e70e4ccee/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionScoreQuery.java#L245-L246)
the final score of a document to 0 if its function score evaluates to undefined.
