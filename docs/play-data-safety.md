# Play Console — Data safety answers

The answers for the Data safety form, derived from what the app does (`PRIVACY.md` is the public text;
both change together). Re-check them whenever a dependency or a data flow changes.

**Privacy policy URL:** https://github.com/miropharm/and_AgentFarm/blob/master/PRIVACY.md

## Overview

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share any of the required user data types? | Yes | The push token goes to Google Firebase; what the user sends goes to the user's own farm |
| Is all of the user data collected by the app encrypted in transit? | Yes | TLS to the farm, pinned to its certificate; Firebase over TLS |
| Do you provide a way for users to request that their data is deleted? | Yes | Forget the farm in the app or uninstall; nothing is kept by the developer |

## Data types

| Data type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Device or other IDs | Yes | No | No | App functionality | The Firebase Cloud Messaging token and installation id, processed by Google as a service provider for push wake |
| Messages — other in-app messages | Yes | No | Yes | App functionality | Answers, prompts and shared text the user sends, delivered only to the user's own paired farm |
| Photos | Yes | No | Yes | App functionality | Only a picture the user shares to Agent Farm, delivered only to the user's own farm |
| Files and docs | Yes | No | Yes | App functionality | Only a file the user picks to attach or import, delivered only to the user's own farm; an export is saved where the user chooses on the phone |

Not collected: location, contacts, calendar, health, financial info, web browsing, app activity analytics,
crash logs, diagnostics, audio (dictation runs in the phone's own speech recognition app). No data is used for
advertising or sold.

## Points to state in the listing

- The app works only with a farm the user runs on their own computer (Agent Farm for VS Code).
- No account, no developer server, no advertising, no analytics, no crash reporting.
