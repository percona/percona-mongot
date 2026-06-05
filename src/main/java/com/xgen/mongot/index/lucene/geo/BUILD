load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "geo",
    srcs = [
        "InvalidGeoPosition.java",
        "LuceneGeoTranslator.java",
        "LuceneGeometryFields.java",
    ],
    visibility = ["//visibility:public"],
    exports = ["@maven//:org_apache_lucene_lucene_core"],
    runtime_deps = [
        "@maven//:com_google_flogger_flogger_slf4j_backend",
    ],
    deps = [
        "//src/main/java/com/xgen/mongot/index",
        "//src/main/java/com/xgen/mongot/util",
        "//src/main/java/com/xgen/mongot/util/geo",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_lucene_lucene_core",
        "@maven//:org_jetbrains_annotations",
        "@maven//:org_mongodb_mongodb_driver_core",
        "@maven//:org_slf4j_slf4j_api",
    ],
)
