#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEST="$ROOT/gradle/wrapper/gradle-wrapper.jar"
URL="https://github.com/gradle/gradle/raw/refs/tags/v8.13.0/gradle/wrapper/gradle-wrapper.jar"
mkdir -p "$(dirname "$DEST")"
if command -v curl >/dev/null 2>&1; then
  curl -fL "$URL" -o "$DEST"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$DEST" "$URL"
else
  echo "Install curl or wget, then run this script again."
  exit 1
fi
echo "Gradle wrapper ready: $DEST"
