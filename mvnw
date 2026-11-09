#!/usr/bin/env sh
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
else
    exec ./gradlew build "$@"
fi
