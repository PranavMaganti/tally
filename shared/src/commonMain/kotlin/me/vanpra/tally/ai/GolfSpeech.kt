package me.vanpra.tally.ai

/**
 * Speech recognisers are trained on everyday talk, so golf comes out as "T-shirt" and "three-pot".
 * This fixes the usual mishearings before the words reach the note, and lists golf words for
 * recognisers that can be nudged towards them.
 */
object GolfSpeech {
    val vocabulary: List<String> = listOf(
        "tee shot", "off the tee", "drive", "driver", "fairway", "rough", "bunker", "greenside bunker", "fairway bunker",
        "green", "putt", "putts", "two putt", "three putt", "one putt", "chipped in", "chip", "pitch", "wedge",
        "iron", "hybrid", "three wood", "par", "birdie", "bogey", "double bogey", "triple bogey", "eagle",
        "out of bounds", "OB", "penalty drop", "lost ball", "water", "lipped out", "up and down", "punched out",
        "sliced", "hooked", "pushed", "pulled", "feet", "yards", "made a", "the hole",
    )

    private const val NOT_A_CLUB = """(?!\s*(?:iron|wood|hybrid|rescue|foot|feet|ft|yards?))"""

    private val fixes: List<Pair<Regex, String>> = listOf(
        """\b(?:t[- ]?shirts?|tee[- ]?shirts?|teeshirts?|t[- ]?shot|tea shot|tee shut)\b""" to "tee shot",
        """\b(one|two|three|four|1|2|3|4)[- ]?(?:pot|pots|part|parts|put|puts|pod|pods|pots)\b""" to "$1 putt",
        """\b(one|two|three|four|1|2|3|4)[- ]?(?:potted|putted|pudded|potted it)\b""" to "$1 putted",
        """\b(bad|good|first|second|last|long|short|that|the|a|my|big|lag) (?:part|pot|pots)\b""" to "$1 putt",
        """\bgotta\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\b$NOT_A_CLUB""" to "got a $1",
        """\b(?:boogie|bogie|bogy|bogeyed)\b""" to "bogey",
        """\bbirdy\b""" to "birdie",
        """\bfair way\b""" to "fairway",
        """\bbunk(?:a|er)s?\b""" to "bunker",
        """\b(?:oh|o) b\b""" to "OB",
        """\bmay(?:d)?\s+(?:a|the)\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)(?:th|st|nd|rd)?\b$NOT_A_CLUB""" to "made a $1",
        """\bneed a\s+(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\b$NOT_A_CLUB""" to "made a $1",
        """\b(?:put|puts)\b(?= (?:from|for|to|of|was|missed|lipped))""" to "putt",
    ).map { (pattern, replacement) -> Regex(pattern, RegexOption.IGNORE_CASE) to replacement }

    fun tidy(said: String): String {
        var s = said
        fixes.forEach { (r, to) -> s = r.replace(s, to) }
        return s.replace(Regex("""\s+"""), " ").trim()
    }
}
