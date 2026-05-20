load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "bloom",
    srcs = ["MongotBloomFilteringPostingsFormat.java"],
    # Cloned from Lucene; exclude from Error Prone (paths use slashes, not package dots).
    javacopts = ["-XepExcludedPaths:.*/codec/bloom/MongotBloomFilteringPostingsFormat\\.java"],
    visibility = ["//:__subpackages__"],
    exports = ["@maven//:org_apache_lucene_lucene_core"],
    deps = [
        "@maven//:com_google_auto_service_auto_service_annotations",
        "@maven//:org_apache_lucene_lucene_codecs",
        "@maven//:org_apache_lucene_lucene_core",
    ],
)
