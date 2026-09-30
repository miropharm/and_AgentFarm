#!/usr/bin/env bash
# Runs instrumentation + Robo tests on Firebase Test Lab and downloads the raw results (video, screenshots, logcat).
set -uo pipefail

OUT=testlab-out
mkdir -p "$OUT"
DEVICE="${TESTLAB_DEVICE:-model=MediumPhone.arm,version=34}"
DIR="run-${GITHUB_RUN_NUMBER}-${GITHUB_RUN_ATTEMPT}"

APP=$(find apks -name app-debug.apk | head -1)
TEST=$(find apks -name app-debug-androidTest.apk | head -1)

gcloud config set project "$FIREBASE_PROJECT"
gcloud services enable testing.googleapis.com toolresults.googleapis.com --quiet

gcloud firebase test android models list \
  --format="table(id,manufacturer,name,form,supportedVersionIds.list())" > "$OUT/models.txt" 2>&1 || true

gcloud firebase test android run --type instrumentation \
  --app "$APP" --test "$TEST" --device "$DEVICE" \
  --results-dir "$DIR-instr" --timeout 10m 2>&1 | tee "$OUT/instrumentation.txt"
rc_instr=${PIPESTATUS[0]}

gcloud firebase test android run --type robo \
  --app "$APP" --device "$DEVICE" \
  --results-dir "$DIR-robo" --timeout 5m 2>&1 | tee "$OUT/robo.txt"
rc_robo=${PIPESTATUS[0]}

BUCKET=$(grep -o 'storage/browser/[^/]*' "$OUT/instrumentation.txt" "$OUT/robo.txt" | head -1 | sed 's#.*storage/browser/##')
if [ -n "$BUCKET" ]; then
  gcloud storage cp -r "gs://$BUCKET/$DIR-instr" "gs://$BUCKET/$DIR-robo" "$OUT/" || echo "::warning::Raw result download failed"
fi

if [ "$rc_instr" -ne 0 ] || [ "$rc_robo" -ne 0 ]; then
  echo "::error::Test Lab: instrumentation=$rc_instr robo=$rc_robo"
  exit 1
fi
