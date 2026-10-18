#!/usr/bin/env bash
# Battery measurement as far as an emulator goes: the app paired, its link service up, the app in the
# background and the screen off, on battery for an idle window. The app's own battery record over that
# window - wake locks, alarms that woke the device, CPU time, network bytes - shows what its link costs
# while nothing happens (it rests after its idle time). The emulator has no real power profile:
# milliamp-hours belong to a real phone. Records only; never fails the job.
# Usage: battery-probe.sh PKG OUT [SECONDS]
set -uo pipefail

PKG=$1
OUT=$2
SECS=${3:-300}
LOG="$OUT/battery.txt"

{
  echo "== battery (idle ${SECS} s, app in the background, screen off, on battery)"
  adb shell input keyevent KEYCODE_HOME
  sleep 2
  echo "service at start: $(adb shell dumpsys activity services "$PKG" | grep -c ServiceRecord) record(s)"
  adb shell dumpsys batterystats --reset > /dev/null
  adb shell dumpsys battery unplug
  adb shell input keyevent KEYCODE_SLEEP
  sleep "$SECS"
  adb shell dumpsys batterystats "$PKG" > "$OUT/batterystats.txt"
  echo "service at end: $(adb shell dumpsys activity services "$PKG" | grep -c ServiceRecord) record(s)"
  grep -iE 'cpu time|wake lock|wakeup alarm|wifi network|mobile network|foreground service|jobs?:|syncs?:|estimated power use' \
    "$OUT/batterystats.txt" | head -40
  adb shell dumpsys notification --noredact | grep -A40 "pkg=$PKG" | grep -m2 -E 'android\.(title|text)='

  adb shell dumpsys battery reset
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard > /dev/null 2>&1 || true
} > "$LOG" 2>&1

cat "$LOG" >> "$OUT/screen-state.txt"
{ echo "### Battery (idle ${SECS} s)"; echo; echo '```'; cat "$LOG"; echo '```'; } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
exit 0
