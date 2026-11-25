#!/usr/bin/env python3
"""CycloneDX 1.6 SBOM for the percona-search-mongodb (mongot) bundle.

Binary scanners alone can't do this: most jars merged into
mongot_community_deploy.jar carry no pom.properties, so syft/trivy see only
~20% of them. The inventory comes from bazel instead:

  * the runtime classpath of the community java_binary (bazel cquery), which
    is exactly what gets merged into the deploy jar, per target platform;
  * rules_jvm_external lock files for versions, hashes, repositories and the
    dependency graph;
  * POM files from the artifact repositories for licenses and links;
  * syft over the unpacked bundle for the bundled JDK, plus a cross-check
    that every jar it recognises matches the bazel inventory.

The rpm/deb re-label this file with retag.sh (jq), not with this script.

Stdlib only.
"""

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import xml.etree.ElementTree as ET

PRODUCT = "percona-search-mongodb"
SUPPLIER = {"name": "Percona LLC", "url": ["https://www.percona.com"]}
MAVEN_CENTRAL = "https://repo1.maven.org/maven2/"
# purl spec: maven repository_url defaults to Maven Central
CENTRAL_ALIASES = ("https://repo1.maven.org/maven2", "https://repo.maven.apache.org/maven2")
UPSTREAM_REPO = "https://github.com/mongodb/mongot"
PERCONA_REPO = "https://github.com/percona/percona-mongot"
TOOL_NAME = "mongot_sbom.py"


def die(msg):
    sys.stderr.write("ERROR: %s\n" % msg)
    sys.exit(1)


def warn(msg):
    sys.stderr.write("WARNING: %s\n" % msg)


def bom_timestamp():
    # SOURCE_DATE_EPOCH keeps rebuilds byte-identical when the caller wants that
    epoch = os.environ.get("SOURCE_DATE_EPOCH")
    t = int(epoch) if epoch else time.time()
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(t))


def purl_maven(group, artifact, version, classifier=None, repository=None):
    q = urllib.parse.quote
    purl = "pkg:maven/%s/%s@%s" % (q(group, safe="."), q(artifact, safe=".-_"), q(version, safe=".-_"))
    quals = []
    if classifier:
        quals.append("classifier=" + q(classifier, safe=".-_"))
    if repository and repository.rstrip("/") not in CENTRAL_ALIASES:
        quals.append("repository_url=" + q(repository.rstrip("/"), safe=""))
    if quals:
        purl += "?" + "&".join(sorted(quals))
    return purl


# --- lock files ---------------------------------------------------------------


class Lock(object):
    """One rules_jvm_external v2 lock file.

    Kept separate per file: @maven and @protobuf_maven resolve some artifacts
    (gson, guava, ...) to different versions.
    """

    def __init__(self, path):
        with open(path) as f:
            lock = json.load(f)
        if lock.get("version") != "2":
            die("%s: unsupported lock file version %r" % (path, lock.get("version")))
        self.path = path
        self.repo_of = {}
        for repo, keys in lock.get("repositories", {}).items():
            for key in keys:
                self.repo_of[key] = repo
        self.artifacts = lock.get("artifacts", {})  # "g:a" -> {version, shasums}
        self.deps = lock.get("dependencies", {})  # "g:a[:jar:classifier]" -> [...]


def lock_lookup(locks, group, artifact, version, classifier):
    """-> (lock, sha256, repository) for the lock that pins this exact version."""
    key = "%s:%s" % (group, artifact)
    pinned = []
    for lock in locks:
        info = lock.artifacts.get(key)
        if not info:
            continue
        if info["version"] != version:
            pinned.append("%s in %s" % (info["version"], os.path.basename(lock.path)))
            continue
        sha = info.get("shasums", {}).get(classifier or "jar")
        if not sha:
            die("%s: no sha256 for %s in %s" % (key, classifier or "jar", lock.path))
        # lock repositories list classified artifacts as g:a:jar:classifier
        lkey = key + (":jar:" + classifier if classifier else "")
        repo = lock.repo_of.get(lkey, lock.repo_of.get(key, MAVEN_CENTRAL))
        return lock, sha, repo
    if pinned:
        die("%s: classpath has %s but lock files pin %s" % (key, version, ", ".join(pinned)))
    return None


