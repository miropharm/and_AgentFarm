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
# A permission dialog would cover the UI tests; the prompt itself is the user's first-run experience.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
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

# A freshly booted emulator can still answer ENETUNREACH; the first tests must not race its network.
for _ in $(seq 60); do adb shell ping -c 1 -W 1 10.0.2.2 > /dev/null 2>&1 && break; sleep 1; done
adb shell ping -c 1 -W 1 10.0.2.2 > /dev/null 2>&1 || echo "::warning::emulator cannot reach 10.0.2.2 yet"

# The shell's own log tags, streamed to their own file so a wrapped system log cannot hide them.
adb logcat -v time -s AFPage:V chromium:V > "$OUT/logcat-shell.txt" 2>&1 &
SHELL_LOG_PID=$!

set +e
adb shell am instrument -w -e pairUri "'$PAIR_URI'" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumentation.txt"
set -e

# Scenes the UI tests reached by pressing (androidTest Shots.kt), kept in the app's external files.
adb pull "/sdcard/Android/data/$PKG/files/shots" "$OUT/" > /dev/null 2>&1 || echo "::warning::no test scene shots were pulled"

# Each screenshot is logged with the rotation and night mode actually in effect, so a capture can be trusted.
# shot NAME ROTATION: the rotation is applied after the app is on screen (launching it reset an earlier one).
shot() {
  adb shell am force-stop "$PKG"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  rotate "$2"
  sleep 3
  adb exec-out screencap -p > "$OUT/$1.png"
  {
    echo "== $1"
    adb shell dumpsys window | grep -m1 -o 'mCurrentFocus=[^ ]* [^ ]*' || true
    adb shell dumpsys window displays | grep -m1 -o 'mCurrentRotation=[^ ]*' || true
    adb shell cmd uimode night
  } >> "$OUT/screen-state.txt"
}

rotate() {
  for _ in 1 2 3; do
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation "$1"
    adb shell cmd window user-rotation lock "$1" > /dev/null 2>&1 || true
    sleep 2
    adb shell dumpsys window displays | grep -m1 -o 'mCurrentRotation=[^ ]*' | grep -q "ROTATION_$(( $1 * 90 ))" && return 0
  done
  echo "::warning::display did not rotate to $1 (see screen-state.txt)"
}

shot main-portrait 0
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml "$OUT/main-portrait-ui.xml" > /dev/null

shot main-landscape 1
rotate 0

# The link service's ongoing notification, as the user sees it in the shade.
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 6
adb shell cmd statusbar expand-notifications
sleep 2
adb exec-out screencap -p > "$OUT/notification-shade.png"
{
  echo "== notification-shade"
  adb shell dumpsys notification --noredact > "$OUT/notifications.txt" || true
  grep -c "pkg=$PKG" "$OUT/notifications.txt" | sed 's/^/records from app: /'
  grep -E 'android\.(title|text)=' "$OUT/notifications.txt" | head -6
} >> "$OUT/screen-state.txt"
adb shell cmd statusbar collapse

# The quick settings tile, added and shown with the farm paired by the tests.
# (The shade was just collapsed: expanding at once lost the race and shot the app.)
sleep 2
adb shell cmd statusbar add-tile "$PKG/.widget.StatusTile" > /dev/null 2>&1 || echo "::warning::could not add the tile"
sleep 2
adb shell cmd statusbar expand-settings
sleep 4
adb exec-out screencap -p > "$OUT/qs-tile.png"
{
  echo "== qs-tile"
  adb shell dumpsys activity services "$PKG/.widget.StatusTile" | grep -m3 -E 'ServiceRecord|bound' || echo "tile service not bound"
} >> "$OUT/screen-state.txt"
adb shell cmd statusbar collapse

bash scripts/ci/doze-probe.sh "$PKG" "$OUT" "$FH_PORT"

adb shell cmd uimode night yes
sleep 2
shot main-dark 0
adb shell cmd uimode night no

# The largest system font the user can pick: nothing clipped, nothing overlapping (measured from the dump).
adb shell settings put system font_scale 2.0
sleep 2
shot main-font200 0
adb shell settings get system font_scale | sed 's/^/font_scale: /' >> "$OUT/screen-state.txt"
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml "$OUT/main-font200-ui.xml" > /dev/null
adb shell settings put system font_scale 1.0

adb logcat -d > "$OUT/logcat.txt"
kill "$SHELL_LOG_PID" 2> /dev/null || true

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
