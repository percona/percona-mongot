load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "weights",
    srcs = [
        "EmptyWeight.java",
        "SafeBlockJoinWeight.java",
        "SafePhraseWeight.java",
        "SafeTermWeight.java",
        "WrappedExplainWeight.java",
    ],
    visibility = ["//visibility:public"],
    exports = ["@maven//:org_apache_lucene_lucene_core"],
    deps = [
        "//src/main/java/com/xgen/mongot/index/lucene/query/util",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_lucene_lucene_core",
        "@maven//:org_apache_lucene_lucene_join",
        "@maven//:org_apache_lucene_lucene_queries",
        "@maven//:org_apache_lucene_lucene_sandbox",
        "@maven//:org_jetbrains_annotations",
    ],
)
