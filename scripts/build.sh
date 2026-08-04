#!/usr/bin/env bash
# Builds SchemToCreate and leaves the standalone jar in build/libs/SchemToCreate.jar.
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Running tests and building the standalone jar"
./gradlew clean build --console=plain

JAR="build/libs/SchemToCreate.jar"
if [[ ! -f "$JAR" ]]; then
    echo "Build finished but $JAR is missing" >&2
    exit 1
fi

echo
echo "==> Built $JAR ($(du -h "$JAR" | cut -f1))"
echo "    Run it with: java -jar $JAR <input.schem>"