# --- POM metadata ---------------------------------------------------------------

SPDX_BY_NAME = [
    # (regex over the lowercased license name, SPDX id); first match wins
    (r"apache.*2", "Apache-2.0"),
    (r"^(the )?mit( license)?$|mit license", "MIT"),
    (r"bsd.*3|new bsd|revised bsd|modified bsd|eclipse distribution license", "BSD-3-Clause"),
    (r"bsd.*2|simplified bsd|freebsd", "BSD-2-Clause"),
    (r"eclipse public license.*2|epl.*2", "EPL-2.0"),
    (r"eclipse public license.*1|epl.*1", "EPL-1.0"),
    (r"lesser general public license.*2\.1|lgpl.*2\.1", "LGPL-2.1-only"),
    (r"cddl.*1\.1|common development and distribution license.*1\.1", "CDDL-1.1"),
    (r"cddl|common development and distribution license", "CDDL-1.0"),
    (r"\bcc0\b|creative commons zero", "CC0-1.0"),
    (r"mozilla public license.*1\.1|mpl.*1\.1", "MPL-1.1"),
    (r"mozilla public license.*2|mpl.*2", "MPL-2.0"),
    (r"unicode.*3\.0", "Unicode-3.0"),
    (r"go license", "BSD-3-Clause"),
]
SPDX_BY_URL = [
    (r"apache\.org/licenses/license-2\.0", "Apache-2.0"),
    (r"opensource\.org/licenses/mit", "MIT"),
    (r"opensource\.org/licenses/bsd-3-clause", "BSD-3-Clause"),
    (r"eclipse\.org/legal/epl-2\.0", "EPL-2.0"),
    (r"eclipse\.org/legal/epl-v10", "EPL-1.0"),
]
CLASSPATH_EXCEPTION = r"gpl.*(classpath|cpe)|general public license.*classpath"


def spdx_id(name, url):
    n = (name or "").strip().lower()
    u = (url or "").strip().lower()
    for rx, sid in SPDX_BY_NAME:
        if n and re.search(rx, n):
            return sid
    for rx, sid in SPDX_BY_URL:
        if u and re.search(rx, u):
            return sid
    return None


def cdx_licenses(pairs):
    """[(name, url)] from POMs -> CycloneDX `licenses` array."""
    if len(pairs) == 1 and re.search(CLASSPATH_EXCEPTION, (pairs[0][0] or "").lower()):
        # an expression can't share the array with license objects
        return [{"expression": "GPL-2.0-only WITH Classpath-exception-2.0"}]
    out, seen = [], set()
    for name, url in pairs:
        if re.search(CLASSPATH_EXCEPTION, (name or "").lower()):
            lic = {"name": name}
        else:
            sid = spdx_id(name, url)
            lic = {"id": sid} if sid else {"name": name or url}
        if url and "id" not in lic:
            lic["url"] = url
        key = json.dumps(lic, sort_keys=True)
        if key not in seen:
            seen.add(key)
            out.append({"license": lic})
    return out


def _strip_ns(root):
    for el in root.iter():
        if isinstance(el.tag, str) and "}" in el.tag:
            el.tag = el.tag.split("}", 1)[1]
    return root


