# Trivia Night

A family trivia game for Android TV. Play solo with the remote, or play together: the TV
shows a QR code, everyone scans it and answers on their own phone's browser, with nothing
to install.

- **Five levels**, from grade school to expert.
- **Dozens of categories**, from science and geography to Star Wars, Star Trek: The Next
  Generation and The Lord of the Rings. Pick any mix, or play everything.
- **Faster right answers score more.** Questions you've already seen wait until the rest
  have had their turn.
- **Players and high scores.** Pick your name (or add it) and your color; the TV keeps each
  player's best game and all-time points. With [SimpleTV](https://github.com/dbarkman/SimpleTV)
  on each TV and the same sync key (the Game Bridge), every TV in the house shares players
  and scores, and other Red Mesa Studios games share the same players.

It runs on the Walmart onn 4K Streaming Device (2026) and other Android TV 14+ devices.

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The question bank is built from the files in `data/` (see `CLAUDE.md`).

## Credits

- Many questions come from [Open Trivia DB](https://opentdb.com), licensed under
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
- Bricolage Grotesque and Figtree are under the SIL Open Font License; see the
  `FONT-LICENSE-*` files.
