load("//bazel/java:test.bzl", "java_test_suite", "mongot_java_unit_test_suite")

mongot_java_unit_test_suite(
    name = "lib",
    exclude_files = ["ScrapeCacheTest.java"],
    deps = [
        "//src/main/java/com/xgen/mongot/metrics/cache",
        "@maven//:junit_junit",
    ],
)

java_test_suite(
    name = "cache",
    srcs = ["ScrapeCacheTest.java"],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/metrics/cache",
        "@maven//:junit_junit",
    ],
)
