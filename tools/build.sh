#!/bin/bash
# DevBrowser offline APK build (aapt2 + javac + D8 + apksigner, no Gradle).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
BUILD="$APP/build"
GEN="$BUILD/gen"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"
APKDIR="$BUILD/apk"
KEYSTORE="$ROOT/tools/keystore/devbrowser.keystore"
KEYSTORE_PASS="${KEYSTORE_PASS:-devbrowser}"
VERSION="1.1.0"
PKG=com.devbrowser.app

# --- environment ----------------------------------------------------------
if [ -z "${DEVTOOLS:-}" ]; then
  # shellcheck disable=SC1091
  source "$ROOT/tools/env.sh" >/dev/null
fi
# The toolchain lives outside the repo and is not persisted between sessions,
# so re-bootstrap it automatically whenever it is missing.
if [ ! -x "$JAVA_HOME/bin/javac" ] || [ ! -f "${AAPT2:-}" ]; then
  echo "==> toolchain missing — bootstrapping (first run only)"
  bash "$ROOT/tools/setup-toolchain.sh"
fi
JAVA_BIN="$JAVA_HOME/bin"
AAPT2_BIN="${AAPT2:-$DEVTOOLS/aapt2/package/bin/x64/linux/aapt2}"
ANDROID_JAR="${ANDROID_JAR:-$DEVTOOLS/platforms/android-34/android.jar}"
D8_JAR="${D8_JAR:-$DEVTOOLS/build-tools/lib/d8.jar}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$DEVTOOLS/build-tools/lib/apksigner_33_0_2.jar}"

for f in "$AAPT2_BIN" "$ANDROID_JAR" "$D8_JAR" "$APKSIGNER_JAR" "$JAVA_BIN/javac"; do
  [ -f "$f" ] || { echo "MISSING tool: $f — run docs/TOOLCHAIN.md instructions"; exit 1; }
done

echo "==> clean"
rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$APKDIR"

echo "==> aapt2 compile resources"
"$AAPT2_BIN" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> aapt2 link (manifest + resources -> R.java + unsigned apk)"
"$AAPT2_BIN" link \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  -A "$APP/assets" \
  --auto-add-overlay \
  --java "$GEN" \
  -o "$APKDIR/app-unsigned.apk"

echo "==> javac (Java sources -> classes)"
find "$GEN" "$APP/src" -name "*.java" > "$BUILD/sources.txt"
# -cp android.jar (not -bootclasspath): lambdas compile via the JDK's
# LambdaMetafactory, Android APIs resolve from the platform jar; D8 dexes
# the result against android.jar.
"$JAVA_BIN/javac" -encoding UTF-8 -source 8 -target 8 \
  -cp "$ANDROID_JAR" -d "$CLASSES" @"$BUILD/sources.txt" 2> >(grep -v "bootstrap class path" >&2 || true)

echo "==> D8 dexing -> classes.dex"
# shellcheck disable=SC2046
"$JAVA_BIN/java" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --min-api 24 --lib "$ANDROID_JAR" \
  --output "$DEX" $(find "$CLASSES" -name "*.class")

echo "==> zip classes.dex + zipalign (4-byte)"
python3 "$ROOT/tools/finalize_apk.py" \
  "$APKDIR/app-unsigned.apk" "$DEX/classes.dex" "$APKDIR/app-aligned.apk"

echo "==> apksigner (v1+v2, debug keystore)"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  "$JAVA_BIN/keytool" -genkeypair -v \
    -keystore "$KEYSTORE" -alias devbrowser \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -dname "CN=DevBrowser, OU=Dev, O=DevBrowser, C=LK" >/dev/null
fi
OUT="$APKDIR/DevBrowser-$VERSION.apk"
"$JAVA_BIN/java" -jar "$APKSIGNER_JAR" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" \
  --out "$OUT" "$APKDIR/app-aligned.apk" 2>/dev/null || \
"$JAVA_BIN/java" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool sign \
  --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" \
  --out "$OUT" "$APKDIR/app-aligned.apk"

echo "==> verify"
"$JAVA_BIN/java" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify -v --print-certs "$OUT" | head -8
echo ""
echo "BUILT: $OUT ($(du -h "$OUT" | cut -f1))"
