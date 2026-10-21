#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$ANDROID_HOME/platform-tools/adb"
device=""

if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$adb" ]]; then
    echo "Set JAVA_HOME and ANDROID_HOME to your Java and Android SDK folders."
    exit 1
fi
if [[ ! -f app/google-services.json ]]; then
    echo "Add your Firebase configuration to app/google-services.json first."
    exit 1
fi

device="$("$adb" devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')"
device="${device:-emulator-5554}"
if ! "$adb" devices | awk '{print $1}' | grep -qx "$device"; then
    emulator_log="$(mktemp -t carmind-emulator)"
    selected_avd="${CARMIND_AVD:-$("$ANDROID_HOME/emulator/emulator" -list-avds | head -n 1)}"
    if [[ -z "$selected_avd" ]]; then
        echo "Create an Android Virtual Device in Android Studio first."
        exit 1
    fi
    echo "Starting $selected_avd. Emulator log: $emulator_log"
    nohup "$ANDROID_HOME/emulator/emulator" -avd "$selected_avd" -port 5554 \
        >"$emulator_log" 2>&1 </dev/null &
fi

echo "Waiting for Android to finish booting..."
ready=false
for ((attempt=0; attempt<180; attempt++)); do
    if [[ "$("$adb" -s "$device" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        ready=true
        break
    fi
    sleep 2
done
if [[ "$ready" != true ]]; then
    echo "Emulator did not finish booting. Check its window and try again."
    exit 1
fi

ANDROID_SERIAL="$device" ./gradlew installDebug --console=plain --quiet
"$adb" -s "$device" shell am force-stop com.carmind.app
"$adb" -s "$device" shell am start -W -n com.carmind.app/.LoginActivity >/dev/null
echo "CarMind is running. Sign in with Firebase, then choose Classroom demo."
