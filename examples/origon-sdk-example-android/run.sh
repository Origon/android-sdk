#!/usr/bin/env bash
#
# Build, install, and launch the Origon SDK example app via CLI
# (no Android Studio required).
#
# Prerequisites (one-time):
#   brew install openjdk@21
#   brew install --cask android-commandlinetools
#   sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"
#
# Usage:
#   ./run.sh              # build + install + launch + logcat
#   ./run.sh --apk-only   # just build the APK
#   ./run.sh --logcat     # stream logcat from an already-installed app

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE="origon.example.android"
ACTIVITY=".MainActivity"

# ── Resolve Android SDK ────────────────────────────────────────────────
if [[ -z "${ANDROID_HOME:-}" ]]; then
    for c in "$HOME/Library/Android/sdk" \
             "/opt/homebrew/share/android-commandlinetools" \
             "/usr/local/share/android-commandlinetools"; do
        [[ -d "$c" ]] && { export ANDROID_HOME="$c"; break; }
    done
fi
if [[ -z "${ANDROID_HOME:-}" || ! -d "${ANDROID_HOME:-}" ]]; then
    echo "ERROR: Android SDK not found. Set ANDROID_HOME to your SDK root." >&2
    exit 1
fi
ADB="$ANDROID_HOME/platform-tools/adb"

# ── Pin a compatible JDK (AGP 8.7 needs JDK 17-21) ─────────────────────
jdk_major() { "$1/bin/java" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1; }
if [[ -z "${JAVA_HOME:-}" || "$(jdk_major "${JAVA_HOME:-/nonexistent}" 2>/dev/null || echo 99)" -gt 21 ]]; then
    for c in "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
             "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
             "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"; do
        [[ -x "$c/bin/java" ]] && { export JAVA_HOME="$c"; break; }
    done
fi

# ── Parse args ─────────────────────────────────────────────────────────
APK_ONLY=false
LOGCAT_ONLY=false
for arg in "$@"; do
    case "$arg" in
        --apk-only) APK_ONLY=true ;;
        --logcat)   LOGCAT_ONLY=true ;;
        -h|--help)  echo "Usage: $0 [--apk-only | --logcat]"; exit 0 ;;
    esac
done

pick_device() {
    "$ADB" devices | grep -w device | head -1 | awk -F'\t' '{print $1}'
}

# ── Logcat-only mode ───────────────────────────────────────────────────
if $LOGCAT_ONLY; then
    DEVICE=$(pick_device)
    [[ -z "$DEVICE" ]] && { echo "ERROR: no ADB device connected." >&2; exit 1; }
    PID=$("$ADB" -s "$DEVICE" shell pidof "$PACKAGE" 2>/dev/null || true)
    if [[ -n "$PID" ]]; then exec "$ADB" -s "$DEVICE" logcat --pid="$PID" '*:V'
    else exec "$ADB" -s "$DEVICE" logcat -s "AndroidRuntime:E" "session:V" "moq:V"; fi
fi

# ── Build ──────────────────────────────────────────────────────────────
echo "== Building APK =="
cd "$SCRIPT_DIR"
./gradlew :app:assembleDebug

APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "ERROR: APK not found at $APK" >&2; exit 1; }
$APK_ONLY && { echo "APK: $APK"; exit 0; }

# ── Install & launch ───────────────────────────────────────────────────
DEVICE=$(pick_device)
[[ -z "$DEVICE" ]] && { echo "ERROR: no ADB device connected (enable USB/Wireless debugging)." >&2; exit 1; }
echo "== Installing on $DEVICE =="
"$ADB" -s "$DEVICE" install -r "$APK"

echo "== Launching $PACKAGE =="
"$ADB" -s "$DEVICE" shell am force-stop "$PACKAGE" 2>/dev/null || true
"$ADB" -s "$DEVICE" shell am start -n "$PACKAGE/$ACTIVITY"

echo "== Streaming logcat (Ctrl+C to stop) =="
sleep 1
PID=$("$ADB" -s "$DEVICE" shell pidof "$PACKAGE" 2>/dev/null || true)
if [[ -n "$PID" ]]; then "$ADB" -s "$DEVICE" logcat --pid="$PID" '*:V'
else "$ADB" -s "$DEVICE" logcat -s "AndroidRuntime:E" "session:V" "moq:V"; fi
