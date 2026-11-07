package me.vanpra.tally.round

import me.vanpra.tally.platform.ImportFile

/**
 * Reads the two CSVs produced by the Golfshot export script (rounds.csv and holes.csv)
 * into [Round]s. Only the owner's scores are in the files, so every row belongs to the user.
 */
object GolfshotImport {
    sealed interface Result {
        data class Success(val rounds: List<Round>) : Result
        data class Problem(val message: String) : Result
    }

    fun parse(files: List<ImportFile>): Result {
        val tables = files.map { parseCsv(it.text) }.filter { it.isNotEmpty() }
        val roundsTable = tables.firstOrNull { "holes_played" in it.first() && "round_id" in it.first() }
        val holesTable = tables.firstOrNull { "hole" in it.first() && "stroke_index" in it.first() }
        if (roundsTable == null || holesTable == null) {
            return Result.Problem("Pick both rounds.csv and holes.csv from the Golfshot export.")
        }
        val rounds = rows(roundsTable)
        val holesByRound = rows(holesTable).groupBy { it["round_id"].orEmpty() }

        return Result.Success(rounds.mapNotNull { r ->
            val id = r["round_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val (course, layout) = splitCourse(r["course"].orEmpty())
            val holes = holesByRound[id].orEmpty().mapNotNull { h -> hole(h) }.sortedBy { it.number }
            Round(
                id = "golfshot-$id",
                courseName = course,
                tee = r["tee"]?.takeIf { it.isNotBlank() } ?: layout,
                date = isoDate(r["date"].orEmpty()) ?: return@mapNotNull null,
                source = RoundSource.GOLFSHOT,
                holes = holes,
            )
        })
    }

    private fun hole(h: Map<String, String>): HoleScore? {
        val number = h["hole"]?.toIntOrNull() ?: return null
        val strokes = h["score"]?.toIntOrNull()
        val played = strokes != null
        return HoleScore(
            number = number,
            par = h["par"]?.toIntOrNull(),
            strokes = strokes,
            putts = h["putts"]?.toIntOrNull(),
            teeShot = when (h["fairway"]) {
                "hit" -> TeeShot.FAIRWAY
                "left" -> TeeShot.LEFT
                "right" -> TeeShot.RIGHT
                else -> null
            },
            // Golfshot leaves these blank when there were none.
            penalties = h["penalties"]?.toIntOrNull() ?: if (played) 0 else null,
            bunkerShots = h["sand_shots"]?.toIntOrNull() ?: if (played) 0 else null,
            teeClub = h["tee_club"]?.takeIf { it.isNotBlank() }?.let { me.vanpra.tally.ai.Clubs.normalize(it) ?: it },
            girRecorded = when (h["gir"]) { "True" -> true; "False" -> false; else -> null },
            fieldSources = if (played) mapOf("all" to FieldSource.IMPORTED) else emptyMap(),
        )
    }

    /** "Wanstead Golf Club - Wanstead" → ("Wanstead Golf Club", "Wanstead"). */
    private fun splitCourse(full: String): Pair<String, String?> {
        val i = full.indexOf(" - ")
        return if (i < 0) full.trim() to null else full.substring(0, i).trim() to full.substring(i + 3).trim()
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Oct 03, 2026" → "2026-10-03". */
    internal fun isoDate(s: String): String? {
        val m = Regex("""([A-Za-z]{3})\w*\s+(\d{1,2}),\s*(\d{4})""").find(s.trim()) ?: return null
        val month = months.indexOf(m.groupValues[1].replaceFirstChar { it.uppercase() }) + 1
        if (month == 0) return null
        return "${m.groupValues[3]}-${month.toString().padStart(2, '0')}-${m.groupValues[2].padStart(2, '0')}"
    }

    private fun rows(table: List<List<String>>): List<Map<String, String>> {
        val header = table.first()
        return table.drop(1).filter { it.size > 1 }.map { row -> header.indices.associate { header[it] to row.getOrElse(it) { "" } } }
    }

    /** RFC 4180 CSV: quoted fields may contain commas, quotes ("") and newlines. */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
