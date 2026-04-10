load("//bazel/java:test.bzl", "java_test_suite")

java_test_suite(
    name = "mongodb",
    srcs = ["MaterializedViewWriterTest.java"],
    tags = ["unit"],
    deps = [
        "//src/main/java/com/xgen/mongot/embedding/config",
        "//src/main/java/com/xgen/mongot/embedding/exceptions",
        "//src/main/java/com/xgen/mongot/embedding/mongodb/common",
        "//src/main/java/com/xgen/mongot/embedding/mongodb/leasing",
        "//src/main/java/com/xgen/mongot/embedding/utils",
        "//src/main/java/com/xgen/mongot/index",
        "//src/main/java/com/xgen/mongot/index/mongodb",
        "//src/main/java/com/xgen/mongot/index/version",
        "//src/main/java/com/xgen/mongot/metrics",
        "//src/main/java/com/xgen/mongot/util",
        "//src/main/java/com/xgen/mongot/util/bson",
        "//src/main/java/com/xgen/mongot/util/mongodb",
        "//src/main/java/com/xgen/testing/mongot/index/definition",
        "@maven//:com_google_flogger_flogger",  # keep: flogger for libs under test
        "@maven//:com_google_flogger_flogger_slf4j_backend",  # keep: flogger for libs under test
        "@maven//:com_google_guava_guava",
        "@maven//:io_micrometer_micrometer_core",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mongodb_bson",
        "@maven//:org_mongodb_mongodb_driver_core",
        "@maven//:org_mongodb_mongodb_driver_sync",
    ],
)
