#!/usr/bin/env bash
# The Firebase API key the app's push wake needs (net/PushWakeService), read from the project's Android
# app config with the CI service account and handed to Gradle through GITHUB_ENV. The repo is public, so
# the key is never committed. A build that cannot read it still builds: that build simply has no push wake.
set -uo pipefail

TOKEN=$(gcloud auth print-access-token 2>/dev/null) || { echo "::warning::No Google Cloud token: this build has no push wake"; exit 0; }
CFG=$(curl -sf -H "Authorization: Bearer $TOKEN" \
  "https://firebase.googleapis.com/v1beta1/projects/-/androidApps/$FIREBASE_APP_ID/config") || {
  echo "::warning::The Firebase app config could not be read (the service account needs a Firebase viewer role): this build has no push wake"
  exit 0
}
KEY=$(printf '%s' "$CFG" | python3 -c '
import base64, json, os, sys
cfg = json.loads(base64.b64decode(json.load(sys.stdin)["configFileContents"]))
app = os.environ["FIREBASE_APP_ID"]
for c in cfg.get("client", []):
    if c.get("client_info", {}).get("mobilesdk_app_id") == app:
        print(c["api_key"][0]["current_key"])
        break
') || { echo "::warning::The Firebase app config has no key for $FIREBASE_APP_ID: this build has no push wake"; exit 0; }
if [ -z "$KEY" ]; then echo "::warning::No Firebase key for $FIREBASE_APP_ID: this build has no push wake"; exit 0; fi
echo "::add-mask::$KEY"
echo "FIREBASE_API_KEY=$KEY" >> "$GITHUB_ENV"
echo "Push wake: the Firebase key is in this build."
