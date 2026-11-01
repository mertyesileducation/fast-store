#!/usr/bin/env bash
# Derleme araclarini indirir (depoda tutulmazlar).
set -euo pipefail
cd "$(dirname "$0")"
R=https://repo1.maven.org/maven2
dl() { [ -f "$2" ] || { echo "İndiriliyor: $2"; curl -fsSL -o "$2" "$1"; }; }
dl $R/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar dalvik-dx-16.0.1.jar
dl $R/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar apksig-2.3.0.jar
# Android 14 API (yalnizca derleme icin). Android SDK kuruluysa oradan kopyalanir.
if [ ! -f android.jar ] && [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then
  cp "$ANDROID_HOME/platforms/android-34/android.jar" android.jar
fi
dl https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar android.jar
echo "Araçlar hazır."
