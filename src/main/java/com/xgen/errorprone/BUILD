load("@rules_java//java:defs.bzl", "java_library", "java_plugin")
load("@rules_jvm_external//:defs.bzl", "artifact")

# gazelle:ignore

java_library(
    name = "errorprone",
    srcs = [
        "UnsafeCollectorsChecker.java",
    ],
    javacopts = [
        "--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    ],
    plugins = ["//bazel/java:autoservice_annotation_processor"],
    visibility = ["//:__subpackages__"],
    deps = [
        artifact("com.google.errorprone:error_prone_annotations"),
        artifact("com.google.errorprone:error_prone_annotation"),
        artifact("com.google.errorprone:error_prone_check_api"),
        artifact("com.google.errorprone:error_prone_core"),
        artifact("com.google.guava:guava"),
        "@maven//:com_google_auto_service_auto_service_annotations",
        "@maven//:org_jetbrains_annotations",
    ],
)

java_plugin(
    name = "unsafe_collectors_plugin",
    visibility = ["//visibility:public"],
    deps = [":errorprone"],
)
