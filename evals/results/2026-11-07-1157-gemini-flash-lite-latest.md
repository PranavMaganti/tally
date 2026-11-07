# Note reading eval · 2026-11-07-1157

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 183/252 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 24/28 | 247/252 | 3 | 0 | 709 | 2453 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 23/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 27/28 | 27/28 | 28/28 | 25/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 |

## Misses · gemini-flash-lite-latest

- *Par 3:* “7 iron to the back of the green, three putted for a 4”
  - teeClub: expected nothing, got 7i (invented)
- *Par 4:* “pulled the driver OB, reloaded, made 7”
  - teeClub: expected nothing, got Driver (invented)
- *Par 3:* “birdie! 9 iron to 4 feet”
  - teeClub: expected nothing, got 9i (invented)
- *Par 4:* “5 iron right, second shot 6 iron short right of the green. Was 45 yards chipping over bunker but put it in and then chipped out to about 20 feet and 2 put.” (no score given: 5i, 6i, chip into bunker, out, 2 putts = 6)
  - strokes: expected 6, got nothing
  - putts: expected 2, got nothing
