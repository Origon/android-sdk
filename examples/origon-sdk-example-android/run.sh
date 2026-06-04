#!/usr/bin/env bash
#
# Build, install, and launch the Origon SDK example app via CLI
# (no Android Studio required).
#
# Prerequisites (one-time):
#   brew install openjdk@21
#   brew install --cask android-commandlinetools
#   sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"
#   # for --emulator: also install the emulator + an arm64 system image, e.g.
#   sdkmanager "emulator" "system-images;android-35;google_apis;arm64-v8a"
#   avdmanager create avd -n Origon_API35 \
#       -k "system-images;android-35;google_apis;arm64-v8a" --device pixel_6
#
# Usage:
#   ./run.sh                      # build + install + launch on first connected device
#   ./run.sh --emulator           # boot first available AVD, then build/install/launch
#   ./run.sh --emulator <name>    # boot the named AVD (e.g. Origon_API35), then run
#   ./run.sh <name>               # shorthand for --emulator <name>
#   ./run.sh --apk-only           # just build the APK
#   ./run.sh --logcat             # stream logcat from an already-installed app
#   ./run.sh --emulator <name> --logcat   # boot AVD, then just stream logcat

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE="origon.example.android"
ACTIVITY=".MainActivity"

usage() {
    cat <<'EOF'
Usage:
  ./run.sh                      # build + install + launch on first connected device
  ./run.sh --emulator           # boot first available AVD, then build/install/launch
  ./run.sh --emulator <name>    # boot the named AVD (e.g. Origon_API35), then run
  ./run.sh <name>               # shorthand for --emulator <name>
  ./run.sh --apk-only           # just build the APK
  ./run.sh --logcat             # stream logcat from an already-installed app
  ./run.sh --emulator <name> --logcat   # boot AVD, then just stream logcat
EOF
}

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
EMULATOR_BIN="$ANDROID_HOME/emulator/emulator"

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
BOOT_EMULATOR=false
EMULATOR_NAME=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --apk-only) APK_ONLY=true ;;
        --logcat)   LOGCAT_ONLY=true ;;
        -e|--emulator)
            BOOT_EMULATOR=true
            # Optional value: consume the next token unless it's another flag.
            if [[ -n "${2:-}" && "${2:0:1}" != "-" ]]; then EMULATOR_NAME="$2"; shift; fi
            ;;
        -h|--help)  usage; exit 0 ;;
        -*)         echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
        *)          BOOT_EMULATOR=true; EMULATOR_NAME="$1" ;;  # bare AVD name
    esac
    shift
done

pick_device() {
    "$ADB" devices | grep -w device | head -1 | awk -F'\t' '{print $1}'
}

# Serial of the running emulator whose AVD name matches $1, or empty.
emulator_serial_for_avd() {
    local want="$1" serial name
    for serial in $("$ADB" devices | awk '/^emulator-/{print $1}'); do
        name=$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | sed -n '1p')
        [[ "$name" == "$want" ]] && { echo "$serial"; return 0; }
    done
    return 1
}

# ── Boot emulator (optional) ───────────────────────────────────────────
# When requested, ensures the target AVD is running and pins the rest of the
# flow to it via DEVICE_OVERRIDE.
DEVICE_OVERRIDE=""
if $BOOT_EMULATOR; then
    [[ -x "$EMULATOR_BIN" ]] || {
        echo "ERROR: emulator not found at $EMULATOR_BIN (sdkmanager \"emulator\")." >&2; exit 1; }

    AVDS=$("$EMULATOR_BIN" -list-avds 2>/dev/null || true)
    [[ -n "$AVDS" ]] || { echo "ERROR: no AVDs exist. Create one with avdmanager." >&2; exit 1; }

    # Default to the first AVD when no name was given.
    [[ -z "$EMULATOR_NAME" ]] && EMULATOR_NAME=$(printf '%s\n' "$AVDS" | head -1)

    if ! printf '%s\n' "$AVDS" | grep -qx "$EMULATOR_NAME"; then
        echo "ERROR: AVD '$EMULATOR_NAME' not found. Available:" >&2
        printf '  %s\n' $AVDS >&2
        exit 1
    fi

    "$ADB" start-server >/dev/null 2>&1 || true
    SERIAL=$(emulator_serial_for_avd "$EMULATOR_NAME" || true)

    if [[ -n "$SERIAL" ]]; then
        echo "== Emulator $EMULATOR_NAME already running ($SERIAL) =="
    else
        echo "== Booting emulator $EMULATOR_NAME =="
        LOG="${TMPDIR:-/tmp}/emulator-$EMULATOR_NAME.log"
        nohup "$EMULATOR_BIN" @"$EMULATOR_NAME" >"$LOG" 2>&1 &
        echo "   (emulator log: $LOG)"
        # Wait for the AVD's serial to register with adb (up to ~4 min).
        for _ in $(seq 1 120); do
            SERIAL=$(emulator_serial_for_avd "$EMULATOR_NAME" || true)
            [[ -n "$SERIAL" ]] && break
            sleep 2
        done
        [[ -n "$SERIAL" ]] || { echo "ERROR: emulator did not register with adb. See $LOG" >&2; exit 1; }
    fi

    echo "== Waiting for $EMULATOR_NAME to finish booting =="
    "$ADB" -s "$SERIAL" wait-for-device
    until [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 2
    done
    "$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true  # dismiss lock screen
    echo "== $EMULATOR_NAME ready ($SERIAL) =="
    DEVICE_OVERRIDE="$SERIAL"
fi

# Device for install/launch/logcat: the booted emulator, else first connected.
resolve_device() { [[ -n "$DEVICE_OVERRIDE" ]] && echo "$DEVICE_OVERRIDE" || pick_device; }

# ── Logcat-only mode ───────────────────────────────────────────────────
if $LOGCAT_ONLY; then
    DEVICE=$(resolve_device)
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
DEVICE=$(resolve_device)
[[ -z "$DEVICE" ]] && { echo "ERROR: no ADB device connected (enable USB/Wireless debugging, or pass --emulator)." >&2; exit 1; }
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
