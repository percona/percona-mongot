#!/usr/bin/env bash
#
# Writes the CycloneDX 1.6 SBOM for an unpacked mongot bundle.
#
# Run from the source root after `bazel build //deploy:mongot-community` for
# the same platform; mongot_builder.sh calls it from build_mongot(). See
# mongot_sbom.py for where each part of the inventory comes from.
#
# Example:
#   percona-packaging/sbom/generate_bundle_sbom.sh \
#       --bazel="./scripts/tools/bazelisk/run.sh --output_user_root=/w/.bazel-cache" \
#       --platform=linux_x86_64 --bundle=/w/repack/percona-search-mongodb \
#       --version=1.70.5 --release=1 --output=/w/percona-search-mongodb-1.70.5.cdx.json

set -euo pipefail

SYFT_VERSION="v1.54.1"
CYCLONEDX_CLI_VERSION="v0.33.1"
CYCLONEDX_CLI_SHA256_X64="bfc8b2538da86fe239bc53658bbb63c1c8c510a293c1e6891aa5bea5d3c58746"
CYCLONEDX_CLI_SHA256_ARM64="b2e9fdf9665ef49868a2ec012171c6e785dcd69745bc5869e53e4f4bfb096a5f"

# java_binary behind //:mongot_community; its direct deps' runtime classpath is
# what gets merged into mongot_community_deploy.jar
BINARY_TARGET="//:mongot_community__non_stamped"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BAZEL_CMD="bazel"
PLATFORM=""
BUNDLE=""
VERSION=""
RELEASE=""
GIT_COMMIT=""
OUTPUT=""
TOOLS_DIR="${HOME:-/tmp}/.cache/mongot-sbom-tools"
POM_CACHE=""

usage() {
    cat <<EOF
Usage: $0 --platform=PLATFORM --bundle=DIR --version=VERSION --output=FILE [OPTIONS]

  --bazel=CMD        bazel command incl. startup options (default: bazel)
  --platform=NAME    linux_x86_64 | linux_aarch64
  --bundle=DIR       unpacked bundle root (bin/, lib/, ...)
  --version=VERSION  product version, e.g. 1.70.5
  --release=N        package release, e.g. 1
  --git_commit=SHA   percona-mongot commit the bundle was built from
  --output=FILE      SBOM path
  --tools_dir=DIR    where syft and cyclonedx-cli get installed (default: ${TOOLS_DIR})
  --pom_cache=DIR    POM download cache (default: <tools_dir>/poms)
EOF
    exit 1
}

for arg in "$@"; do
    val="${arg#*=}"
    case "$arg" in
        --bazel=*)      BAZEL_CMD="$val" ;;
        --platform=*)   PLATFORM="$val" ;;
        --bundle=*)     BUNDLE="$val" ;;
        --version=*)    VERSION="$val" ;;
        --release=*)    RELEASE="$val" ;;
        --git_commit=*) GIT_COMMIT="$val" ;;
        --output=*)     OUTPUT="$val" ;;
        --tools_dir=*)  TOOLS_DIR="$val" ;;
        --pom_cache=*)  POM_CACHE="$val" ;;
        --help|-h)      usage ;;
        *) echo "ERROR: unknown argument: $arg" >&2; usage ;;
    esac
done

[ -n "$PLATFORM" ] && [ -n "$BUNDLE" ] && [ -n "$VERSION" ] && [ -n "$OUTPUT" ] || usage
[ -d "$BUNDLE/bin" ] || { echo "ERROR: $BUNDLE does not look like a mongot bundle" >&2; exit 1; }

case "$PLATFORM" in
    linux_x86_64)  ARCH=x86_64 ;;
    linux_aarch64) ARCH=aarch64 ;;
    *) echo "ERROR: unsupported platform: $PLATFORM" >&2; exit 1 ;;
esac

POM_CACHE="${POM_CACHE:-${TOOLS_DIR}/poms}"
mkdir -p "${TOOLS_DIR}/bin"
export PATH="${TOOLS_DIR}/bin:${PATH}"
# cyclonedx-cli is .NET; we only parse JSON, so skip the libicu dependency
export DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=true
export SYFT_CHECK_FOR_APP_UPDATE=false

install_tools() {
    if [ "$(syft version 2>/dev/null | awk '/^Version:/ {print "v"$2}')" != "${SYFT_VERSION}" ]; then
        # the tag's installer verifies the release checksums itself
        curl -fsSL --retry 3 "https://raw.githubusercontent.com/anchore/syft/${SYFT_VERSION}/install.sh" \
            | sh -s -- -b "${TOOLS_DIR}/bin" "${SYFT_VERSION}"
    fi

    local asset sum
    case "$(uname -m)" in
        x86_64|amd64)  asset=cyclonedx-linux-x64;   sum="${CYCLONEDX_CLI_SHA256_X64}" ;;
        aarch64|arm64) asset=cyclonedx-linux-arm64; sum="${CYCLONEDX_CLI_SHA256_ARM64}" ;;
        *) echo "ERROR: no cyclonedx-cli build for $(uname -m)" >&2; exit 1 ;;
    esac
    local bin="${TOOLS_DIR}/bin/cyclonedx"
    if ! echo "${sum}  ${bin}" | sha256sum -c --status 2>/dev/null; then
        curl -fsSL --retry 3 -o "${bin}.tmp" \
            "https://github.com/CycloneDX/cyclonedx-cli/releases/download/${CYCLONEDX_CLI_VERSION}/${asset}"
        echo "${sum}  ${bin}.tmp" | sha256sum -c --quiet
        chmod +x "${bin}.tmp"
        mv "${bin}.tmp" "${bin}"
    fi
}

install_tools

read -r -a BAZEL <<< "${BAZEL_CMD}"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo ">> runtime classpath of ${BINARY_TARGET} for ${PLATFORM}"
"${BAZEL[@]}" cquery "deps(${BINARY_TARGET}, 1) except ${BINARY_TARGET}" \
    --notool_deps --noimplicit_deps \
    --platforms="//bazel/platforms:${PLATFORM}" \
    --output=starlark --starlark:file="${SCRIPT_DIR}/runtime_jars.cquery.bzl" \
    | sort -u | sed '/^$/d' > "${WORK}/classpath"
OUTPUT_BASE="$("${BAZEL[@]}" info output_base)"

echo ">> syft ${SYFT_VERSION} over ${BUNDLE}"
# java catalogers only: OS packages are the business of the rpm/deb/image SBOMs.
# The "file" group would add per-file hashes nobody consumes.
syft scan "dir:${BUNDLE}" -q \
    --override-default-catalogers java-archive-cataloger,java-jvm-cataloger \
    --select-catalogers "-file" \
    -o "cyclonedx-json@1.6=${WORK}/syft.cdx.json"

LOCKS=()
for f in bazel/java/*_pin_info.json; do
    LOCKS+=(--lock "$f")
done

echo ">> generating ${OUTPUT}"
python3 "${SCRIPT_DIR}/mongot_sbom.py" generate \
    --classpath "${WORK}/classpath" \
    --external "${OUTPUT_BASE}/external" \
    "${LOCKS[@]}" \
    --bundle "${BUNDLE}" \
    --syft "${WORK}/syft.cdx.json" \
    --version "${VERSION}" \
    --release "${RELEASE}" \
    --arch "${ARCH}" \
    --git-commit "${GIT_COMMIT}" \
    --pom-cache "${POM_CACHE}" \
    --output "${OUTPUT}"

cyclonedx validate --input-file "${OUTPUT}" --input-format json --input-version v1_6 --fail-on-errors
