load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "profiler",
    srcs = [
        "ProfileCollectorManager.java",
        "ProfileDocIdSetIterator.java",
        "ProfileLeafCollector.java",
        "ProfileScorer.java",
        "ProfileWeight.java",
        "QueryProfiler.java",
        "QueryProfilerFeatureExplainer.java",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "//src/main/java/com/xgen/mongot/index/lucene/explain/information",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/query",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/timing",
        "@maven//:org_apache_lucene_lucene_core",
    ],
    deps = [
        "//src/main/java/com/xgen/mongot/index",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/information",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/information/creator",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/query",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/timing",
        "//src/main/java/com/xgen/mongot/index/lucene/explain/tracing",
        "//src/main/java/com/xgen/mongot/util",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_lucene_lucene_core",
        "@maven//:org_jetbrains_annotations",
        "@maven//:org_slf4j_slf4j_api",
    ],
)
