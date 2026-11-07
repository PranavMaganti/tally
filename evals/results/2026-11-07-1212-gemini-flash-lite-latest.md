# Note reading eval · 2026-11-07-1212

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 186/252 | 1 | 0 | – | – |
| gemini-flash-lite-latest | 26/28 | 250/252 | 1 | 0 | 2535 | 5815 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 26/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 27/28 | 28/28 | 28/28 | 27/28 | 28/28 | 28/28 | 28/28 | 28/28 | 28/28 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there)
  - teeClub: expected nothing, got 9i (invented)
- *Par 4:* “5 iron right, second shot 6 iron short right of the green. Was 45 yards chipping over bunker but put it in and then chipped out to about 20 feet and 2 put.” (no score given: 5i, 6i, chip into bunker, out, 2 putts = 6)
  - strokes: expected 6, got 5
