#!/bin/bash
# Reproducible offline toolchain bootstrap for DevBrowser development.
#
# This sandbox has no access to Google/apt mirrors, so every binary is
# pulled from channels that ARE reachable (npm registry, git repos via
# the GitHub contents/blob API, PyPI). On a normal machine the same
# components come from the official SDK manager — see docs/TOOLCHAIN.md.
#
# Usage:  bash tools/setup-toolchain.sh
set -euo pipefail

DEVTOOLS="${DEVTOOLS:-$HOME/Tools}"
mkdir -p "$DEVTOOLS/build-tools/lib" "$DEVTOOLS/platforms/android-34"

echo "== 1/6  JDK 17 (npm: javajre-linux-64, contains javac/jarsigner/keytool)"
if [ ! -x "$DEVTOOLS/jdk-17/bin/javac" ]; then
  mkdir -p "$DEVTOOLS/dl" && cd "$DEVTOOLS/dl"
  npm pack javajre-linux-64 >/dev/null
  tar xzf javajre-linux-64-*.tgz
  cp -r package/jre "$DEVTOOLS/jdk-17"
  rm -rf "$DEVTOOLS/dl"
fi

echo "== 2/6  aapt2 (npm: aaptjs3 linux x64)"
if [ ! -x "$DEVTOOLS/aapt2/package/bin/x64/linux/aapt2" ]; then
  mkdir -p "$DEVTOOLS/aapt2" && cd "$DEVTOOLS/aapt2"
  npm pack aaptjs3 >/dev/null && tar xzf aaptjs3-*.tgz
  chmod +x package/bin/x64/linux/aapt2
fi

echo "== 3/6  android.jar API 34 (git blob: Reginer/aosp-android-jar)"
if [ ! -s "$DEVTOOLS/platforms/android-34/android.jar" ]; then
  cd /tmp && rm -rf reginer && git init -q reginer && cd reginer
  git remote add origin https://github.com/Reginer/aosp-android-jar.git
  git config core.sparseCheckout true
  git sparse-checkout set android-34
  git fetch -q --depth 1 origin main && git checkout -q FETCH_HEAD -- android-34
  cp android-34/android.jar "$DEVTOOLS/platforms/android-34/android.jar"
fi

echo "== 4/6  d8.jar + apksigner.jar (git blob: cyber-Ray/CCN + patrickfav/uber-apk-signer)"
if [ ! -s "$DEVTOOLS/build-tools/lib/d8.jar" ]; then
  # d8 33.0.2 (r8)
  gh api "repos/cyber-Ray/CCN/git/blobs/f6c34f72b97851bb19a5a7adf0d43de3dbc25950" \
    --jq .content 2>/dev/null | base64 -d > "$DEVTOOLS/build-tools/lib/d8.jar"
  # apksigner tool jar (SDK 33.0.2)
  gh api "repos/patrickfav/uber-apk-signer/contents/src/main/resources/lib/apksigner_33_0_2.jar?ref=main" \
    --jq .content 2>/dev/null | base64 -d > "$DEVTOOLS/build-tools/lib/apksigner_33_0_2.jar"
fi

echo "== 5/6  Python media toolchain (bpy/Blender, ffmpeg, Pillow, matplotlib, trimesh, PySide6)"
python3 -m venv "$DEVTOOLS/venv"
"$DEVTOOLS/venv/bin/pip" install --quiet \
  bpy imageio-ffmpeg pillow numpy matplotlib trimesh svglib PySide6-Essentials 2>/dev/null || \
"$DEVTOOLS/venv/bin/pip" install --quiet \
  imageio-ffmpeg pillow numpy matplotlib trimesh svglib PySide6-Essentials

echo "== 6/6  verify"
SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SOURCE_DIR/env.sh"
"$JAVA_HOME/bin/javac" -version
"$AAPT2" version
"$JAVA_HOME/bin/java" -cp "$D8_JAR" com.android.tools.r8.D8 --version | head -1
"$JAVA_HOME/bin/java" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool --version
echo
echo "Toolchain ready. Build the app with:  bash tools/build.sh"
