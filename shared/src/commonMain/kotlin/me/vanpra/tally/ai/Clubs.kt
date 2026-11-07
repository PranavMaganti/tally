package me.vanpra.tally.ai

/** Club names as golfers say them ("5 iron", "three wood", "rescue"), in one short form ("5i", "3W", "Hybrid"). */
object Clubs {
    /** The chips offered for the tee shot, most used first. */
    val teeChoices = listOf("Driver", "3W", "5W", "Hybrid", "3i", "4i", "5i", "6i", "7i", "8i", "9i", "PW", "GW", "SW", "LW")

    private val numbers = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
    private val n = """(\d|${numbers.joinToString("|")})"""
    private fun num(s: String) = s.toIntOrNull() ?: (numbers.indexOf(s.lowercase()) + 1)

    private val patterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""\bdriver\b|\b1\s*w(?:ood)?\b""", RegexOption.IGNORE_CASE) to { _ -> "Driver" },
        Regex("""\b$n\s*-?\s*wood\b|\b(\d)w\b""", RegexOption.IGNORE_CASE) to { m -> "${num(m.groupValues[1].ifEmpty { m.groupValues[2] })}W" },
        Regex("""\b(?:$n\s*-?\s*)?(?:hybrid|rescue)\b|\b\d\s*hy\b""", RegexOption.IGNORE_CASE) to { _ -> "Hybrid" },
        Regex("""\b$n\s*-?\s*iron\b|\b(\d)i\b""", RegexOption.IGNORE_CASE) to { m -> "${num(m.groupValues[1].ifEmpty { m.groupValues[2] })}i" },
        Regex("""\bpitching wedge\b|\bpw\b""", RegexOption.IGNORE_CASE) to { _ -> "PW" },
        Regex("""\b(?:gap|approach) wedge\b|\bgw\b""", RegexOption.IGNORE_CASE) to { _ -> "GW" },
        Regex("""\bsand wedge\b|\bsw\b""", RegexOption.IGNORE_CASE) to { _ -> "SW" },
        Regex("""\blob wedge\b|\blw\b""", RegexOption.IGNORE_CASE) to { _ -> "LW" },
    )

    /** The short form of a club name, or null if it isn't one. */
    fun normalize(said: String?): String? {
        val s = said?.trim().orEmpty()
        if (s.isEmpty()) return null
        teeChoices.firstOrNull { it.equals(s, ignoreCase = true) }?.let { return it }
        return patterns.firstNotNullOfOrNull { (r, f) -> r.find(s)?.let(f) }
    }

    /** Every club the note mentions, in order, with where it is. */
    fun mentioned(note: String): List<Pair<String, IntRange>> =
        patterns.flatMap { (r, f) -> r.findAll(note).map { f(it) to it.range }.toList() }.sortedBy { it.second.first }
}
