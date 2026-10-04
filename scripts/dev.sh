#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
adb="$ANDROID_HOME/platform-tools/adb"
device="emulator-5554"

if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$adb" ]]; then
    echo "Set JAVA_HOME and ANDROID_HOME to your Java and Android SDK folders."
    exit 1
fi
if [[ ! -f app/google-services.json ]]; then
    echo "Add your Firebase configuration to app/google-services.json first."
    exit 1
fi

if ! "$adb" devices | awk '{print $1}' | grep -qx "$device"; then
    emulator_log="$(mktemp -t carmind-emulator)"
    echo "Starting CarMind_API_36. Emulator log: $emulator_log"
    nohup "$ANDROID_HOME/emulator/emulator" -avd CarMind_API_36 -port 5554 \
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

ANDROID_SERIAL="$device" ./gradlew installDebug -PauthEmulator=false --console=plain
"$adb" -s "$device" shell am force-stop com.carmind.app
"$adb" -s "$device" shell am start -W -n com.carmind.app/.LoginActivity
app_pid="$("$adb" -s "$device" shell pidof com.carmind.app | tr -d '\r')"
echo "CarMind is running. Streaming logs; press Ctrl+C to stop logging."
exec "$adb" -s "$device" logcat --pid="$app_pid" -v time
