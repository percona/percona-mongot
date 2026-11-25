#!/bin/sh
# Re-labels the bundle SBOM for an rpm/deb. The packages ship the bundle as is,
# so only the product identity changes. Needs jq, not python, so the packaging
# stages stay free of it.
#
# usage: retag.sh INPUT PURL VERSION OUTPUT
set -eu

if [ $# -ne 4 ]; then
    echo "usage: $0 INPUT PURL VERSION OUTPUT" >&2
    exit 2
fi
input=$1 purl=$2 version=$3 output=$4

# dpkg-buildpackage exports SOURCE_DATE_EPOCH from the changelog; honour it
# like mongot_sbom.py does
if [ -n "${SOURCE_DATE_EPOCH:-}" ]; then
    ts=$(date -u -d "@${SOURCE_DATE_EPOCH}" +%Y-%m-%dT%H:%M:%SZ)
else
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
fi
serial=urn:uuid:$(cat /proc/sys/kernel/random/uuid)

mkdir -p "$(dirname "$output")"
jq --arg purl "$purl" --arg version "$version" --arg ts "$ts" --arg serial "$serial" '
    .metadata.component["bom-ref"] as $old
    | .metadata.component |= (.["bom-ref"] = $purl | .purl = $purl | .version = $version)
    | if has("dependencies")
      then .dependencies |= map(if .ref == $old then .ref = $purl else . end)
      else . end
    | .serialNumber = $serial
    | .metadata.timestamp = $ts
' "$input" > "$output.tmp"
mv "$output.tmp" "$output"
echo "SBOM retagged: $purl ($output)"
