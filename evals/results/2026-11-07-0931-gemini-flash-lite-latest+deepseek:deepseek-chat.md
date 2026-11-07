# Note reading eval · 2026-11-07-0931

20 notes, 8 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 2/20 | 117/160 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 20/20 | 160/160 | 0 | 0 | 1466 | 2321 |
| deepseek:deepseek-chat | 18/20 | 158/160 | 0 | 0 | 1006 | 1434 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 17/20 | 8/20 | 7/20 | 16/20 | 17/20 | 15/20 | 19/20 | 18/20 |
| gemini-flash-lite-latest | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 |
| deepseek:deepseek-chat | 20/20 | 19/20 | 19/20 | 20/20 | 20/20 | 20/20 | 20/20 | 20/20 |

## Misses · gemini-flash-lite-latest


## Misses · deepseek:deepseek-chat

- *Par 4:* “hit it left”
  - teeShot: expected LEFT, got nothing
- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there)
  - putts: expected 3, got nothing
