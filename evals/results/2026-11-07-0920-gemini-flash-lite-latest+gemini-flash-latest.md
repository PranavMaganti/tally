# Note reading eval · 2026-11-07-0920

20 notes, 8 fields each.

| Pipeline | Notes fully right | Fields right | Invented fields | Failed calls | Median ms | Slowest ms |
|---|---|---|---|---|---|---|
| rules only (offline) | 1/20 | 116/160 | 0 | 0 | – | – |
| gemini-flash-lite-latest | 14/20 | .| 0 | 3 | 1742 | 2825 |
| gemini-flash-latest | 1/20 | 117/160 | 0 | 19 | 10005 | 10035 |

## Accuracy by field

| Pipeline | strokes | putts | teeShot | penalties | bunkerShots | firstPuttFeet | puttMiss | leaveFeet |
|---|---|---|---|---|---|---|---|---|
| rules only (offline) | 17/20 | 7/20 | 7/20 | 16/20 | 17/20 | 15/20 | 19/20 | 18/20 |
| gemini-flash-lite-latest | 19/20 | 15/20 | 17/20 | 19/20 | 20/20 | 19/20 | 20/20 | 19/20 |
| gemini-flash-latest | 17/20 | 7/20 | 8/20 | 16/20 | 17/20 | 15/20 | 19/20 | 18/20 |

## Misses · gemini-flash-lite-latest

- *Par 4:* “pulled the driver OB, reloaded, made 7”
  - penalties: expected 1, got 2
- *Par 3:* “birdie! 9 iron to 4 feet”
  - putts: expected 1, got nothing
- *Par 5:* “duffed my chip, then chipped in”
  - putts: expected 0, got nothing
- *Par 4:* “Six hit my drive right into the trees had to chip out then two putts” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected RIGHT, got nothing
- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there) **call failed**
  - putts: expected 3, got nothing
  - teeShot: expected RIGHT, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing
- *Par 4:* “Need a six hit my T-shirt a bit left but just in the left rough. May the 6th my T-shirt bit left I then made a three-pot and it.” (raw dictation before speech fixes) **call failed**
  - strokes: expected 6, got 3
  - putts: expected 3, got nothing
  - teeShot: expected LEFT, got nothing

## Misses · gemini-flash-latest

- *Par 4:* “8, lost a ball right off the tee took a penalty drop 2 shots, hit a nice iron in to 10 feet but missed the putt low” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected RIGHT, got nothing
  - penalties: expected 2, got nothing
  - firstPuttFeet: expected 10, got nothing
  - puttMiss: expected LOW, got nothing
- *Par 4:* “par, fairway, 2 putts” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected FAIRWAY, got nothing
- *Par 3:* “7 iron to the back of the green, three putted for a 4” **call failed**
  - putts: expected 3, got nothing
- *Par 4:* “bogey, sliced it into the trees, punched out” **call failed**
  - teeShot: expected RIGHT, got nothing
- *Par 5:* “6. drive left in the rough, two to get out of the fairway bunker, two putts” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected LEFT, got nothing
  - bunkerShots: expected 2, got nothing
- *Par 4:* “double. went in the water on the right with my drive” **call failed**
  - teeShot: expected RIGHT, got nothing
  - penalties: expected 1, got nothing
- *Par 4:* “pulled the driver OB, reloaded, made 7” **call failed**
  - teeShot: expected LEFT, got nothing
  - penalties: expected 1, got nothing
- *Par 3:* “birdie! 9 iron to 4 feet” **call failed**
  - putts: expected 1, got nothing
  - firstPuttFeet: expected 4, got nothing
- *Par 4:* “drive down the middle, wedge to 15 feet, two putts for par” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected FAIRWAY, got nothing
  - firstPuttFeet: expected 15, got nothing
- *Par 5:* “duffed my chip, then chipped in” **call failed**
  - putts: expected 0, got nothing
- *Par 4:* “5” **call failed**
- *Par 4:* “hit it left” **call failed**
  - teeShot: expected LEFT, got nothing
- *Par 4:* “greenside bunker, took 3 to get out, 3 putt, 9” **call failed**
  - strokes: expected 9, got nothing
  - putts: expected 3, got nothing
  - bunkerShots: expected 3, got nothing
- *Par 3:* “plugged in the bunker, splashed out and one putt for par” **call failed**
  - putts: expected 1, got nothing
  - bunkerShots: expected 1, got nothing
- *Par 4:* “triple, two balls in the lake” **call failed**
  - penalties: expected 2, got nothing
- *Par 4:* “sorry, it was a 6 not a 5. drive right” **call failed**
  - strokes: expected 6, got nothing
  - teeShot: expected RIGHT, got nothing
- *Par 4:* “6, hit first shot left into tee, chipped out, got a really good 9 iron onto the green and then 3 putt from 20 feet. First putt was a bad one, it ended up like 8 feet away” **call failed**
  - putts: expected 3, got nothing
  - teeShot: expected LEFT, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing
- *Par 4:* “Six hit my drive right into the trees had to chip out then two putts” **call failed**
  - putts: expected 2, got nothing
  - teeShot: expected RIGHT, got nothing
- *Par 4:* “Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there” (raw dictation; 'too far from there' = two from there) **call failed**
  - putts: expected 3, got nothing
  - teeShot: expected RIGHT, got nothing
  - firstPuttFeet: expected 20, got nothing
  - leaveFeet: expected 8, got nothing
- *Par 4:* “Need a six hit my T-shirt a bit left but just in the left rough. May the 6th my T-shirt bit left I then made a three-pot and it.” (raw dictation before speech fixes)
  - strokes: expected 6, got 3
  - putts: expected 3, got nothing
