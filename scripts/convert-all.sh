#!/usr/bin/env bash
# Converts every .schem under a directory tree.
#
# Usage: scripts/convert-all.sh <directory> [extra schemtocreate options...]
set -euo pipefail

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <directory> [options...]" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

JAR="build/libs/SchemToCreate.jar"
[[ -f "$JAR" ]] || { echo "Run scripts/build.sh first" >&2; exit 1; }

DIRECTORY="$1"
shift

exec java -jar "$JAR" --recursive "$DIRECTORY" --overwrite "$@"
