load("//bazel/java:test.bzl", "java_test_suite")

java_test_suite(
    name = "codec",
    # Note: LuceneStoredFieldsFormatTest needs "moderate" timeout, but java_test_suite
    # applies timeout uniformly, so setting moderate for the suite.
    timeout = "moderate",
    srcs = [
        "LuceneCodecBloomFilterTest.java",
        "LuceneCodecTest.java",
        "LuceneStoredFieldsFormatTest.java",
    ],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/index",
        "//src/main/java/com/xgen/mongot/index/definition",
        "//src/main/java/com/xgen/mongot/index/lucene/codec",
        "//src/main/java/com/xgen/mongot/index/lucene/codec/flat",
        "//src/main/java/com/xgen/mongot/index/lucene/field",
        "//src/main/java/com/xgen/mongot/index/lucene/quantization",
        "//src/main/java/com/xgen/mongot/index/lucene/util",
        "//src/main/java/com/xgen/mongot/index/lucene/vector",
        "//src/main/java/com/xgen/mongot/util",
        "//src/main/java/com/xgen/testing/mongot/mock/index",
        "@lucene_fork//:org_apache_lucene_lucene_codecs",
        "@maven//:com_google_flogger_flogger_system_backend",
        "@maven//:com_google_truth_truth",
        "@maven//:io_micrometer_micrometer_core",
        "@maven//:junit_junit",
        "@maven//:org_apache_lucene_lucene_backward_codecs",
        "@maven//:org_apache_lucene_lucene_core",
        "@maven//:org_apache_lucene_lucene_test_framework",
    ],
)