class PomResolver(object):
    """Fetches POMs (with an on-disk cache) and walks <parent> for licenses/scm."""

    def __init__(self, cache_dir, offline=False):
        self.cache_dir = cache_dir
        self.offline = offline
        self.memo = {}
        if cache_dir:
            os.makedirs(cache_dir, exist_ok=True)

    def _fetch(self, repo, group, artifact, version):
        rel = "%s/%s/%s/%s-%s.pom" % (group.replace(".", "/"), artifact, version, artifact, version)
        cached = os.path.join(self.cache_dir, rel) if self.cache_dir else None
        if cached and os.path.exists(cached):
            with open(cached, "rb") as f:
                return f.read()
        if self.offline:
            return None
        url = repo.rstrip("/") + "/" + rel
        for attempt in range(4):
            try:
                with urllib.request.urlopen(url, timeout=30) as r:
                    data = r.read()
                break
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    return None
                err = e
            except (urllib.error.URLError, OSError) as e:
                err = e
            time.sleep(2 * (attempt + 1))
        else:
            die("cannot fetch %s: %s" % (url, err))
        if cached:
            os.makedirs(os.path.dirname(cached), exist_ok=True)
            with open(cached, "wb") as f:
                f.write(data)
        return data

    def info(self, repo, group, artifact, version, depth=0):
        """-> dict(licenses=[(name, url)], url, vcs, description) or None."""
        key = (group, artifact, version)
        if key in self.memo:
            return self.memo[key]
        data = None
        for r in (repo, MAVEN_CENTRAL) if repo.rstrip("/") not in CENTRAL_ALIASES else (repo,):
            data = self._fetch(r, group, artifact, version)
            if data is not None:
                break
        if data is None:
            self.memo[key] = None
            return None
        try:
            pom = _strip_ns(ET.fromstring(data))
        except ET.ParseError as e:
            warn("unparsable POM %s:%s:%s: %s" % (group, artifact, version, e))
            self.memo[key] = None
            return None
        res = {
            "licenses": [(text_of(l, "name"), text_of(l, "url")) for l in pom.findall("licenses/license")],
            "url": text_of(pom, "url"),
            "vcs": text_of(pom, "scm/url"),
            "description": text_of(pom, "description"),
        }
        parent = pom.find("parent")
        if parent is not None and depth < 6 and not (res["licenses"] and res["vcs"]):
            pg, pa, pv = (text_of(parent, t) for t in ("groupId", "artifactId", "version"))
            if pg and pa and pv:
                p = self.info(repo, pg, pa, pv, depth + 1)
                if p:
                    res["licenses"] = res["licenses"] or p["licenses"]
                    res["vcs"] = res["vcs"] or p["vcs"]
                    res["url"] = res["url"] or p["url"]
        self.memo[key] = res
        return res


def text_of(el, path):
    # unresolved ${property} values are as good as missing
    c = el.find(path)
    return c.text.strip() if c is not None and c.text and "${" not in c.text else None


# --- classpath -> components -------------------------------------------------------

# Jars built from source by bazel instead of coming from a maven repository.
# Each gets the identity of the equivalent Maven Central artifact so scanners
# can match advisories against it.


def _grpc_java(subpath, ext_dir):
    version = _module_version(os.path.join(ext_dir, "MODULE.bazel"))
    sub = subpath.split("/", 1)[0]
    return "io.grpc", "grpc-" + sub, version


def _grpc_proto(subpath, ext_dir):
    # generated channelz/health/reflection stubs; upstream ships them inside grpc-services
    grpc_java = os.path.join(os.path.dirname(ext_dir), _sibling(ext_dir, "io_grpc_grpc_java"))
    return "io.grpc", "grpc-services", _module_version(os.path.join(grpc_java, "MODULE.bazel"))


def _protobuf(subpath, ext_dir):
    with open(os.path.join(ext_dir, "version.json")) as f:
        info = json.load(f)
    version = list(info.values())[0]["languages"]["java"]
    artifacts = {"java/core": "protobuf-java", "java/util": "protobuf-java-util", "java/lite": "protobuf-javalite"}
    for prefix, artifact in artifacts.items():
        if subpath.startswith(prefix + "/"):
            return "com.google.protobuf", artifact, version
    die("unmapped protobuf jar: %s" % subpath)


SOURCE_BUILT = {
    "io_grpc_grpc_java": (_grpc_java, "https://github.com/grpc/grpc-java"),
    "io_grpc_grpc_proto": (_grpc_proto, "https://github.com/grpc/grpc-proto"),
    "protobuf": (_protobuf, "https://github.com/protocolbuffers/protobuf"),
}


def _module_version(path):
    with open(path) as f:
        m = re.search(r"module\([^)]*?version\s*=\s*\"([^\"]+)\"", f.read(), re.S)
    if not m:
        die("no module version in %s" % path)
    return m.group(1)


