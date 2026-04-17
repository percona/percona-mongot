load("//bazel/java:dep_utils.bzl", "append_version", "as_test_only")

# Upstream Apache Lucene release version.
_LUCENE_UPSTREAM_VERSION = "10.1.0"

_LUCENE_UPSTREAM_ARTIFACTS = append_version(
    _LUCENE_UPSTREAM_VERSION,
    [
        "org.apache.lucene:lucene-analysis-common",
        "org.apache.lucene:lucene-analysis-icu",
        "org.apache.lucene:lucene-analysis-kuromoji",
        "org.apache.lucene:lucene-analysis-morfologik",
        "org.apache.lucene:lucene-analysis-nori",
        "org.apache.lucene:lucene-analysis-phonetic",
        "org.apache.lucene:lucene-analysis-smartcn",
        "org.apache.lucene:lucene-analysis-stempel",
        # lucene-core, lucene-backward-codecs and lucene-codecs are also listed here at the upstream
        # version to ensure that transitive dependency resolution in the main maven_install works
        # correctly. At build time, override_targets redirects these to the fork JARs.
        # See deps.bzl for more details.
        "org.apache.lucene:lucene-backward-codecs",
        "org.apache.lucene:lucene-codecs",
        "org.apache.lucene:lucene-core",
        "org.apache.lucene:lucene-expressions",
        "org.apache.lucene:lucene-highlighter",
        "org.apache.lucene:lucene-join",
        "org.apache.lucene:lucene-misc",
        "org.apache.lucene:lucene-queries",
        "org.apache.lucene:lucene-queryparser",
        "org.apache.lucene:lucene-facet",
    ],
) + as_test_only(
    append_version(_LUCENE_UPSTREAM_VERSION, ["org.apache.lucene:lucene-test-framework"]),
)

# Version of mongot's Lucene fork artifacts published to
# https://downloads.mongodb.com/lucene-mongot/*. Built from the corresponding mongot development
# branch in the [lucene-mongot](https://github.com/mongodb-forks/lucene-mongot) repository.
# These are resolved via the separate "lucene_fork" maven_install in deps.bzl and override the
# upstream artifacts in the main maven_install via override_targets.
# Changes included in this fork are documented in
# https://github.com/mongodb-forks/lucene-mongot/blob/mongot_10_1_0/lucene/CHANGES.txt#L6-L12
# Major change - Support for Bloom Filter for id field
_LUCENE_FORK_VERSION = "10.1.0-2"

_LUCENE_FORK_ARTIFACT_NAMES = [
    "org.apache.lucene:lucene-backward-codecs",
    "org.apache.lucene:lucene-codecs",
    "org.apache.lucene:lucene-core",
]

LUCENE_FORK_ARTIFACTS = append_version(_LUCENE_FORK_VERSION, _LUCENE_FORK_ARTIFACT_NAMES)

LUCENE_FORK_OVERRIDE_TARGETS = {
    name: "@lucene_fork//:org_apache_lucene_%s" % name.split(":")[1].replace("-", "_")
    for name in _LUCENE_FORK_ARTIFACT_NAMES
}

SEARCH_QUERY_DEPS = _LUCENE_UPSTREAM_ARTIFACTS
