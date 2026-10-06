# Changelog

User-facing changes to the Agent Farm Android app, newest first. Each build's release notes on the phone are read from its own heading here.

## 0.2.6 - The speaker buttons on Agent Farm's pages read aloud on the phone (2026-10-07)

- On a page opened on the phone (a conversation, the Console, Notices, Needs You, Now, the wiki), the speaker button now reads with the phone's own voice instead of making the computer speak in another room.
- The text is read the way Agent Farm reads it on the computer: code, links and lists said in words, in the text's own language when the phone has a voice for it, otherwise in your voice language.
- While it reads, the button shows a stop square, and a small player sits above the tab bar: previous paragraph, pause, next paragraph, stop and the speed.
- A phone call or another player pauses the reading; the play button goes on from that paragraph. Leaving the page ends it, and a question from an agent cuts in.
- Needs Agent Farm 3.876.0 or newer on the computer; with an older one the pages work as before.

## 0.2.5 - A shared text meets Agent Farm's remote check, and you can answer it (2026-10-05)

- A text shared into a session now passes the same check as a Telegram message: over today's remote budget, or carrying a command that cannot be undone (like rm -rf or git reset --hard), it is not sent at once, and the screen shows Agent Farm's own reason.
- Send anyway asks for your fingerprint or screen lock, then sends the same text with your yes. A message stopped by both checks is asked about twice, then goes.
- Picking another session or farm after the question starts over, so a yes is never spent on a different session.

## 0.2.4 - Scan the pairing QR code inside the app (2026-10-02)

- The pairing screen has a Scan QR code button: it opens a scanner inside the app, reads the code Agent Farm shows and pairs at once. Not every camera app opens a pairing link, so this is the one way that always works.
- The app asks for no camera permission: the scanner screen is Google's own.
- A phone without Google Play services says so and points to the other way: copy the pairing link in Agent Farm (Toolbox > Services > Agent Farm app) and paste it.

## 0.2.3 - A conversation opens on the phone (2026-10-01)

- Tapping a session in Agent Farm's pages (a Sessions row, a task's run, a chain's next leg) opens that conversation right on the phone instead of on the computer. Back returns to the page you came from.
- Needs Agent Farm 3.777.0 or newer on the computer; an older one answers "not available".

## 0.2.2 - Read aloud and dictation in your language (2026-10-01)

- Reading aloud and dictation are no longer fixed to Turkish: they follow the phone's language.
- Settings > Read aloud > Voice language picks another one; "Phone language" always says which language it is right now.
- If the phone has no voice for that language, Settings says so and how to add one.

## 0.2.1 - The app speaks Agent Farm's language: English (2026-10-01)

- Every word the app shows is now English, like Agent Farm itself: screens, settings, notifications and their buttons, the link status line, pairing messages, the widget and the quick settings tile.
- Reading aloud and dictation still use Turkish, the language you speak to your agents.
- When pairing cannot reach the farm, the message says where to look on the computer: Toolbox > Services > Agent Farm app.

## 0.2.0 - Agent Farm on the phone: pairing, pages, questions, dictation (2026-10-01)

- Pair with Agent Farm on your computer: scan the QR code shown in Agent Farm; the link is encrypted and locked to that computer. When the link drops it reconnects by itself, and what you sent meanwhile is held and delivered.
- Agent Farm's own pages open on the phone (the Now page, sessions, settings); a link on a page opens the next page, Back goes back.
- When an agent asks a question or a permission, a notification arrives and its lock-screen buttons answer it. A question answered on the computer leaves the phone by itself.
- Read aloud: questions, permission requests and (if you choose) a finished turn's summary are read aloud. Settings > Read aloud (off by default).
- Text boxes on the pages get a microphone: speak, and the phone writes it.
- Share text from any app into a running session.
- A home-screen widget and a quick settings tile: connected or not, what waits, what runs; a tap opens the Now page. The widget's microphone lets you speak and pick the session the words go to.
- TalkBack support and a clean layout at the largest font size.
- An optional app lock; locking deletes the data the pages keep on the phone. Settings has a battery optimization row.

## 0.1.0 - First test build (2026-09-30)

- The app's first test build: it shows "Hello, Agent Farm" and its version on launch. The real features (watching sessions, writing to agents, notifications) come in later builds.
- Follows the phone's dark theme.
- New builds install over the one on the phone as an update; there is no need to remove the old one.
