# Note reading eval · 2026-11-07-1216

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 187/252 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 25/28 | 249/252 | 1 | 0 | 1061 | 4304 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 27/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 27/28 | 27/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 | 27/28 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “par, lagged it to 3 feet from 40 and tapped in” (putt story that adds up to 2)
  - leaveFeet: expected 3, got nothing
- *Par 4:* “5 iron right, second shot 6 iron short right of the green. Was 45 yards chipping over bunker but put it in and then chipped out to about 20 feet and 2 put.” (no score given: 5i, 6i, chip into bunker, out, 2 putts = 6)
  - putts: expected 2, got 3
- *Par 4:* “3 wood left into the trees, two putts” (shots between tee and green not described: don't count a score)
  - strokes: expected nothing, got 4 (invented)
