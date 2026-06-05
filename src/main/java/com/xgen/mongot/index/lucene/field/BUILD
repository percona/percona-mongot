load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "field",
    srcs = [
        "FieldName.java",
        "FieldValue.java",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "//src/main/java/com/xgen/mongot/index/path/string",
        "//src/main/java/com/xgen/mongot/util",
    ],
    deps = [
        "//src/main/java/com/xgen/mongot/index/path/string",
        "//src/main/java/com/xgen/mongot/util",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_lucene_lucene_core",
        "@maven//:org_apache_lucene_lucene_facet",
        "@maven//:org_jetbrains_annotations",
    ],
)
