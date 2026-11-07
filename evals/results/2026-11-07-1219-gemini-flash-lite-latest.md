# Note reading eval · 2026-11-07-1219

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 187/252 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 27/28 | 251/252 | 1 | 0 | 1137 | 33862 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 27/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 27/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “3 wood left into the trees, two putts” (shots between tee and green not described: don't count a score)
  - strokes: expected nothing, got 4 (invented)
