# and_AgentFarm — Conventions

Binding on every change in this repo. These are the house rules of `vsc_AgentFarm`
(`docs/CONVENTIONS.md` there, `_REF/agentfarm-dev.md` on the agent side) carried to a
phone: same names, same gates, same record. The human copies are in the vault project
`PRS.MuratVural_DEV_AND.AgentFarm`: `10_Notes/Tasarım İlkeleri.md` (design) and
`10_Notes/A-SOP - Android Otonom Geliştirme ve Test Çalışma Yöntemi - 260930.md`
(the dev/test loop). The two sides never contradict; change one, change the other.

---

## 0. Where things run

Nothing is built, installed or emulated on the user's PC. GitHub Actions builds and runs
the emulator; Firebase Test Lab runs real devices on `[testlab]`; App Distribution
delivers on `[deliver]`. Local work is editing plus the node repo gate. Details: the SOP.

## 1. Code architecture

| Layer | Path (`app/src/main/java/com/muvusoft/agentfarm/`) | Rule |
|---|---|---|
| Entry | `MainActivity.kt` | composition and wiring only, never logic |
| Core | `core/` | pure Kotlin: protocol models, state reducers, formatters, verdict functions. No `android.*`, `androidx.*` or other app layer (`test/test_core_purity.js`). Unit-tested on the JVM (`app/src/test`) |
| Network | `net/` | WebSocket client, discovery, reconnect/replay |
| UI | `ui/` | Compose screens; `ui/components/` holds each shared component once |

- **Size:** target under 300 lines, hard limit 500 (`test/test_file_size.js`). Extract before growing; never append to a file that is already long.
- **Callbacks are passed by name.** A parameter defaulting to a no-op (`{}`) can be left off and nothing notices (vsc X-267, the wiring gate). Use named arguments or an options object past two or three parameters.
- **One verdict, one function.** A screen asks `core/` for a verdict (connection state, who started a session, what "Default" resolves to) and prints it; it never re-derives it from pieces. Two derivations are two answers that will disagree.

## 2. Parity with Agent Farm

- **The phone shows Agent Farm's own pages**, served by `vsc_AgentFarm/src/remote/`. It never re-implements a screen; `ui/` holds only the shell's screens (pairing, connection, device settings).
- **The contract is Agent Farm's.** Its owner is `vsc_AgentFarm/src/remote/contract`; this repo carries the versioned copy in `contract/`, refreshed with `node tools/sync_contract.js` in the same work item as the change. `docs/PROTOKOL-VE-MIMARI.md` describes it; `test/test_contract.js` holds the copy, the owner and the document together.
- **Every phone capability that reads data or triggers an action is an existing bridge op**, called through a `call` frame. A missing one is added in `vsc_AgentFarm` under `_REF/agentfarm-dev.md` (the vsc parity gate) in the same work item. The phone never reaches around the bridge.
- **No new launch door.** Remotely opened pages are the desktop's own doors. A native "new session" (share, voice, widget) uses the Telegram `/new` launch verdict and inherits the last launch (`seedLastLaunch`); `sessions.start` records without inheriting, so it is not used.
- When `net/` lands, a test derives every frame kind and event type from `contract/` and fails on one with no Kotlin model (a rule is measured by reading the source, never by a list in the test).

## 3. Design — the five names, on a phone

The vsc names are kept so one word still carries a whole instruction. A phone has no hover,
so the instrument changes; the principles do not.

| Name | On the phone |
|---|---|
| **DENSITY** | Maximum information, minimum space, minimum taps. A fact the user can see must not cost a navigation. Ask of every element: "can they have this by long-press?" |
| **AFTip** | The shared detail sheet (`ui/components/`): long-press, or an ⓘ, opens a scrollable bottom sheet with the whole text. Anything that is TEXT or could grow into text goes there, never into a truncated label. It **yields to the hand**: any tap outside, Back, or typing closes it, and it never covers the control being pressed. |
| **SHORTHAND** | Short, unambiguous form on the row or chip; the sheet opens with the long form as its first line (`R3` → `Round 3 — …`). Never an abbreviation the user has to learn. |
| **MIRROR** | A chip or selector shows its CURRENT value; its sheet opens with that value, a blank line, then the description — and follows the value when it changes. |
| **ONE SUBJECT** | An object with several detail sheets gives each its own subject; no fact in two of them. |

The rest of the house rules, as they apply here:

