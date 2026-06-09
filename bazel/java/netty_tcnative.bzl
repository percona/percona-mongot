"""netty-tcnative configuration for Community builds.

This file replaces netty_tcnative.bzl in the open source repository.
Community builds use only the standard Maven artifacts.
"""

# Matching tcnative for Netty 4.1.135.Final is 2.0.77.Final, but that release
# lacks the linux-aarch_64-fedora classifier; 2.0.71.Final still ships it.
NETTY_TCNATIVE_VERSION = "2.0.71.Final"

# Maven artifacts for maven_install()
NETTY_TCNATIVE_MAVEN_ARTIFACTS = [
    "io.netty:netty-tcnative-boringssl-static:{}".format(NETTY_TCNATIVE_VERSION),
    "io.netty:netty-tcnative-classes:{}".format(NETTY_TCNATIVE_VERSION),
    "io.netty:netty-tcnative:jar:linux-aarch_64-fedora:{}".format(NETTY_TCNATIVE_VERSION),
    "io.netty:netty-tcnative:jar:linux-x86_64-fedora:{}".format(NETTY_TCNATIVE_VERSION),
]

NETTY_TCNATIVE_AARCH64_TARGET = "@maven//:io_netty_netty_tcnative_linux_aarch_64_fedora"

# AL2 and AL2023 targets point to same JAR to maintain compatibility with Atlas build configuration
NETTY_TCNATIVE_X86_64_AL2_TARGET = "@maven//:io_netty_netty_tcnative_linux_x86_64_fedora"
NETTY_TCNATIVE_X86_64_AL2023_TARGET = "@maven//:io_netty_netty_tcnative_linux_x86_64_fedora"

# Legacy alias for runtime dependency resolution
NETTY_TCNATIVE_X86_64_TARGET = "@maven//:io_netty_netty_tcnative_linux_x86_64_fedora"

def netty_tcnative_deps():
    """No-op for community builds.
    """
    pass
