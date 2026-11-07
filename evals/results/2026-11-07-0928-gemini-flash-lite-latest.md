# Note reading eval · 2026-11-07-0928

20 notes, 8 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 1/20 | 116/160 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 16/20 | 155/160 | 0 | 1 | 1917 | 9402 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 17/20 | 7/20 | 7/20 | 16/20 | 17/20 | 15/20 | 19/20 | 18/20 |
| gemini-flash-lite-latest | 20/20 | 17/20 | 20/20 | 20/20 | 18/20 | 20/20 | 20/20 | 20/20 |

## Misses · gemini-flash-lite-latest

- *Par 5:* “6. drive left in the rough, two to get out of the fairway bunker, two putts”
  - bunkerShots: expected 2, got 1
- *Par 5:* “duffed my chip, then chipped in”
  - putts: expected 0, got nothing
- *Par 3:* “plugged in the bunker, splashed out and one putt for par” **call failed**
  - log: HTTP 503: {
  "error": {
    "code": 503,
    "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
    "status": "UNAVAILABLE
  - putts: expected 1, got nothing
  - bunkerShots: expected 1, got nothing
- *Par 4:* “Need a six hit my T-shirt a bit left but just in the left rough. May the 6th my T-shirt bit left I then made a three-pot and it.” (raw dictation before speech fixes)
  - putts: expected 3, got nothing
