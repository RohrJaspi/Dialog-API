#!/usr/bin/env bash
# Starts a local Paper test server with the freshly built DialogAPI jar.
#
#   scripts/run-server.sh <minecraft version>      e.g. 1.21.9, 1.21.11, 26.1.2, 26.2, 26.3
#
# The server lives in run/<version>/. Paper is downloaded once (SHA-256 verified) from the Paper
# download API. Minecraft 26.x needs Java 25, 1.21.x needs Java 21.
#
# You have to accept the Minecraft EULA yourself: the first start stops after writing
# run/<version>/eula.txt; read https://aka.ms/MinecraftEULA, set eula=true and start the script again.
set -euo pipefail

VERSION="${1:?usage: scripts/run-server.sh <minecraft version>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/run/$VERSION"
PLUGIN_JAR="$(ls "$ROOT"/plugin/target/DialogAPI-*.jar 2>/dev/null | grep -v original | head -n 1 || true)"

if [ -z "$PLUGIN_JAR" ]; then
    echo "No plugin jar found. Run ./mvnw package first." >&2
    exit 1
fi

case "$VERSION" in
    26.*) NEEDED_JAVA=25 ;;
    *)    NEEDED_JAVA=21 ;;
esac
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
if [ "${JAVA_MAJOR:-0}" -lt "$NEEDED_JAVA" ]; then
    echo "Minecraft $VERSION needs Java $NEEDED_JAVA, found Java ${JAVA_MAJOR:-unknown}." >&2
    exit 1
fi

mkdir -p "$RUN/plugins"
SERVER_JAR="$RUN/paper.jar"
if [ ! -f "$SERVER_JAR" ]; then
    META="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest")"
    URL="$(printf '%s' "$META" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
    SHA256="$(printf '%s' "$META" | python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["checksums"]["sha256"])')"
    echo "Downloading $URL"
    curl -fsSL -o "$SERVER_JAR.part" "$URL"
    ACTUAL="$(shasum -a 256 "$SERVER_JAR.part" | cut -d' ' -f1)"
    if [ "$ACTUAL" != "$SHA256" ]; then
        rm -f "$SERVER_JAR.part"
        echo "SHA-256 mismatch for the Paper download" >&2
        exit 1
    fi
    mv "$SERVER_JAR.part" "$SERVER_JAR"
fi

cp "$PLUGIN_JAR" "$RUN/plugins/DialogAPI.jar"
cd "$RUN"
exec java -Xmx2G -jar paper.jar --nogui
