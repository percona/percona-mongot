load("@rules_java//java:defs.bzl", "java_library")

# gazelle:ignore

# Use native java_library because we cannot use NullAway to build this class since this class
# needs to be built to run NullAway
java_library(
    name = "errorprone",
    srcs = [
        "CustomNullAwayLibraryModel.java",
    ],
    plugins = ["//bazel/java:autoservice_annotation_processor"],
    visibility = ["//:__subpackages__"],
    deps = [
        "@maven//:com_google_auto_service_auto_service_annotations",
        "@maven//:com_google_guava_guava",
        "@maven//:com_uber_nullaway_nullaway",
        "@maven//:org_jetbrains_annotations",
    ],
)
