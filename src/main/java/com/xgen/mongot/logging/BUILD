load("//bazel/java:package.bzl", "java_library")

java_library(
    name = "logging",
    srcs = [
        "DefaultKeyValueLogger.java",
        "Logging.java",
        "OmitEmptyFieldJsonDecorator.java",
        "SafeKeyValuePairsJsonProvider.java",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:ch_qos_logback_logback_classic",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:org_slf4j_slf4j_api",
    ],
    deps = [
        "@maven//:ch_qos_logback_logback_classic",
        "@maven//:ch_qos_logback_logback_core",
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_google_errorprone_error_prone_annotations",
        "@maven//:net_logstash_logback_logstash_logback_encoder",
        "@maven//:org_jetbrains_annotations",
        "@maven//:org_slf4j_jul_to_slf4j",
        "@maven//:org_slf4j_slf4j_api",
    ],
)
