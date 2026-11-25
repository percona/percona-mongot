#!/usr/bin/env bash
#
# Usage: apply_spi_service_overrides.sh <jar> <overrides.tsv>
#
# Percona: upstream's java_binary_stamped_manifest (bazel/java/package.bzl) calls
# //scripts/deploy:apply_spi_service_overrides, but the published 1.78.0 sources don't
# include it. This is a re-implementation of the contract described in
# bazel/java/spi_service_overrides.tsv: for each row, both the source and the target
# provider must be listed in the service file inside the jar (otherwise fail the build),
# then the source provider line is removed so the target becomes the only provider of
# that SPI name. The jar is updated in place.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <jar> <overrides.tsv>" >&2
  exit 2
fi

jar="$1"
overrides="$2"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

# provider class names listed in a service file (comments and whitespace stripped)
providers() {
  sed -e 's/#.*//' -e 's/[[:space:]]//g' -e '/^$/d' "$1"
}

changed=()
while read -r svc src dst rest || [[ -n "${svc:-}" ]]; do
  [[ -z "${svc:-}" || "${svc}" == \#* ]] && continue
  if [[ -z "${src:-}" || -z "${dst:-}" || -n "${rest:-}" ]]; then
    echo "error: malformed override row: ${svc} ${src:-} ${dst:-} ${rest:-}" >&2
    exit 1
  fi

  f="${work}/${svc}"
  if [[ ! -f "${f}" ]]; then
    mkdir -p "$(dirname "${f}")"
    if ! unzip -p "${jar}" "${svc}" > "${f}"; then
      echo "error: ${svc} not found in ${jar}" >&2
      exit 1
    fi
    changed+=("${svc}")
  fi

  for p in "${src}" "${dst}"; do
    if ! providers "${f}" | grep -qxF "${p}"; then
      echo "error: ${p} is not listed in ${svc} of ${jar}" >&2
      exit 1
    fi
  done

  # drop the source provider (exact class name match, surrounding whitespace allowed)
  awk -v drop="${src}" '{ line = $0; gsub(/[[:space:]]/, "", line); if (line != drop) print }' \
    "${f}" > "${f}.new"
  mv "${f}.new" "${f}"
done < "${overrides}"

jar_abs="$(cd "$(dirname "${jar}")" && pwd)/$(basename "${jar}")"
for svc in "${changed[@]+"${changed[@]}"}"; do
  zip -d -q "${jar_abs}" "${svc}"
  (cd "${work}" && zip -q "${jar_abs}" "${svc}")
done
