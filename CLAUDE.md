# Trivia Night

A family trivia game for the onn 4K Google TV sticks (the same ones SimpleTV runs on). Solo
play uses the remote; group play puts a QR code on the TV, and everyone answers on their
own phone in the browser, with nothing to install (the family's phones are iPhones). It's a
personal app for home, never sold.

## Build and run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
adb -s <stick-ip>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

The sticks are Android TV 14, 320 dpi (1 dp = 2 px), UI at 1920×1080. Plain Compose
foundation only (no tv-material), like SimpleTV. Text sizes are the design's px halved.

## Questions

`app/src/main/assets/questions.json` is built, never edited by hand:

```bash
uv run --no-project python -I scripts/fetch_opentdb.py    # about 10 minutes; rate-limited
uv run --no-project python -I scripts/build_questions.py
```

- `data/opentdb.json` — every verified question from [Open Trivia DB](https://opentdb.com)
  (CC BY-SA 4.0). Its easy, medium and hard become levels 3, 4 and 5.
- `data/written/*.json` — questions written for this app: grade school and middle school
  (levels 1–2) across school subjects (`school-*`) and Open Trivia DB's categories that
  otherwise start at level 3 (`easy-*`), extra categories at all five levels (Food & Drink,
  US Presidents, US States, Inventions, Holidays, 80s & 90s Pop Culture, Dinosaurs, Words &
  Phrases, Old Testament, New Testament) and fandom categories at all five levels (Star Wars, Lord of the Rings, Harry
  Potter, Star Trek: TNG, Marvel, Disney & Pixar). Format:
  `{"category", "group", "questions": [{"level", "q", "a", "wrong": [3 answers]}]}`.
  A file's category name joins the same-named category from other sources.

`build_questions.py` maps Open Trivia DB's categories to the app's names and groups
(`OPENTDB`), drops repeated questions, and writes compact rows:
`[category index, level, question, answer, wrong…]`.

Levels: 1 Grade school, 2 Middle school, 3 High school, 4 College, 5 Expert (`Levels` in
`Questions.kt`). The setup screen only offers categories that have questions at the
chosen level.

## Code map

- `MainActivity.kt` — loads the bank and the records, then Home → (solo: Who's playing?) →
  Setup → a solo or group game, or Home → High scores. Syncs on every resume.
- `Questions.kt` — the bank, levels and groups; `pickQuestions` prefers questions not asked
  on any of the household's TVs before (by text hash), then the ones asked longest ago.
- `Records.kt` — saved players (first name and color; a removed one stays as a "gone" note),
  every finished game, and when each question was last asked, in `files/records.json`.
  Boards: best games rank each player's best by points a question (so 8 of 10 beats 12 of
  30), all-time points add up every game. `mergeSaved` joins two copies.
- `GameBridge.kt` — the Game Bridge: shares the records with the household's other TVs, and
  the `players` collection with the household's other Red Mesa Studios games (such as
  Imposter), through SimpleTV, which is always running. It POSTs each collection to
  SimpleTV's sync server on 127.0.0.1:8724 and keeps the merged copy that comes back. Needs
  SimpleTV's sync key (`~/.config/simpletv/sync_key`, baked into `BuildConfig.SYNC_KEY`;
  `-Phousehold=<name>` builds for another household with `~/.config/simpletv/<name>/sync_key`);
  without it or without SimpleTV, records stay on the one TV and the game works the same.
- `PlayerScreens.kt` — Who's playing? (saved players and New player) and the player editor
  (name with the TV keyboard, eight colors, remove).
- `HighScoresScreen.kt` — the two boards; OK on a player opens the editor.
- `Game.kt` — settings, rounds (shuffled answers; True/False keeps True first) and scoring:
  500 for a right answer plus up to 500 more for speed, 20 seconds per question.
- `HomeScreen.kt`, `SetupScreen.kt` — mode cards; level, categories, question count.
- `SoloGame.kt` — remote play: D-pad over the answers, OK picks, a 4-second reveal (OK skips).
- `GroupGame.kt` — lobby (QR, players), question (who has answered), reveal (picks per
  answer, points this round; 7 seconds, OK skips), final scores, each player's game saved.
  Back during a game returns to the lobby; Back in the lobby leaves.
- `GroupRoom.kt` — the group game's state, changed under one lock from the server's threads
  (join, answer) and the TV (start, reveal, next), published as an immutable `RoomState`.
  `phoneView` is what one phone shows.
- `GroupServer.kt` — a plain-socket web server on port 8725 while a group game is open. Every
  API call carries the four-digit code from the QR link. `GET /` is the phone page,
  `GET /api/state`, `GET /api/players`, `POST /api/join`, `POST /api/answer`. A phone joins
  as a saved player or adds a new name; one saved player sits in one seat.
- `PhonePage.kt` — the phone page: tap your saved name or add a new one with a color, then
  one tap per answer. It
  polls about twice a second and keeps its player id in localStorage, so a reload rejoins.
- `QuestionViews.kt`, `Widgets.kt`, `ui/Theme.kt` — shared pieces and the palette.

## Design

The design canvas is https://claude.ai/artifact/6ZA9DuUkm6LGD6nFtpgptp: night-sky ground,
gold for focus and what's chosen, and one color plus one shape per answer (coral triangle,
sky diamond, gold circle, mint square) so answers can be told apart without color. Type is
Bricolage Grotesque for headings and Figtree for text, both bundled (OFL licences in the
repo root).
