# Note reading eval · 2026-11-07-1014

23 notes, 8 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/23 | 134/184 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 22/23 | 181/184 | 0 | 0 | 1706 | 8441 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 20/23 | 8/23 | 11/23 | 19/23 | 20/23 | 15/23 | 22/23 | 19/23 |
| gemini-flash-lite-latest | 23/23 | 22/23 | 23/23 | 23/23 | 23/23 | 22/23 | 23/23 | 22/23 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “Got a 6 drove it right into the trees chipped out then hit it to 8 ft from 20 ft and then two from there” (first putt from 20 ft to 8 ft, then two more)
  - putts: expected 3, got 2
  - firstPuttFeet: expected 20, got 8
  - leaveFeet: expected 8, got nothing
