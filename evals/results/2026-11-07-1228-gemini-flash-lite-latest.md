# Note reading eval · 2026-11-07-1228

28 notes, 9 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 3/28 | 187/252 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 17/28 | 220/252 | 0 | 11 | 821 | 31404 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | teeClub | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 22/28 | 9/28 | 12/28 | 27/28 | 24/28 | 24/28 | 18/28 | 27/28 | 24/28 |
| gemini-flash-lite-latest | 24/28 | 18/28 | 20/28 | 28/28 | 28/28 | 27/28 | 22/28 | 28/28 | 25/28 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “Six hit my drive right into the trees had to chip out then two putts” **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 2, got nothing
  - teeShot: expected RIGHT, got nothing
- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 3, got nothing
  - teeShot: expected RIGHT, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing
- *Par 4:* “Need a six hit my T-shirt a bit left but just in the left rough. May the 6th my T-shirt bit left I then made a three-pot and it.” (raw dictation before speech fixes) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - strokes: expected 6, got nothing
  - putts: expected 3, got nothing
  - teeShot: expected LEFT, got nothing
- *Par 4:* “Got a 6 drove it right into the trees chipped out then putted it to 8 ft from 20 ft and then two from there” (first putt from 20 ft to 8 ft, then two more) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 3, got nothing
  - teeShot: expected RIGHT, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing
- *Par 5:* “bogey, two putts from 30 feet” (control: plain two-putt, must not become 3) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 2, got nothing
  - firstPuttFeet: expected 30, got nothing
- *Par 4:* “par, lagged it to 3 feet from 40 and tapped in” (putt story that adds up to 2) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 2, got nothing
  - firstPuttFeet: expected 40, got nothing
  - leaveFeet: expected 3, got nothing
- *Par 4:* “5 iron right, second shot 6 iron short right of the green. Was 45 yards chipping over bunker but put it in and then chipped out to about 20 feet and 2 put.” (no score given: 5i, 6i, chip into bunker, out, 2 putts = 6) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - strokes: expected 6, got nothing
  - putts: expected 2, got nothing
  - teeShot: expected RIGHT, got nothing
  - bunkerShots: expected 1, got nothing
  - firstPuttFeet: expected 20, got nothing
- *Par 4:* “driver down the middle, 8 iron onto the green, two putts” (every shot listed: 4) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - strokes: expected 4, got nothing
  - putts: expected 2, got nothing
  - teeShot: expected FAIRWAY, got nothing
- *Par 3:* “7 iron to 15 feet, holed it” (every shot listed: 2) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - strokes: expected 2, got nothing
  - putts: expected 1, got nothing
  - firstPuttFeet: expected 15, got nothing
- *Par 4:* “3 wood left into the trees, two putts” (shots between tee and green not described: don't count a score) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - putts: expected 2, got nothing
  - teeShot: expected LEFT, got nothing
- *Par 5:* “hybrid off the tee, found the fairway, then I don't remember” (incomplete: no score) **call failed**
  - log: HTTP 429; retrying in 30000 ms
  - log: read failed: Busy: HTTP 429
  - teeShot: expected FAIRWAY, got nothing