- **Cockpit, not literature.** Buttons, tabs, chips: 1–3 words. Guidance goes in the sheet.
- **The shape states the arithmetic.** Switch = independent on/off; radio or badge+chip = exactly one; chip = an action. Draw what the code enforces.
- **A selected control stays selected:** fill + border + check mark + `selected` semantics. Never colour alone.
- **"Default" never stands alone.** `Default → sonnet`; the axis travels in the value (`effort: high` chosen here, `effort → low` resolved). An unknown value is shown as itself, never as the first option.
- **A text filter box has ✕ inside it**, Back clears or dismisses, and a live filter keeps focus and caret across recomposition.
- **Time:** every clock through one `core/` helper — short in a row, full with UTC and offset in the sheet. A missing timestamp prints nothing.
- **Copy:** one copy path; the ✓ appears only after the write happened.
- **The shell's words live in `res/values/strings.xml`** (Turkish, the default locale): a screen's text, a TalkBack label, a dialog, a notification channel name through `R.string`, never a Kotlin literal; `core/` verdict sentences stay in `core/` (pure, JVM-tested). `test/test_shell_strings.js` holds it, both ways (every name exists, every string is used).
- **TalkBack reads what the eye groups:** a label + detail + switch row is ONE `toggleable` node (`Role.Switch`), a screen title and section titles are `heading()`, a row with a long press names both gestures (`onClickLabel` / `onLongClickLabel`), an icon-only button has a label. The system font scale is honoured (no fixed text heights; the CI shoots `main-font200` at 200%).
- **A backlog code never reaches the user** (`test/test_no_dev_codes.js`). Codes live in comments, commit bodies and backlog records.
- **Making things:** one form per object and every route opens it; the refusal is shown in the form; saving a whole form can remove a field and never removes one it never showed.
- **A wait is an answer.** Connecting, reconnecting, a parked run, a question pending: one shape — kind · reason (the source's own sentence) · since · what clears it.
- **Destructive actions** (kill switch, critical tool calls) take a native confirmation (the "Kritik Komut Kalkanı" in `Tasarım İlkeleri.md`).

## 4. Looking at it

- **A visual change is not done until its screenshots were looked at** (emulator job artifacts; SOP section 4). String and unit tests pass on layouts nobody drew.
- **Shoot the scene, not the montage:** one capture per state, at its own size.
- **Draw the arity the user actually gets:** empty · one · many · too-many are different layouts; add the smallest first.
- **Light and dark, portrait and landscape**, with Turkish text and a real distribution of states in fixtures.
- **A scene that needs a press presses it** in the UI test; two scenes whose captures are identical are one scene with two labels.
- **When the question is a number, measure it** from the UI tree (bounds from `uiautomator dump` / Compose semantics): clipped text, a name squeezed to zero width, siblings overlapping.
- **Prove the capture:** `screen-state.txt` records the rotation and night mode actually in effect for each shot.
- Screenshots shared with the user go to `docs/Screenshots/` (local only) named `YYYYMMDD-HHMMSS_<build>-<env>-<screen>-<result>.png`.

## 5. Tests and gates

- **Local gate** — `.githooks/pre-commit` runs `node test/run-all.js` (encoding, tracked-file credentials, dev codes, file size, core purity, changelog/version, pipeline shape) and `tools/scan_staged.js` (staged credentials). Seconds, no Android build. Enable per clone: `git config core.hooksPath .githooks`. Never `--no-verify` without the user's say-so.
- **Cloud gate** — every push: the same repo gate, unit tests, emulator instrumentation, screenshots. `[testlab]` real devices at milestones; `[deliver]` to the phone only after the emulator passes.
- **Derive the rule from the source**, never from a list written in the test: a list is right the day it is written and wrong the day the next field is added.
- **A shape assertion is a shape:** compare positions, never a character budget; read files through `test/lib.js`, which normalises CRLF.
- **Prove a guard bites:** break the thing on purpose, see the test fail, restore from your own copy (never `git checkout --`, which restores HEAD and takes uncommitted work with it). Name the mutation in the commit body.
- **A red test nobody can explain is fixed or deleted in the turn it is found** — never carried as a known failure.
- **Fixtures** live in temp directories, never inside `test/`.

## 6. The record — commit, changelog, version

- **Commit title:** `v#.# - <version>: <lowercase summary>` for a release (`[deliver]`), otherwise `v#.# - <lowercase summary> (not released)`. `#.#` is the repo's global commit counter: the last `v#.#` in `git log` + 1; unformatted commits also consume a number. The counter starts at `v1.13`.
- **Commit body:** what changed, file by file; `Verification:` with the local gate figures (`N files / M passed / 0 failed`) and the CI run that proves the rest; `Limit:` for anything not covered; the attribution line. Work is not "done" until that CI run is green and its screenshots were looked at.
- **One job, one commit**, and only your own paths: parallel sessions share the working tree, so `git status` before staging.
- **Version:** `appVersion` in `app/build.gradle.kts` is the release number; `versionName` = `<appVersion>-b<CI run>`, `versionCode` = the CI run. A release bumps `appVersion` and adds `## <version> - <title> (<YYYY-MM-DD>)` to `CHANGELOG.md` — user-facing, in Turkish — and the phone's release notes are read from that entry (`scripts/ci/deliver.sh`). `test/test_changelog.js` holds the two together.
- **Docs travel with the change:** protocol → `docs/PROTOKOL-VE-MIMARI.md`; scenarios and results → `docs/test-plan.md`; conventions → this file and its human copy.

## 7. Encoding and the shell

The constitution's rules apply (`_REF/shell.md`: a payload goes to a file, never inline; never
rewrite a repo file through Windows PowerShell `Get-Content`/`Set-Content`).
`test/test_encoding.js` (ported from vsc) fails on mojibake, stray control characters and a
leading BOM in `.kt .kts .xml .md .yml .sh .js .json .properties`; repair with `--fix`.
