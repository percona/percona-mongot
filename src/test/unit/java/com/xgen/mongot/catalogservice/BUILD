load("//bazel/java:test.bzl", "mongot_java_unit_test_suite")

# gazelle:ignore

mongot_java_unit_test_suite(
    name = "lib",
    resources = True,
    deps = [
        "//src/main/java/com/xgen/mongot/catalogservice",
        "//src/main/java/com/xgen/mongot/index",
        "//src/main/java/com/xgen/mongot/index/status",
        "//src/main/java/com/xgen/mongot/index/synonym",
        "//src/main/java/com/xgen/mongot/util",
        "//src/main/java/com/xgen/mongot/util/concurrent",
        "//src/main/java/com/xgen/mongot/util/mongodb",
        "//src/main/java/com/xgen/testing",
        "//src/main/java/com/xgen/testing/mongot/index/definition",
        "//src/main/java/com/xgen/testing/util",
        "@maven//:com_google_truth_truth",
        "@maven//:org_mockito_mockito_core",
        "@maven//:org_mongodb_bson",
        "@maven//:org_mongodb_mongodb_driver_core",
        "@maven//:org_mongodb_mongodb_driver_sync",
    ],
)
