#!/usr/bin/env bash
# Doze measurement: with the app and its link service up, force deep idle, emit a question from
# the fake host and time how long its alert takes to appear. Records only; never fails the job.
# Usage: doze-probe.sh PKG OUT FH_PORT
set -uo pipefail

PKG=$1
OUT=$2
FH_PORT=$3
ASK=a_doze_$RANDOM
LOG="$OUT/doze.txt"

{
  echo "== doze"
  adb shell dumpsys deviceidle enable deep
  adb shell dumpsys battery unplug
  adb shell input keyevent KEYCODE_SLEEP
  sleep 2
  adb shell dumpsys deviceidle force-idle deep
  echo "deep state: $(adb shell dumpsys deviceidle get deep | tr -d '\r')"
  echo "service: $(adb shell dumpsys activity services "$PKG" | grep -c ServiceRecord) record(s)"

  data="{\"session\":\"s_doze\",\"agent\":\"developer\",\"askId\":\"$ASK\",\"question\":\"Doze?\",\"options\":[{\"label\":\"Evet\"}]}"
  echo "emit: $(curl -sk -X POST -H 'Content-Type: application/json' \
    -d "{\"type\":\"ask.opened\",\"data\":$data}" "https://127.0.0.1:$FH_PORT/_test/event")"

  seen=""
  for s in $(seq 1 30); do
    if adb shell dumpsys notification --noredact | grep -q "$ASK"; then seen=$s; break; fi
    sleep 1
  done
  if [ -n "$seen" ]; then echo "alert: shown after ~${seen} s in deep idle"; else echo "alert: NOT shown within 30 s in deep idle"; fi
  echo "deep state after: $(adb shell dumpsys deviceidle get deep | tr -d '\r')"
  adb shell dumpsys notification --noredact > "$OUT/doze-notifications.txt"
  grep -E 'android\.(title|text)=' "$OUT/doze-notifications.txt" | head -6

  adb shell dumpsys deviceidle unforce
  adb shell dumpsys battery reset
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard > /dev/null 2>&1 || true
} > "$LOG" 2>&1

cat "$LOG" >> "$OUT/screen-state.txt"
exit 0
