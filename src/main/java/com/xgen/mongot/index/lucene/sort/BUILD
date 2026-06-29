load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "sort",
    srcs = [
        "LuceneIndexSortCompatibilityValidator.java",
        "LuceneIndexSortFactory.java",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:org_apache_lucene_lucene_core",
    ],
    deps = [
        "//src/main/java/com/xgen/mongot/index/definition",
        "//src/main/java/com/xgen/mongot/index/lucene/field",
        "//src/main/java/com/xgen/mongot/index/lucene/query/sort",
        "//src/main/java/com/xgen/mongot/index/lucene/query/sort/mixed",
        "//src/main/java/com/xgen/mongot/index/query/sort",
        "//src/main/java/com/xgen/mongot/logging",
        "//src/main/java/com/xgen/mongot/util",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_lucene_lucene_core",
    ],
)
