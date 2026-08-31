#!/bin/bash
# Toolchain environment for DevBrowser builds (offline, Gradle-free).
# Source this file, or let build.sh do it for you.
#
# The toolchain lives under $DEVTOOLS (default ~/Tools); see docs/TOOLCHAIN.md
# for how each piece was obtained.

DEVTOOLS="${DEVTOOLS:-$HOME/Tools}"

export DEVTOOLS
export JAVA_HOME="$DEVTOOLS/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_JAR="$DEVTOOLS/platforms/android-34/android.jar"
export AAPT2="$DEVTOOLS/aapt2/package/bin/x64/linux/aapt2"
export D8_JAR="$DEVTOOLS/build-tools/lib/d8.jar"
export APKSIGNER_JAR="$DEVTOOLS/build-tools/lib/apksigner_33_0_2.jar"

echo "env:  JDK=$(java -version 2>&1 | head -1)"
echo "env:  AAPT2=$AAPT2"
echo "env:  android.jar=$ANDROID_JAR"
