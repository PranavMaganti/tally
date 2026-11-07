# Tally

A golf scoring app: GPS yardages on OpenStreetMap hole maps, and hole notes (typed or spoken) that AI
reads into your scorecard. Built with Kotlin Multiplatform and Compose Multiplatform; Android first,
iOS compiling.

<table>
  <tr>
    <td><img src="docs/screenshots/hole.png" alt="Hole map with GPS yardages to the front, middle and back of the green, a target line and hazard distances" width="260"></td>
    <td><img src="docs/screenshots/note.png" alt="Scoring a hole from a typed note, with the score and tee club underlined and filled in from the note" width="260"></td>
    <td><img src="docs/screenshots/card.png" alt="Scorecard for the round in progress" width="260"></td>
  </tr>
  <tr>
    <td align="center">GPS yardages and hazards on every hole</td>
    <td align="center">Say or type how it went; the card fills itself in</td>
    <td align="center">The card as you go</td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/home.png" alt="Home screen with the scoring average trend and recent rounds" width="260"></td>
    <td><img src="docs/screenshots/stats.png" alt="Stats: scoring average, biggest pattern and the mix of scores" width="260"></td>
    <td><img src="docs/screenshots/stats-detail.png" alt="Stats: tee shots by club, greens, scrambling and first putts by length" width="260"></td>
  </tr>
  <tr>
    <td align="center">Your rounds at a glance</td>
    <td align="center">The pattern that costs you most</td>
    <td align="center">Tee shots by club and putting by length</td>
  </tr>
</table>

<sub>Screenshots use made-up demo rounds.</sub>

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
