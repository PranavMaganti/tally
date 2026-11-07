# Note reading eval · 2026-11-07-1007

20 notes, 8 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/20 | 119/160 | 0 | 0 | – | – |
| gemini nano (on device) | 16/20 | 154/160 | 0 | 0 | 1010 | 1558 |
| gemini-flash-lite-latest | 20/20 | 160/160 | 0 | 0 | 1424 | 2390 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 17/20 | 8/20 | 9/20 | 16/20 | 17/20 | 15/20 | 19/20 | 18/20 |
| gemini nano (on device) | 20/20 | 17/20 | 19/20 | 20/20 | 20/20 | 19/20 | 20/20 | 19/20 |
| gemini-flash-lite-latest | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 |

## Misses · gemini nano (on device)

- *Par 4:* “8, lost a ball right off the tee took a penalty drop 2 shots, hit a nice iron in to 10 feet but missed the putt low”
  - putts: expected 2, got nothing
- *Par 3:* “birdie! 9 iron to 4 feet”
  - putts: expected 1, got nothing
- *Par 4:* “hit it left”
  - teeShot: expected LEFT, got nothing
- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there)
  - putts: expected 3, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing

## Misses · gemini-flash-lite-latest

