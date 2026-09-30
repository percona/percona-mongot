load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "collectors",
    testonly = True,
    srcs = [
        "CollectorBuilder.java",
        "FacetCollectorBuilder.java",
        "FacetDefinitionBuilder.java",
        "MetricDefinitionBuilder.java",
        "MetricsCollectorBuilder.java",
    ],
    visibility = ["//visibility:public"],
    exports = ["//src/main/java/com/xgen/mongot/index/query/collectors"],
    deps = [
        "//src/main/java/com/xgen/mongot/index/query/collectors",
        "//src/main/java/com/xgen/mongot/index/query/operators",
        "//src/main/java/com/xgen/mongot/util",
        "@maven//:org_mongodb_bson",
    ],
)
