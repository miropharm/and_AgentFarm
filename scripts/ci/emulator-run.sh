#!/usr/bin/env bash
# Installs the APKs on the running emulator, runs instrumentation tests, captures screens, UI tree and logcat.
set -euo pipefail

PKG=com.muvusoft.agentfarm
OUT=emulator-out
mkdir -p "$OUT"

APP=$(find apks -name app-debug.apk | head -1)
TEST=$(find apks -name app-debug-androidTest.apk | head -1)

adb install -r -t "$APP"
adb install -r -t "$TEST"
adb logcat -c
# A slow emulator's own "isn't responding" dialogs otherwise cover the app's screenshots.
adb shell settings put global hide_error_dialogs 1 || true

# The fake Agent Farm host, over TLS with a throwaway certificate; the emulator reaches the runner at 10.0.2.2.
FH_PORT=8743
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 1 -subj "/CN=fakehost" \
  -keyout "$OUT/fh-key.pem" -out "$OUT/fh-cert.pem" 2> /dev/null
node tools/fake_host.js --port "$FH_PORT" --cert "$OUT/fh-cert.pem" --key "$OUT/fh-key.pem" \
  --addr "10.0.2.2:$FH_PORT" > "$OUT/fake-host.txt" 2>&1 &
FH_PID=$!
trap 'kill $FH_PID 2> /dev/null || true' EXIT
for _ in $(seq 20); do grep -q '^PAIR ' "$OUT/fake-host.txt" && break; sleep 0.5; done
PAIR_URI=$(sed -n 's/^PAIR //p' "$OUT/fake-host.txt")
if [ -z "$PAIR_URI" ]; then echo "::error::fake host did not start"; cat "$OUT/fake-host.txt"; exit 1; fi
rm -f "$OUT/fh-key.pem"

set +e
adb shell am instrument -w -e pairUri "'$PAIR_URI'" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumentation.txt"
set -e

# Each screenshot is logged with the rotation and night mode actually in effect, so a capture can be trusted.
shot() {
  adb shell am force-stop "$PKG"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 4
  adb exec-out screencap -p > "$OUT/$1.png"
  {
    echo "== $1"
    adb shell dumpsys window | grep -m1 -o 'mCurrentFocus=[^ ]* [^ ]*' || true
    adb shell dumpsys window displays | grep -m1 -o 'mCurrentRotation=[^ ]*' || true
    adb shell cmd uimode night
  } >> "$OUT/screen-state.txt"
}

rotate() {
  adb shell cmd window user-rotation lock "$1" || {
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation "$1"
  }
  sleep 2
}

rotate 0
shot main-portrait
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml "$OUT/main-portrait-ui.xml" > /dev/null

rotate 1
shot main-landscape
rotate 0

adb shell cmd uimode night yes
sleep 2
shot main-dark
adb shell cmd uimode night no

adb logcat -d > "$OUT/logcat.txt"

status=0
if ! grep -q "^OK (" "$OUT/instrumentation.txt"; then
  echo "::error::Instrumentation tests failed"
  status=1
fi
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::App crashed (FATAL EXCEPTION in logcat)"
  status=1
fi
exit $status
