#!/usr/bin/env bash
# FluxDM Launcher — macOS / Linux
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/FluxDM-2.0.0.jar"

if ! command -v java &>/dev/null; then
    echo "ERROR: Java not found. Install Java 11+ from https://adoptium.net" >&2
    exit 1
fi

JAVA_OPTS="-Xms64m -Xmx512m"
if [[ "$OSTYPE" == "darwin"* ]]; then
    JAVA_OPTS="$JAVA_OPTS \
        -Dapple.laf.useScreenMenuBar=true \
        -Dapple.awt.application.name=FluxDM \
        -Dcom.apple.mrj.application.apple.menu.about.name=FluxDM"
fi

exec java $JAVA_OPTS -jar "$JAR" "$@"
