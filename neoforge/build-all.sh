#!/usr/bin/env bash
set -e
echo "========================================="
echo "  LazoDiscs NeoForge - Build All Versions"
echo "========================================="
BUILD_OK=1
for v in 1.21.*/ ; do
    v="${v%/}"
    if [ -d "$v" ]; then
        echo "[BUILD] NeoForge $v"
        (cd "$v" && ./gradlew build --no-daemon) || { echo "[FAILED] NeoForge $v"; BUILD_OK=0; }
        echo ""
    fi
done
echo "========================================="
if [ "$BUILD_OK" -eq 1 ]; then
    echo "  All versions built successfully!"
else
    echo "  Some versions FAILED - check log above."
fi
echo "========================================="
