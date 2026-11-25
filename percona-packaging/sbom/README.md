# SBOM

CycloneDX 1.6 SBOMs for percona-search-mongodb, generated during the build.

| artifact | where | `metadata.component.purl` |
|---|---|---|
| bundle tarball | `percona-search-mongodb/percona-search-mongodb-<ver>.cdx.json` | `pkg:generic/percona/percona-search-mongodb@<ver>-<rel>?arch=…` |
| rpm | `/usr/share/doc/percona-search-mongodb/percona-search-mongodb-<ver>.cdx.json` | `pkg:rpm/percona/percona-search-mongodb@<ver>-<rel>.<dist>?arch=…` |
| deb | `/usr/share/doc/percona-search-mongodb/percona-search-mongodb-<ver>.cdx.json` | `pkg:deb/percona/percona-search-mongodb@<ver>-<rel>.<codename>?arch=…` |

`mongot_builder.sh --build_mongot` writes the bundle SBOM into the tarball root;
the rpm spec and `debian/rules` re-label that same file with `retag.sh` (jq only,
so packaging needs no python), since the packages ship the bundle as is.

Why not just syft: most jars merged into `mongot_community_deploy.jar` have no
`pom.properties`, so binary scanners only recognise about a fifth of them. The
inventory comes from bazel (runtime classpath of the community `java_binary`,
per platform) plus the `bazel/java/*_pin_info.json` locks; licenses come from
the POMs. syft is still run over the bundle for the JDK and as a cross-check:
a version mismatch between what it finds and the bazel inventory fails the build.

Not in here: OS packages (they belong to the image SBOM) and vulnerabilities
(stale the day after release; scan the SBOM with grype/trivy instead).

Scanning: `trivy image` / `trivy rootfs` pick up the `.cdx.json` inside the
image or bundle on their own, so everything listed here shows up in those
scans. The image SBOM attached with oras names the OS `rhel`, as syft reads it
from `/etc/os-release`; `trivy sbom` only knows `redhat` and skips the OS
packages in that file. Scan the image itself with trivy, or the SBOM with grype.

Run it by hand after building the bundle, e.g. in `build-env`:

```sh
percona-packaging/sbom/generate_bundle_sbom.sh --platform=linux_aarch64 \
    --bundle=/path/to/percona-search-mongodb --version=1.70.5 --release=1 \
    --output=/tmp/percona-search-mongodb-1.70.5.cdx.json
```

A jar from an external repo that isn't Maven (built from source by bazel) has
to be mapped in `SOURCE_BUILT` in `mongot_sbom.py`; the generator fails until it is.