def _sibling(ext_dir, apparent):
    parent = os.path.dirname(ext_dir)
    for name in os.listdir(parent):
        if _apparent_name(name) == apparent:
            return name
    die("external repo %s not found next to %s" % (apparent, ext_dir))


def _apparent_name(canonical):
    # bzlmod canonical names: "protobuf+", "+_repo_rules5+io_grpc_grpc_java", "rules_jvm_external++maven+maven"
    parts = [p for p in canonical.split("+") if p]
    return parts[-1] if parts else canonical


MAVEN_JAR = re.compile(r"^(?:processed_|header_)?(?P<file>.+)\.jar$")


def parse_maven_path(rel):
    """<group/path>/<artifact>/<version>/[processed_]<artifact>-<version>[-<classifier>].jar"""
    parts = rel.split("/")
    if len(parts) < 4:
        return None
    group, artifact, version, fname = ".".join(parts[:-3]), parts[-3], parts[-2], parts[-1]
    m = MAVEN_JAR.match(fname)
    stem = "%s-%s" % (artifact, version)
    if not m or not m.group("file").startswith(stem):
        return None
    classifier = m.group("file")[len(stem):].lstrip("-") or None
    return group, artifact, version, classifier


def collect(classpath, ext_root):
    """-> ({(group, artifact, version, classifier): (kind, vcs)}, first-party jar count)."""
    found = {}
    first_party = 0
    for line in classpath:
        line = line.strip()
        if not line:
            continue
        m = re.match(r"^bazel-out/[^/]+/bin/external/([^/]+)/(.+)$", line)
        if not m:
            if re.match(r"^bazel-out/[^/]+/bin/(src|conf)/", line):
                first_party += 1
                continue
            die("unexpected classpath entry: %s" % line)
        repo, rel = m.group(1), m.group(2)
        if repo.startswith("rules_jvm_external+"):
            coords = parse_maven_path(rel)
            if not coords:
                die("cannot parse maven jar path: %s" % line)
            found[coords] = ("maven", None)
            continue
        handler = SOURCE_BUILT.get(_apparent_name(repo))
        if not handler:
            die("jar from unmapped external repo %s: %s (add it to SOURCE_BUILT)" % (repo, line))
        g, a, v = handler[0](rel, os.path.join(ext_root, repo))
        found[(g, a, v, None)] = ("source", handler[1])
    return found, first_party


def build_components(found, locks, poms):
    comps = {}  # purl -> component
    lock_key_to_purl = {}  # (lock path, "g:a[:jar:classifier]") -> purl
    missing_license = []
    for (g, a, v, cls), (kind, vcs) in sorted(found.items()):
        lock, sha, repo = None, None, MAVEN_CENTRAL
        if kind == "maven":
            hit = lock_lookup(locks, g, a, v, cls)
            if hit is None:
                die("%s:%s:%s is on the classpath but in no lock file" % (g, a, v))
            lock, sha, repo = hit
        purl = purl_maven(g, a, v, cls, repo)
        c = {"type": "library", "bom-ref": purl, "group": g, "name": a, "version": v, "scope": "required", "purl": purl}
        info = poms.info(repo, g, a, v)
        if info:
            if info["description"]:
                c["description"] = " ".join(info["description"].split())
            if info["licenses"]:
                c["licenses"] = cdx_licenses(info["licenses"])
        if "licenses" not in c:
            missing_license.append(purl)
        if sha:
            c["hashes"] = [{"alg": "SHA-256", "content": sha}]
        refs = []
        if kind == "maven":
            jar = "%s-%s%s.jar" % (a, v, "-" + cls if cls else "")
            refs.append({"type": "distribution", "url": "%s/%s/%s/%s/%s" % (repo.rstrip("/"), g.replace(".", "/"), a, v, jar)})
        if info and info["url"]:
            refs.append({"type": "website", "url": info["url"]})
        vcs_url = vcs or (info and info["vcs"])
        if vcs_url:
            refs.append({"type": "vcs", "url": vcs_url})
        if refs:
            c["externalReferences"] = refs
        if kind == "source":
            c["properties"] = [{"name": "percona:sbom:origin", "value": "built from source by bazel"}]
        if purl in comps:
            continue  # e.g. several grpc-services jars
        comps[purl] = c
        if lock:
            lock_key_to_purl[(lock.path, "%s:%s" % (g, a) + (":jar:" + cls if cls else ""))] = purl
    return comps, lock_key_to_purl, missing_license


