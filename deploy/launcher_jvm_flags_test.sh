#!/usr/bin/env bash

# Exercise the packaged launcher with a fake bundled JVM that records its arguments.
set -euo pipefail

launcher="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
jdk_layout="$2"
main_class="$3"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail() {
  echo "FAIL (${launcher}, ${os}, ${scenario}): $*" >&2
  exit 1
}

for os in linux-gnu darwin; do
  bundle="${work}/${os}"
  java_home="${bundle}/${jdk_layout}"
  if [[ "$os" == darwin ]]; then
    java_home="${java_home}/Contents/Home"
  fi
  mkdir -p "${java_home}/bin"
  cp "$launcher" "${bundle}/mongot"

  # Isolate argument handling from the host's mount table and crypto configuration.
  cat >"${bundle}/bc-fips-env.sh" <<'EOF'
bc_fips_configure() {
  BC_FIPS_FLAGS=("-Dlauncher.crypto=true")
}
EOF
  cat >"${java_home}/bin/java" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >"${JAVA_ARGS_FILE}"
EOF
  chmod +x "${java_home}/bin/java"

  for scenario in default custom conflicting; do
    launcher_args=()
    case "$scenario" in
      custom)
        launcher_args=(--jvm-flags "-Xmx64m -Dlauncher.custom=true")
        ;;
      conflicting)
        launcher_args=(--jvm-flags "-Xmx64m -Dlauncher.custom=true -XX:+UseBASE64Intrinsics")
        ;;
    esac
    env OSTYPE="$os" JAVA_ARGS_FILE="${work}/java-args" \
      bash "${bundle}/mongot" ${launcher_args[@]+"${launcher_args[@]}"} \
      --config "config path with spaces" --verbose

    args=()
    while IFS= read -r arg; do
      args+=("$arg")
    done <"${work}/java-args"

    main_index=-1
    classpath_index=-1
    mitigation_index=-1
    base64_flag=""
    custom_flag=""
    crypto_flag=""
    for ((i = 0; i < ${#args[@]}; i++)); do
      arg="${args[i]}"
      if [[ "$arg" == "$main_class" ]]; then
        main_index="$i"
        break
      fi
      case "$arg" in
        -cp) classpath_index="$i" ;;
        -XX:+UseBASE64Intrinsics|-XX:-UseBASE64Intrinsics)
          base64_flag="$arg"
          mitigation_index="$i"
          ;;
        -Dlauncher.custom=true) custom_flag="$arg" ;;
        -Dlauncher.crypto=true) crypto_flag="$arg" ;;
      esac
    done

    [[ "$main_index" -ge 0 ]] || fail "missing main class ${main_class}"
    [[ "$base64_flag" == -XX:-UseBASE64Intrinsics ]] \
      || fail "Base64 intrinsics are not disabled after custom JVM flags"
    [[ "$mitigation_index" -lt "$classpath_index" ]] \
      || fail "mitigation must be a JVM option before the classpath"
    [[ "$crypto_flag" == -Dlauncher.crypto=true ]] || fail "lost crypto JVM flags"
    if [[ "$scenario" != default ]]; then
      [[ "$custom_flag" == -Dlauncher.custom=true ]] || fail "lost custom JVM flags"
    fi
    [[ "${#args[@]}" -eq $((main_index + 4)) ]] || fail "unexpected application arguments"
    [[ "${args[main_index + 1]}" == --config ]] || fail "lost config option"
    [[ "${args[main_index + 2]}" == "config path with spaces" ]] || fail "split config path"
    [[ "${args[main_index + 3]}" == --verbose ]] || fail "lost trailing argument"
  done
done

echo "PASS: ${launcher}"
