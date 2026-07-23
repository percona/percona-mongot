load("//bazel/java:test.bzl", "java_test_suite")

java_test_suite(
    name = "config",
    srcs = [
        "EmbeddingModelConfigTest.java",
        "EmbeddingServiceConfigTest.java",
        "OpenAiApiSchemaTest.java",
        "VoyageApiSchemaTest.java",
        "VoyageModelConfigTest.java",
    ],
    data = [
        "//src/test/unit/resources/embedding/providers/config:srcs",
    ],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/embedding/providers/clients",
        "//src/main/java/com/xgen/mongot/embedding/providers/configs",
        "//src/main/java/com/xgen/mongot/index/definition/quantization",
        "//src/main/java/com/xgen/mongot/util/bson",
        "//src/main/java/com/xgen/mongot/util/bson/parser",
        "//src/main/java/com/xgen/testing",
        "@maven//:junit_junit",
        "@maven//:org_mongodb_bson",
    ],
)