# --- bundle extras ---------------------------------------------------------------


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_release(path):
    out = {}
    with open(path) as f:
        for line in f:
            if "=" in line:
                k, v = line.rstrip("\n").split("=", 1)
                out[k] = v.strip('"')
    return out


def strip_purl_qualifier(purl, name):
    base, _, quals = purl.partition("?")
    kept = [q for q in quals.split("&") if q and not q.startswith(name + "=")]
    return base + ("?" + "&".join(kept) if kept else "")


def jdk_component(syft, bundle):
    rel = os.path.join(bundle, "bin", "jdk", "release")
    if not os.path.exists(rel):
        die("bundled JDK not found: %s" % rel)
    release = read_release(rel)
    jvm = [c for c in syft.get("components", []) if _syft_prop(c, "syft:package:foundBy") == "java-jvm-cataloger"]
    if len(jvm) != 1:
        die("expected exactly one JDK from syft, got %d" % len(jvm))
    s = jvm[0]
    purl = strip_purl_qualifier(s["purl"], "package-id")
    c = {
        "type": "platform",
        "bom-ref": purl,
        "supplier": {"name": release.get("IMPLEMENTOR", "unknown")},
        "name": s["name"],
        "version": s["version"],
        "description": "%s %s (bundled Java runtime)" % (release.get("IMPLEMENTOR", ""), release.get("IMPLEMENTOR_VERSION", "")),
        "scope": "required",
        "licenses": [{"expression": "GPL-2.0-only WITH Classpath-exception-2.0"}],
        "purl": purl,
    }
    if s.get("cpe"):
        c["cpe"] = s["cpe"]
    refs = []
    if release.get("SOURCE_REPO"):
        refs.append({"type": "vcs", "url": release["SOURCE_REPO"]})
    if refs:
        c["externalReferences"] = refs
    return c


def _syft_prop(c, name):
    for p in c.get("properties", []):
        if p.get("name") == name:
            return p.get("value")
    return None


def native_components(bundle, version):
    out = []
    lib = os.path.join(bundle, "lib")
    for fname in sorted(os.listdir(lib)) if os.path.isdir(lib) else []:
        if not fname.endswith(".so"):
            continue
        name = fname[3:-3] if fname.startswith("lib") else fname[:-3]
        out.append({
            "type": "library",
            "bom-ref": "%s:%s" % (PRODUCT, name),
            "supplier": SUPPLIER,
            "name": name,
            "version": version,
            "description": "native library built from the mongot sources (src/main/c)",
            "scope": "required",
            "hashes": [{"alg": "SHA-256", "content": sha256_file(os.path.join(lib, fname))}],
            "licenses": [{"license": {"id": "SSPL-1.0"}}],
            "properties": [{"name": "percona:sbom:path", "value": "lib/" + fname}],
        })
    return out


def cross_check(syft, comps, deploy_jar):
    """Fails on version drift between syft's view and the bazel inventory.

    Jars syft identifies that bazel doesn't list (fat-jar sub-modules such as
    icu4j's) are returned so they can be added.
    """
    by_ga = {}
    for c in comps.values():
        if c["purl"].startswith("pkg:maven/"):
            by_ga.setdefault((c["group"], c["name"]), set()).add(c["version"])
    extras, matched, problems = [], 0, []
    for s in syft.get("components", []):
        purl = s.get("purl", "")
        if not purl.startswith("pkg:maven/"):
            continue
        loc = _syft_prop(s, "syft:location:0:path") or ""
        if loc.startswith("/bin/jdk/"):
            continue  # jrt-fs and friends, part of the JDK
        m = re.match(r"^pkg:maven/([^/]+)/([^@]+)@([^?#]+)", purl)
        if not m:
            continue
        g, a, v = (urllib.parse.unquote(x) for x in m.groups())
        if a.startswith("processed_"):
            a = a[len("processed_"):]
        if a == os.path.splitext(deploy_jar)[0] or v == "local":
            continue  # the mongot deploy jar itself
        versions = by_ga.get((g, a))
        if versions is None:
            extras.append(s)
        elif v not in versions:
            problems.append("%s:%s: bundle has %s, bazel classpath has %s" % (g, a, v, ", ".join(sorted(versions))))
        else:
            matched += 1
    if problems:
        die("SBOM inventory does not match the bundle:\n  " + "\n  ".join(problems))
    return matched, extras


