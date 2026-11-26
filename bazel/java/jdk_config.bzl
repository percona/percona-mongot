load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@rules_java//toolchains:remote_java_repository.bzl", "remote_java_repository")

def adoptium_jdk():
    _adoptium_jdk_linux_x86_64()
    _adoptium_jdk_linux_x86_64_repo()

    _adoptium_jdk_linux_aarch64()
    _adoptium_jdk_linux_aarch64_repo()

    _adoptium_jdk_macos_x86_64()
    _adoptium_jdk_macos_x86_64_repo()

    _adoptium_jdk_macos_aarch64()
    _adoptium_jdk_macos_aarch64_repo()

def adoptium_jdk_community():
    """Register JDK 21.0.13.1+2 under _community_ repo names.

    In the public repo there is only one JDK version, so the _community_ repos are
    identical to the standard ones. They must still be registered because deploy/BUILD
    references @adoptium_jdk_community_* for community bundle targets.

    Only http_archive targets are registered here (not remote_java_repository) because
    the community JDK is used solely for bundling into deployment artifacts, not as a
    Java compilation toolchain. Registering remote_java_repository would add a second
    toolchain with the same prefix/version as the standard one, making toolchain
    resolution non-deterministic.
    """
    _adoptium_jdk_community_linux_x86_64()
    _adoptium_jdk_community_linux_aarch64()
    _adoptium_jdk_community_macos_x86_64()
    _adoptium_jdk_community_macos_aarch64()

def _adoptium_jdk_linux_x86_64():
    http_archive(
        name = "adoptium_jdk_linux_x86_64",
        build_file = Label("//bazel/java:jdk_linux.BUILD"),
        sha256 = "bb8a93f9ae6e7a693b2b721e85dbeff26ae1a77447985a8d7afcdd9c7cf6222e",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_linux_x86_64_repo():
    remote_java_repository(
        name = "adoptium_jdk_linux_x86_64_repo",
        strip_prefix = "jdk-21.0.13.1+2",
        sha256 = "bb8a93f9ae6e7a693b2b721e85dbeff26ae1a77447985a8d7afcdd9c7cf6222e",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz"],
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:x86_64",
        ],
        prefix = "adoptium",
        version = "21",
    )

def _adoptium_jdk_linux_aarch64():
    http_archive(
        name = "adoptium_jdk_linux_aarch64",
        build_file = Label("//bazel/java:jdk_linux.BUILD"),
        sha256 = "8c72816a6b308842d5ed1f13e5826b69c567da4784f7238d7e800efa71fe54b1",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_linux_aarch64_repo():
    remote_java_repository(
        name = "adoptium_jdk_linux_aarch64_repo",
        strip_prefix = "jdk-21.0.13.1+2",
        sha256 = "8c72816a6b308842d5ed1f13e5826b69c567da4784f7238d7e800efa71fe54b1",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13.1_2.tar.gz"],
        target_compatible_with = [
            "@platforms//os:linux",
            "@platforms//cpu:aarch64",
        ],
        prefix = "adoptium",
        version = "21",
    )

def _adoptium_jdk_macos_x86_64():
    http_archive(
        name = "adoptium_jdk_macos_x86_64",
        build_file = Label("//bazel/java:jdk_macos.BUILD"),
        sha256 = "1b5eff1741bb83d32e2e19aade71452bb42e40f047c01d3e178d438304c9a26d",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_mac_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_macos_x86_64_repo():
    remote_java_repository(
        name = "adoptium_jdk_macos_x86_64_repo",
        sha256 = "1b5eff1741bb83d32e2e19aade71452bb42e40f047c01d3e178d438304c9a26d",
        strip_prefix = "jdk-21.0.13.1+2/Contents/Home",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_mac_hotspot_21.0.13.1_2.tar.gz"],
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:x86_64",
        ],
        prefix = "adoptium",
        version = "21",
    )

def _adoptium_jdk_macos_aarch64():
    http_archive(
        name = "adoptium_jdk_macos_aarch64",
        build_file = Label("//bazel/java:jdk_macos.BUILD"),
        sha256 = "48067f2b7c958ed104a9d8a5749be7d25220b0a356b4d2d1bcfaab3e16ad0b14",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_macos_aarch64_repo():
    remote_java_repository(
        name = "adoptium_jdk_macos_aarch64_repo",
        sha256 = "48067f2b7c958ed104a9d8a5749be7d25220b0a356b4d2d1bcfaab3e16ad0b14",
        strip_prefix = "jdk-21.0.13.1+2/Contents/Home",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13.1_2.tar.gz"],
        target_compatible_with = [
            "@platforms//os:macos",
            "@platforms//cpu:aarch64",
        ],
        prefix = "adoptium",
        version = "21",
    )

def _adoptium_jdk_community_linux_x86_64():
    http_archive(
        name = "adoptium_jdk_community_linux_x86_64",
        build_file = Label("//bazel/java:jdk_linux.BUILD"),
        sha256 = "bb8a93f9ae6e7a693b2b721e85dbeff26ae1a77447985a8d7afcdd9c7cf6222e",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_community_linux_aarch64():
    http_archive(
        name = "adoptium_jdk_community_linux_aarch64",
        build_file = Label("//bazel/java:jdk_linux.BUILD"),
        sha256 = "8c72816a6b308842d5ed1f13e5826b69c567da4784f7238d7e800efa71fe54b1",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_community_macos_x86_64():
    http_archive(
        name = "adoptium_jdk_community_macos_x86_64",
        build_file = Label("//bazel/java:jdk_macos.BUILD"),
        sha256 = "1b5eff1741bb83d32e2e19aade71452bb42e40f047c01d3e178d438304c9a26d",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_mac_hotspot_21.0.13.1_2.tar.gz"],
    )

def _adoptium_jdk_community_macos_aarch64():
    http_archive(
        name = "adoptium_jdk_community_macos_aarch64",
        build_file = Label("//bazel/java:jdk_macos.BUILD"),
        sha256 = "48067f2b7c958ed104a9d8a5749be7d25220b0a356b4d2d1bcfaab3e16ad0b14",
        strip_prefix = "jdk-21.0.13.1+2",
        urls = ["https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13.1_2.tar.gz"],
    )
