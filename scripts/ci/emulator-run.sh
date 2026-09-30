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

set +e
adb shell am instrument -w "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumentation.txt"
set -e

shot() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 3
  adb exec-out screencap -p > "$OUT/$1.png"
}

adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
shot main-portrait
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml "$OUT/main-portrait-ui.xml" > /dev/null

adb shell settings put system user_rotation 1
shot main-landscape
adb shell settings put system user_rotation 0

adb shell cmd uimode night yes
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
