#!/bin/sh
# Percona MongoDB Search (mongot) launcher.
# The actual bundle (launcher + bundled JDK + jars) lives in /usr/lib/percona-server-mongodb-mongot.
# This wrapper exists because the upstream `mongot` script resolves siblings via
# ${BASH_SOURCE[0]} and therefore must be invoked from its real directory.
exec /usr/lib/percona-server-mongodb-mongot/mongot "$@"