def extra_component(s, poms):
    purl = strip_purl_qualifier(s["purl"], "package-id")
    c = {"type": "library", "bom-ref": purl, "group": s.get("group", ""), "name": s["name"], "version": s["version"], "scope": "required", "purl": purl}
    info = poms.info(MAVEN_CENTRAL, c["group"], c["name"], c["version"])
    if info and info["licenses"]:
        c["licenses"] = cdx_licenses(info["licenses"])
    elif s.get("licenses"):
        c["licenses"] = s["licenses"]
    c["properties"] = [{"name": "percona:sbom:origin", "value": "found by binary scan inside %s" % (_syft_prop(s, "syft:location:0:path") or "bundle")}]
    return c


# --- subcommands -------------------------------------------------------------------


def product_component(version, release, arch, git_commit):
    full = "%s-%s" % (version, release) if release else version
    purl = "pkg:generic/percona/%s@%s?arch=%s" % (PRODUCT, full, arch)
    vcs = PERCONA_REPO + ("/tree/" + git_commit if git_commit else "")
    upstream_purl = "pkg:github/mongodb/mongot@%s" % version
    return {
        "type": "application",
        "bom-ref": purl,
        "supplier": SUPPLIER,
        "publisher": "Percona LLC",
        "group": "percona",
        "name": PRODUCT,
        "version": full,
        "description": "Percona Search for MongoDB: Percona distribution of MongoDB Search (mongot)",
        "licenses": [{"license": {"id": "SSPL-1.0"}}],
        "purl": purl,
        "externalReferences": [
            {"type": "vcs", "url": vcs},
            {"type": "website", "url": "https://www.percona.com"},
        ],
        "pedigree": {
            "ancestors": [{
                "type": "application",
                "bom-ref": upstream_purl,
                "supplier": {"name": "MongoDB, Inc.", "url": ["https://www.mongodb.com"]},
                "group": "mongodb",
                "name": "mongot",
                "version": version,
                "licenses": [{"license": {"id": "SSPL-1.0"}}],
                "purl": upstream_purl,
                "externalReferences": [{"type": "vcs", "url": UPSTREAM_REPO}],
            }],
            "notes": "Built from the percona-mongot fork of mongodb/mongot with Percona patches.",
        },
    }


