load("//bazel/docker:init_bazel_deps.bzl", "docker_init_bazel_deps")
load("//bazel/java:init_bazel_deps.bzl", "java_init_bazel_deps")
load("//bazel/javascript:init_bazel_deps.bzl", "javascript_init_bazel_deps")
load("//bazel/python:init_bazel_deps.bzl", "python_init_bazel_deps")

def mongot_init_bazel_deps():
    docker_init_bazel_deps()
    java_init_bazel_deps()
    javascript_init_bazel_deps()
    python_init_bazel_deps()
