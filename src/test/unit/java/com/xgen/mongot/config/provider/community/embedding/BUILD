load("//bazel/java:test.bzl", "java_test_suite")

# gazelle:ignore

java_test_suite(
    name = "embedding",
    srcs = [
        "EmbeddingServiceManagerConfigTest.java",
    ],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/config/provider/community/embedding",
        "//src/main/java/com/xgen/mongot/embedding/providers/configs",
        "@maven//:ch_qos_logback_logback_classic",
        "@maven//:ch_qos_logback_logback_core",
        "@maven//:junit_junit",
        "@maven//:org_slf4j_slf4j_api",
    ],
)
