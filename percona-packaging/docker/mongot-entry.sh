#!/bin/sh
#
# Entry point for the Percona Search for MongoDB (mongot) dev image.
#
# This lets operators pass mongot options as arguments to the container, e.g.:
#
#   docker run ... percona-search-mongodb --config /path/to/mongot.yml
#
# while a plain `docker run` still starts mongot with the bundled default
# config supplied via CMD. Any other command (e.g. `sh`) is execed as-is
# so the image stays debuggable.
#
# POSIX sh only (no bashisms), so it runs on minimal base images.
set -e

# First argument is a flag -> the operator is passing mongot options; prepend
# the binary so they don't have to restate it.
case "${1:-}" in
    -*) set -- mongot "$@" ;;
esac

# Normalize the bare `mongot` command to its absolute path.
if [ "$1" = 'mongot' ]; then
    shift
    set -- /usr/bin/mongot "$@"
fi

exec "$@"