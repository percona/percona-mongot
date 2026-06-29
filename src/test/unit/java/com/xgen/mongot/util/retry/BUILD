load("//bazel/java:test.bzl", "java_test_suite")

java_test_suite(
    name = "retry",
    srcs = [
        "ActionRetryTest.java",
        "BackoffPolicyDelayForTest.java",
        "BackoffRetryExecutorTest.java",
    ],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/util/retry",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)
