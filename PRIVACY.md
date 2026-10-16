# Agent Farm for Android — Privacy Policy

Effective 2026-10-16. Applies to the Agent Farm app for Android (`com.muvusoft.agentfarm`), published by
Muvusoft.

Agent Farm for Android is the companion of Agent Farm, a Visual Studio Code extension that runs on your own
computer. The app shows and controls **your own farm** — the computer you paired it with. It has no account,
and **we run no server that receives your data**: nothing the app handles is sent to us.

## What the app keeps on your phone

| What | Why | Where, and for how long |
|---|---|---|
| Each paired farm: its name, id, the addresses it can be reached at, the fingerprint of its certificate, your device's id on it and the access level it granted | To reconnect to your farm and to trust only its certificate | The app's private storage, until you forget the farm or uninstall the app |
| A signing key for each pairing | To prove to your farm that it is this phone | The Android Keystore; the private key never leaves it. Deleted when the farm is forgotten |
| Your settings in the app (app lock, read-aloud, voice language) | To work the way you chose | The app's private storage |
| Messages you sent while the farm was unreachable (an answer, a shared text) | To deliver them when the farm is back | The app's private storage, until delivered, and never longer than 6 hours |
| The network card your farm's PC named (its MAC and broadcast address) | To wake that PC from the same network when you ask | The app's private storage, until you forget the farm |
| A push token from Firebase Cloud Messaging | So your farm can wake the app when something needs you | The app's private storage |

The pages of your farm are shown live and are **not stored**: the app's web view keeps no cache, and its page
storage is erased when the app locks. The app keeps **no conversation history** of its own. App data is excluded
from Android backups.

## Where data goes

- **To your own farm only.** Everything you see and do in the app travels between your phone and the computer
  you paired, over an encrypted connection that trusts only that computer's certificate. What happens on your
  farm (the AI agents it runs, the services they use) is governed by your farm's own setup.
- **Google Firebase Cloud Messaging (push wake).** When your farm has something that needs you and the app is not
  connected, it can ask Google to wake the app. That message carries **no content** — only that your farm has
  something new, and which farm. The app then connects to your farm directly. Google receives the push token and
  the Firebase installation identifier needed to deliver it ([Google's privacy policy](https://policies.google.com/privacy)).
- **Pairing QR code.** The code is read by Google Play services' code scanner, on your phone.
- **Dictation and reading aloud.** Dictation uses the speech recognition app of your phone (for example
  Google's), under that app's own terms. Reading aloud uses the text-to-speech engine of your phone.
- **Sharing into the app.** Text or a picture you share to Agent Farm goes to your farm only.

The app contains **no advertising, no analytics and no crash reporting**.

## Your choices

- Forget a farm in the app to delete everything the app keeps about it.
- Uninstall the app to delete all of its data, including its keys.
- Revoke the phone on your farm (Agent Farm on your computer) to end its access from there.
- Turn off the app's notifications in Android's settings; push wake then only reconnects silently.

## Children

The app is a developer tool and is not directed to children.

## Changes

A change to this policy is published in this file with a new effective date. The app's
[source code](https://github.com/miropharm/and_AgentFarm) is public.

## Contact

Questions about this policy: open an issue at <https://github.com/miropharm/and_AgentFarm/issues>.
