#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Script for UNIX / Linux / macOS
# ----------------------------------------------------------------------------

if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi

MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.6/apache-maven-3.9.6"
if [ -x "$MAVEN_HOME/bin/mvn" ]; then
    exec "$MAVEN_HOME/bin/mvn" "$@"
fi

echo "[Maven Wrapper] System 'mvn' not detected in PATH. Downloading Maven 3.9.6..."
DIST_DIR="$HOME/.m2/wrapper/dists/apache-maven-3.9.6"
mkdir -p "$DIST_DIR"
ZIP_FILE="$DIST_DIR/apache-maven-3.9.6-bin.zip"
curl -sSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip" -o "$ZIP_FILE"
unzip -q -o "$ZIP_FILE" -d "$DIST_DIR"

if [ -x "$MAVEN_HOME/bin/mvn" ]; then
    exec "$MAVEN_HOME/bin/mvn" "$@"
else
    echo "[ERROR] Failed to run Maven."
    exit 1
fi
