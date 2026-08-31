#!/bin/bash
# Show package info of the last built APK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/app/build/apk/DevBrowser-1.0.0.apk}"
[ -f "$APK" ] || { echo "no apk at $APK — run tools/build.sh"; exit 1; }
source "$ROOT/tools/env.sh" >/dev/null
"$AAPT2" dump badging "$APK" 2>/dev/null | head -25
echo
"$JAVA_HOME/bin/java" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify -v --print-certs "$APK" 2>&1 | head -10
