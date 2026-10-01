#!/usr/bin/env bash
# Uploads the debug APK to Firebase App Distribution and sends it to the tester group.
# The group is resolved by display name or alias, because the console's display name and the API alias can differ.
set -euo pipefail

WANT="${DELIVER_GROUP:-dev}"
PROJECT_NUMBER="${FIREBASE_APP_ID#1:}"
PROJECT_NUMBER="${PROJECT_NUMBER%%:*}"
APK=$(find apks -name app-debug.apk | head -1)

gcloud services enable firebaseappdistribution.googleapis.com --project "$FIREBASE_PROJECT" --quiet

TOKEN=$(gcloud auth print-access-token)
GROUPS_JSON=$(curl -sS -H "Authorization: Bearer $TOKEN" -H "x-goog-user-project: $FIREBASE_PROJECT" \
  "https://firebaseappdistribution.googleapis.com/v1/projects/$PROJECT_NUMBER/groups")

echo "Tester groups in project:"
echo "$GROUPS_JSON" | jq -r '.groups[]? | "  alias=\(.name | split("/") | last)  name=\(.displayName)  testers=\(.testerCount // 0)"'
echo "$GROUPS_JSON" | jq -e '.error' > /dev/null && { echo "::error::Group listing failed"; echo "$GROUPS_JSON" | jq '.error'; exit 1; }

ALIAS=$(echo "$GROUPS_JSON" | jq -r --arg w "$WANT" '
  [.groups[]? | select((.displayName | ascii_downcase) == ($w | ascii_downcase) or (.name | split("/") | last) == $w)]
  | if length > 0 then (.[0].name | split("/") | last) else "" end')

if [ -z "$ALIAS" ] && [ "$(echo "$GROUPS_JSON" | jq '.groups | length')" = "1" ]; then
  ALIAS=$(echo "$GROUPS_JSON" | jq -r '.groups[0].name | split("/") | last')
  echo "::warning::No group named '$WANT'; using the project's only group '$ALIAS'"
fi
if [ -z "$ALIAS" ]; then
  echo "::error::No App Distribution group named '$WANT' in project $FIREBASE_PROJECT"
  exit 1
fi
echo "Delivering to group alias: $ALIAS"

# Release notes: the newest CHANGELOG.md entry (the user-facing text), then which build and commit this is.
NOTES=$(awk '/^## /{n++; if (n == 2) exit} n == 1' CHANGELOG.md)
SUBJECT=$(printf '%s\n' "${COMMIT_MESSAGE:-}" | head -1)
RELEASE_NOTES=$(printf '%s\n\nBuild b%s - %s\n' "$NOTES" "${GITHUB_RUN_NUMBER:-?}" "$SUBJECT")

npx --yes firebase-tools@latest appdistribution:distribute "$APK" \
  --app "$FIREBASE_APP_ID" --groups "$ALIAS" --release-notes "$RELEASE_NOTES"