def cmd_generate(a):
    with open(a.classpath) as f:
        classpath = f.read().splitlines()
    with open(a.syft) as f:
        syft = json.load(f)
    locks = [Lock(path) for path in a.lock]
    poms = PomResolver(a.pom_cache, a.offline)

    found, first_party = collect(classpath, a.external)
    if first_party == 0:
        die("no first-party jars on the classpath; wrong cquery target?")
    comps, lock_key_to_purl, no_license = build_components(found, locks, poms)

    deploy_jars = [f for f in os.listdir(os.path.join(a.bundle, "bin")) if f.endswith("_deploy.jar")]
    if len(deploy_jars) != 1:
        die("expected one *_deploy.jar in %s/bin, got %s" % (a.bundle, deploy_jars))
    matched, extras = cross_check(syft, comps, deploy_jars[0])
    # sub-modules of a fat artifact (icu4j's collate, core, ...) aren't published
    # on their own; they inherit from the bazel artifact with the same group and version
    by_gv = {}
    for c in comps.values():
        by_gv.setdefault((c.get("group"), c["version"]), []).append(c)
    owners = {}
    for s in extras:
        c = extra_component(s, poms)
        owner = by_gv.get((c["group"], c["version"]), [])
        if len(owner) == 1:
            owners.setdefault(owner[0]["bom-ref"], []).append(c["bom-ref"])
            if "licenses" not in c and "licenses" in owner[0]:
                c["licenses"] = owner[0]["licenses"]
        comps.setdefault(c["purl"], c)
        if "licenses" not in c:
            no_license.append(c["purl"])

    jdk = jdk_component(syft, a.bundle)
    natives = native_components(a.bundle, a.version)
    root = product_component(a.version, a.release, a.arch, a.git_commit)

    # edges come from the lock that resolved the parent; a child pinned there at
    # a version that isn't shipped is dropped
    deps = {}
    for lock in locks:
        for key, children in lock.deps.items():
            parent = lock_key_to_purl.get((lock.path, key))
            if parent:
                kids = {lock_key_to_purl.get((lock.path, k)) for k in children} - {None, parent}
                deps[parent] = sorted(kids)
    for parent, kids in owners.items():
        deps[parent] = sorted(set(deps.get(parent, [])) | set(kids))
    referenced = {k for kids in deps.values() for k in kids}
    top = sorted(p for p in comps if p not in referenced)
    dependencies = [{"ref": root["bom-ref"], "dependsOn": [jdk["bom-ref"]] + [n["bom-ref"] for n in natives] + top}]
    dependencies += [{"ref": p, "dependsOn": deps.get(p, [])} for p in sorted(comps)]
    dependencies += [{"ref": jdk["bom-ref"], "dependsOn": []}] + [{"ref": n["bom-ref"], "dependsOn": []} for n in natives]

    tools = [{"type": "application", "name": TOOL_NAME, "supplier": SUPPLIER}]
    syft_tool = syft.get("metadata", {}).get("tools", {})
    for t in syft_tool.get("components", []) if isinstance(syft_tool, dict) else []:
        tools.append({k: t[k] for k in ("type", "author", "name", "version") if k in t})

    bom = {
        "$schema": "http://cyclonedx.org/schema/bom-1.6.schema.json",
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": "urn:uuid:%s" % uuid.uuid4(),
        "version": 1,
        "metadata": {
            "timestamp": bom_timestamp(),
            "lifecycles": [{"phase": "build"}],
            "tools": {"components": tools},
            "component": root,
            "supplier": SUPPLIER,
        },
        "components": [jdk] + natives + [comps[p] for p in sorted(comps)],
        "dependencies": dependencies,
    }
    with open(a.output, "w") as f:
        json.dump(bom, f, indent=2)
        f.write("\n")

    n_maven = sum(1 for p in comps if p.startswith("pkg:maven/"))
    print("SBOM written: %s" % a.output)
    print("  product:      %s" % root["purl"])
    print("  components:   %d (%d maven, %d from binary scan only, jdk %s, %d native)" % (
        len(bom["components"]), n_maven, len(extras), jdk["version"], len(natives)))
    print("  syft matched: %d jars, versions consistent" % matched)
    if no_license:
        warn("%d components without license data:\n  %s" % (len(no_license), "\n  ".join(no_license)))
    if n_maven < a.min_components:
        die("only %d maven components, expected at least %d" % (n_maven, a.min_components))


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd")

    g = sub.add_parser("generate", help="build the bundle SBOM")
    g.add_argument("--classpath", required=True, help="runtime classpath from bazel cquery, one jar per line")
    g.add_argument("--external", required=True, help="bazel output_base/external")
    g.add_argument("--lock", action="append", required=True, help="rules_jvm_external lock file (repeatable)")
    g.add_argument("--bundle", required=True, help="unpacked bundle root (contains bin/, lib/)")
    g.add_argument("--syft", required=True, help="syft CycloneDX JSON of the bundle")
    g.add_argument("--version", required=True)
    g.add_argument("--release", default="")
    g.add_argument("--arch", required=True, help="x86_64 | aarch64")
    g.add_argument("--git-commit", default="")
    g.add_argument("--pom-cache", default="")
    g.add_argument("--offline", action="store_true", help="only use --pom-cache, no network")
    g.add_argument("--min-components", type=int, default=100)
    g.add_argument("--output", required=True)
    g.set_defaults(func=cmd_generate)

    a = p.parse_args()
    if not getattr(a, "func", None):
        p.print_help()
        sys.exit(2)
    a.func(a)


if __name__ == "__main__":
    main()
