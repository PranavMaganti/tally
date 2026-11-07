# Tally

A golf scoring app: GPS yardages on OpenStreetMap hole maps, and hole notes (typed or spoken) that AI
reads into your scorecard. Built with Kotlin Multiplatform and Compose Multiplatform; Android first,
iOS compiling.

## Building

- Android: `./gradlew :androidApp:assembleRelease`
- iOS: open `iosApp` in Xcode.
- Tests: `./gradlew :shared:testAndroidHostTest`

Online note reading uses the Gemini API. Put your own key in `local.properties` (never committed):

```
GEMINI_API_KEY=...
```

Without a key, notes are read on the phone with Gemini Nano where available.

## Note-reading evals

`evals/run-note-evals.sh` scores the note reader against `evals/note-evals.jsonl`. It calls the
Gemini API, so it uses your quota.

## Data and licences

Course data is © OpenStreetMap contributors (ODbL). Fonts are under the SIL Open Font License. See
[NOTICE.md](NOTICE.md).
