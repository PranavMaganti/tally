package me.vanpra.tally.ai

import me.vanpra.tally.round.FieldSource
import me.vanpra.tally.round.HoleEntryForm
import me.vanpra.tally.round.HoleScore
import me.vanpra.tally.round.Origin
import me.vanpra.tally.round.TeeShot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteReadingTest {
    private val par4 = HoleContext(1, 4)
    private val par3 = HoleContext(2, 3)

    @Test fun parsesAModelReply() {
        val r = NotePrompt.parse("""Here you go: {"score":8,"result":null,"putts":2,"tee":"right","penalties":2,"bunker":null}""", par4)
        assertEquals(NoteReading(8, 2, TeeShot.RIGHT, 2, null), r)
    }

    @Test fun namedScoresBecomeNumbers() {
        assertEquals(5, NotePrompt.parse("""{"score":null,"result":"bogey"}""", par4)?.strokes)
        assertEquals(6, NotePrompt.parse("""{"result":"double bogey"}""", par4)?.strokes)
        assertEquals(2, NotePrompt.parse("""{"result":"Birdie"}""", par3)?.strokes)
        // A number beats a name when both come back.
        assertEquals(7, NotePrompt.parse("""{"score":7,"result":"par"}""", par4)?.strokes)
    }

    @Test fun rejectsNonsense() {
        assertNull(NotePrompt.parse("I can't help with that", par4))
        val r = NotePrompt.parse("""{"score":"6","putts":9,"tee":"left","penalties":-1,"bunker":"two"}""", par3)!!
        assertEquals(6, r.strokes) // numbers sent as strings are fine
        assertNull(r.putts)        // impossible
        assertNull(r.teeShot)      // no tee shot on a par 3
        assertNull(r.penalties)
        assertNull(r.bunkerShots)
        assertNull(NotePrompt.parse("""{"score":4,"putts":4}""", par4)!!.putts) // can't putt every shot
    }

    @Test fun dropsCountsTheNoteNeverTalksAbout() {
        val note = "6. drive left in the rough, two to get out of the fairway bunker, two putts"
        val r = NotePrompt.parse("""{"score":null,"putts":2,"tee":"left","penalties":2,"bunker":2}""", HoleContext(1, 5), note)!!
        assertNull(r.penalties) // seen from Gemini Nano: "two to get out" is not a penalty
        assertEquals(2, r.bunkerShots)
        assertEquals(2, r.putts)
        assertEquals(1, NotePrompt.parse("""{"penalties":1}""", par4, "pulled the driver OB, reloaded")!!.penalties)
        assertNull(NotePrompt.parse("""{"putts":2}""", par4, "drive left, chipped on")!!.putts)
    }

    @Test fun quickReadsALeadingScoreOnly() {
        assertEquals(8, NotePrompt.quickRead("8, lost a ball right").strokes)
        assertEquals(5, NotePrompt.quickRead("5").strokes)
        assertEquals(6, NotePrompt.quickRead("Six hit first shot left").strokes) // as dictated
        assertEquals(7, NotePrompt.quickRead("pulled it OB and made seven").strokes)
        assertNull(NotePrompt.quickRead("seven iron to the back").strokes)
        assertEquals(6, NotePrompt.quickRead(" 6. drive left").strokes)
        // A real one: the 9 iron and the 20 and 8 feet must not be taken for the score.
        assertEquals(6, NotePrompt.quickRead("6, hit first shot left into tee, chipped out, got a really good 9 iron onto the green and then 3 putt from 20 feet. First putt was a bad one, it ended up like 8 feet away", 4).strokes)
        assertNull(NotePrompt.quickRead("7 iron to the back").strokes)
        assertNull(NotePrompt.quickRead("10 feet for birdie").strokes)
        assertNull(NotePrompt.quickRead("3 putt from 40ft").strokes)
        assertNull(NotePrompt.quickRead("150 out").strokes)
    }

    @Test fun checksThatCatchNanosMisses() {
        // All seen from Gemini Nano on the eval set.
        assertNull(NotePrompt.parse("""{"result":"birdie"}""", HoleContext(1, 5), "duffed my chip, then chipped in")!!.strokes)
        assertNull(NotePrompt.parse("""{"score":6,"putts":0}""", par4, "Gotta 6 drove it right, bad part hit it to 8 ft from like 20 ft")!!.putts)
        assertNull(NotePrompt.parse("""{"score":8,"putts":1}""", par4, "8, iron in to 10 feet but missed the putt low")!!.putts)
        assertEquals(TeeShot.RIGHT, NotePrompt.quickRead("bogey, sliced it into the trees, punched out", 4).teeShot)
        assertEquals(TeeShot.LEFT, NotePrompt.quickRead("hooked my drive, chipped out", 4).teeShot)
        assertNull(NotePrompt.quickRead("pulled it left of the green", 4).teeShot) // names a side itself: leave it to the reader
        assertNull(NotePrompt.quickRead("sliced it", 3).teeShot) // no tee shot field on a par 3
    }

    @Test fun teeClubFromTheFirstPhrase() {
        assertEquals("5i", NotePrompt.quickRead("5 iron right, second shot 6 iron short right", 4).teeClub)
        assertEquals("Driver", NotePrompt.quickRead("pulled the driver OB, reloaded, made 7", 4).teeClub)
        assertEquals("7i", NotePrompt.quickRead("7 iron to the back of the green, three putted for a 4", 3).teeClub)
        assertEquals("3W", NotePrompt.quickRead("Three wood down the middle", 5).teeClub)
        assertNull(NotePrompt.quickRead("drive left, 7 iron to the green", 4).teeClub)
        assertNull(NotePrompt.quickRead("bogey, then 8 iron in", 4).teeClub)
        assertNull(NotePrompt.quickRead("Gotta 6 drove it right into the trees chipped out got nine iron onto the green", 4).teeClub)
    }

    @Test fun noScoreHoles() {
        // Picked up: saves with no score, keeps what else you recorded, and counts as done.
        var f = HoleEntryForm.start(4, 4, null).tapStrokes(8).tapTeeShot(TeeShot.LEFT).tapNoScore(true)
        assertNull(f.strokes.value)
        val saved = f.toHoleScore(null, emptyList())!!
        assertNull(saved.strokes); assertEquals(true, saved.pickedUp); assertEquals(TeeShot.LEFT, saved.teeShot)
        assertEquals(true, saved.isDone)
        // Tapping a score again turns it off.
        f = f.tapStrokes(6)
        assertEquals(false, f.noScore.value)
        // From the note.
        assertEquals(true, NotePrompt.quickRead("two in the water, picked up", 4).pickedUp)
        assertEquals(true, HoleEntryForm.start(4, 4, null).read(NotePrompt.quickRead("didn't finish, lost two balls", 4)).noScore.value)
        assertNull(NotePrompt.quickRead("picked the wrong club, made 5", 4).pickedUp)
        // Totals leave it out; "thru" counts it.
        val round = me.vanpra.tally.round.Round("r", "Wanstead", date = "2026-11-07", holes = listOf(HoleScore(1, 4, strokes = 5), saved.copy(number = 2)))
        assertEquals(1, round.played.size); assertEquals(2, round.done.size); assertEquals(5, round.strokes)
    }

    @Test fun aChipInMeansNoPutts() {
        assertEquals(0, NotePrompt.quickRead("duffed my chip, then chipped in", 5).putts)
        assertEquals(0, NotePrompt.quickRead("bogey, holed out from the bunker", 4).putts)
        assertNull(NotePrompt.quickRead("chipped in to 10 feet, two putts", 4).putts)
        // Dictated "three-pot" is still a three putt to the guard.
        assertEquals(3, NotePrompt.parse("""{"score":6,"putts":3}""", par4, "made a 6 then a three-pot")!!.putts)
    }

    @Test fun quickReadsScoresStatedLater() {
        assertEquals(5, NotePrompt.quickRead("drive left, made 5").strokes)
        assertEquals(4, NotePrompt.quickRead("7 iron to the back, three putted for a 4", 3).strokes)
        assertEquals(4, NotePrompt.quickRead("drive down the middle, two putts for par", 4).strokes)
        assertEquals(5, NotePrompt.quickRead("bogey, sliced it into the trees", 4).strokes)
        assertEquals(6, NotePrompt.quickRead("Double. Water right", 4).strokes)
        assertEquals(3, NotePrompt.quickRead("chipped to 3 feet for birdie", 4).strokes)
        assertNull(NotePrompt.quickRead("greenside bunker, took 3 to get out").strokes)
        assertNull(NotePrompt.quickRead("left it short for 20 feet", 4).strokes)
        assertNull(NotePrompt.quickRead("missed a birdie putt from 6 feet", 4).strokes)
        assertNull(NotePrompt.quickRead("par 5 into the wind", 5).strokes)
        assertNull(NotePrompt.quickRead("went for the green", 4).strokes)
    }

    @Test fun noteFillsUntouchedFields() {
        val f = HoleEntryForm.start(1, 4, null).read(NoteReading(8, 2, TeeShot.RIGHT, 2, null))
        assertEquals(8, f.strokes.value)
        assertEquals(Origin.NOTE, f.strokes.origin)
        assertEquals(TeeShot.RIGHT, f.teeShot.value)
        assertEquals(0, f.bunkerShots.value)
        assertEquals(Origin.BASE, f.bunkerShots.origin)
        assertEquals(listOf("strokes", "putts", "teeShot", "penalties"), f.filledFromNote)
    }

    @Test fun aTapStaysPutWhileYouKeepWriting() {
        var f = HoleEntryForm.start(1, 4, null).read(NoteReading(strokes = 6))
        f = f.tapStrokes(5)
        // More words, same score in the note: your tap stands.
        f = f.read(NoteReading(strokes = 6, putts = 2))
        assertEquals(5, f.strokes.value)
        assertEquals(Origin.YOU, f.strokes.origin)
        assertEquals(Origin.NOTE, f.putts.origin)
        // You rewrite the score in the note: now the note wins.
        f = f.read(NoteReading(strokes = 7, putts = 2))
        assertEquals(7, f.strokes.value)
    }

    @Test fun fieldsGoBackWhenTheNoteStopsMentioningThem() {
        var f = HoleEntryForm.start(1, 4, null).read(NoteReading(strokes = 6, penalties = 1))
        f = f.read(NoteReading(strokes = 6))
        assertEquals(0, f.penalties.value)
        assertEquals(Origin.BASE, f.penalties.origin)
        // A tapped value is never taken away.
        f = f.tapPutts(3).read(NoteReading(strokes = 6, putts = 1)).read(NoteReading(strokes = 6))
        assertEquals(3, f.putts.value)
    }

    @Test fun editingAnOldNoteKeepsWhatYouCorrected() {
        val saved = HoleScore(1, 4, strokes = 7, putts = 2, fieldSources = mapOf("strokes" to FieldSource.MANUAL, "putts" to FieldSource.AI))
        // The old note said 8; you'd fixed it to 7 by hand.
        var f = HoleEntryForm.start(1, 4, saved).withBaseline(NoteReading(strokes = 8, putts = 2))
        f = f.read(NoteReading(strokes = 8, putts = 2, teeShot = TeeShot.LEFT))
        assertEquals(7, f.strokes.value)
        assertEquals(TeeShot.LEFT, f.teeShot.value)
        val score = f.toHoleScore(saved, emptyList())!!
        assertEquals(FieldSource.MANUAL, score.fieldSources["strokes"])
        assertEquals(FieldSource.AI, score.fieldSources["teeShot"])
    }

    @Test fun countsFitTheScore() {
        // 6 with 3 putts: the tee shot plus 3 putts leaves 2 for penalties and bunker shots.
        var f = HoleEntryForm.start(1, 4, null).tapStrokes(6).tapPutts(3)
        assertEquals(2, f.maxFor("penalties"))
        f = f.tapPenalties(2)
        assertEquals(0, f.maxFor("bunkerShots"))
        // Dropping the score: the defaults and older taps give way, never the thing you just changed.
        f = HoleEntryForm.start(1, 4, null).tapStrokes(7).tapBunkerShots(2).tapPenalties(2)
        f = f.tapStrokes(5) // 1 + putts 2 (default) + 2 + 2 = 7 > 5: default putts give way first
        assertEquals(0, f.putts.value)
        assertEquals(2, f.bunkerShots.value)
        assertEquals(2, f.penalties.value)
        f = f.tapPutts(2) // your new putts stay; the older bunker tap gives way
        assertEquals(2, f.putts.value)
        assertEquals(0, f.bunkerShots.value + f.penalties.value - 2)
        // A note that over-counts is trimmed the same way.
        val n = HoleEntryForm.start(1, 4, null).read(NoteReading(strokes = 4, putts = 2, penalties = 2))
        assertEquals(4, 1 + n.putts.value + n.penalties.value + n.bunkerShots.value)
    }

    @Test fun trimmedCountsComeBack() {
        // Typing "two putts for bogey": "two" is briefly the score, then bogey (5) arrives.
        var f = HoleEntryForm.start(1, 4, null).read(NoteReading(strokes = 2))
        assertEquals(1, f.putts.value)
        f = f.read(NoteReading(strokes = 5))
        assertEquals(2, f.putts.value)
    }

    @Test fun puttDistancesFollowThePutts() {
        var f = HoleEntryForm.start(1, 4, null).tapStrokes(6).tapPutts(3)
        assertEquals(listOf<Int?>(null, null, null), f.puttFeet)
        f = f.tapPuttFeet(0, 20).tapPuttFeet(1, 8).tapPuttFeet(2, 2)
        assertEquals(listOf<Int?>(20, 8, 2), f.puttFeet)
        val saved = f.toHoleScore(null, emptyList())!!
        assertEquals(20, saved.firstPuttFeet); assertEquals(8, saved.leaveFeet); assertEquals(listOf(2), saved.laterPuttFeet)
        // Fewer putts later: the extra distance isn't saved.
        assertEquals(emptyList(), f.tapPutts(2).toHoleScore(null, emptyList())!!.laterPuttFeet)
    }

    @Test fun puttsNeverExceedStrokes() {
        val f = HoleEntryForm.start(3, 3, null).tapStrokes(2)
        assertEquals(1, f.putts.value)
        assertEquals(0, f.tapStrokes(1).putts.value) // an ace has no putts
    }

    @Test fun tidiesDictatedGolf() {
        // Exactly what the phone's recogniser produced on the course.
        assertEquals(
            "made a six hit my tee shot a bit left but just in the left rough. made a 6 my tee shot bit left I then made a three putt and it.",
            GolfSpeech.tidy("Need a six hit my T-shirt a bit left but just in the left rough. May the 6th my T-shirt bit left I then made a three-pot and it."),
        )
        assertEquals("need a 6 iron", GolfSpeech.tidy("need a 6 iron"))
        assertEquals("2 putt for bogey", GolfSpeech.tidy("2 pots for boogie"))
    }

    @Test fun findsTheWordsBehindEachValue() {
        val note = "6, hit first shot left into tee, chipped out, got a really good 9 iron onto the green and then 3 putt from 20 feet. First putt was a bad one, it ended up like 8 feet away"
        val spans = NoteEvidence.spans(note, NoteReading(strokes = 6, putts = 3, teeShot = TeeShot.LEFT, firstPuttFeet = 20, leaveFeet = 8), 4)
        assertEquals(
            listOf("strokes" to "6", "teeShot" to "first shot left into tee", "putts" to "3 putt", "firstPuttFeet" to "20 feet", "leaveFeet" to "8 feet away"),
            spans.map { it.field to note.substring(it.start, it.end) },
        )
        val n2 = "8, lost a ball right off the tee took a penalty drop 2 shots, hit a nice iron in to 10 feet but missed the putt low"
        val s2 = NoteEvidence.spans(n2, NoteReading(strokes = 8, putts = 2, teeShot = TeeShot.RIGHT, penalties = 2, firstPuttFeet = 10, puttMiss = me.vanpra.tally.round.PuttMiss.LOW), 4)
        assertEquals(
            listOf("strokes" to "8", "penalties" to "lost a ball", "teeShot" to "right off the tee", "penalties" to "penalty drop 2 shots", "firstPuttFeet" to "10 feet", "putts" to "putt", "firstPuttMiss" to "low"),
            s2.map { it.field to n2.substring(it.start, it.end) },
        )
        val n3 = "two putts for par"
        assertEquals(listOf("putts" to "two putts", "strokes" to "for par"), NoteEvidence.spans(n3, NoteReading(strokes = 4, putts = 2), 4).map { it.field to n3.substring(it.start, it.end) })
    }

    @Test fun aMissNeedsWordsAboutThePutt() {
        val note = "6, hit first shot left into tee, chipped out, then 3 putt from 20 feet. First putt was a bad one, it ended up like 8 feet away"
        // Gemini Nano said the first putt missed left, borrowing the tee shot's "left".
        assertNull(NotePrompt.parse("""{"score":6,"putts":3,"miss":"left"}""", par4, note)!!.puttMiss)
        assertEquals(me.vanpra.tally.round.PuttMiss.LOW, NotePrompt.parse("""{"putts":2,"miss":"low"}""", par4, "missed the putt low")!!.puttMiss)
    }

    @Test fun yourDictatedNoteFromTheRange() {
        val said = "Gotta 6 drove it right into the trees chipped out got nine iron onto the green but then I had really bad part hit it to 8 ft from like 20 ft and then just too far from there"
        val note = GolfSpeech.tidy(said)
        assertEquals(true, note.startsWith("got a 6") && "bad putt" in note)
        // Gemini Nano said 8, from "to 8 ft": not a score in this note.
        assertNull(NotePrompt.parse("""{"score":8,"tee":"right"}""", par4, said)!!.strokes)
        assertEquals(6, NotePrompt.quickRead(said, 4).strokes)
        assertEquals(6, NotePrompt.quickRead(note, 4).strokes)
    }

    @Test fun aNamedResultBeatsAStrayNumber() {
        // Seen from Gemini Nano: "birdie! 9 iron to 4 feet" came back with score 4.
        assertEquals(2, NotePrompt.parse("""{"score":4,"result":"birdie"}""", par3, "birdie! 9 iron to 4 feet")!!.strokes)
    }

    @Test fun readsPuttingDetails() {
        val r = NotePrompt.parse("""{"score":6,"putts":3,"tee":"left","first_putt_ft":20,"miss":"short","leave_ft":8}""", par4, "6, left, 3 putt from 20 feet, left it 8 feet short")!!
        assertEquals(20, r.firstPuttFeet)
        assertEquals(me.vanpra.tally.round.PuttMiss.SHORT, r.puttMiss)
        assertEquals(8, r.leaveFeet)
        // A one-putt can't have missed.
        assertNull(NotePrompt.parse("""{"putts":1,"miss":"low","leave_ft":3}""", par4, "one putt from 3 feet")!!.puttMiss)
    }
}
