# Note reading eval · 2026-11-07-1203

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 180/252 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 25/28 | 248/252 | 0 | 1 | 2498 | 10586 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 20/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 28/28 | 27/28 | 27/28 | 26/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “pulled the driver OB, reloaded, made 7”
  - teeClub: expected Driver, got nothing
- *Par 4:* “5 iron right, second shot 6 iron short right of the green. Was 45 yards chipping over bunker but put it in and then chipped out to about 20 feet and 2 put.” (no score given: 5i, 6i, chip into bunker, out, 2 putts = 6)
  - putts: expected 2, got 3
- *Par 5:* “hybrid off the tee, found the fairway, then I don't remember” (incomplete: no score) **call failed**
  - log: HTTP 429; retrying in 10000 ms
  - log: read failed: Busy: HTTP 429
  - teeShot: expected FAIRWAY, got nothing
  - teeClub: expected Hybrid, got nothing
