package me.vanpra.tally.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import me.vanpra.tally.course.Course
import me.vanpra.tally.db.TallyDatabase
import me.vanpra.tally.round.ClubDistance
import me.vanpra.tally.round.ClubDistances
import me.vanpra.tally.round.HoleAverage
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.StatsData
import me.vanpra.tally.round.StatsFilter
import me.vanpra.tally.round.StatsView
import me.vanpra.tally.round.TeeClub
import me.vanpra.tally.round.formatAvg
import me.vanpra.tally.round.oneDecimal
import me.vanpra.tally.round.sameCourse
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.Segmented
import me.vanpra.tally.ui.components.Sparkline
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally
import kotlin.math.roundToInt

/**
 * Your game, from the rounds you pick (latest 10, 20 or all; one course or all of them): the one thing
 * that stands out first, then scoring, the tee, around the green, putting, and hole by hole on a course.
 * The numbers come from SQL over the rounds you picked, and update as rounds are saved.
 */
@Composable
fun StatsScreen(
    history: List<Round>,
    db: TallyDatabase?,
    course: Course? = null,
    /** Every round, the one in progress too: for shots you've marked. */
    tracked: List<Round> = history,
) {
    val c = Tally.colors
    val t = Tally.type
    var window by rememberSaveable { mutableStateOf(20) }
    var courseName by rememberSaveable { mutableStateOf<String?>(null) }
    val courses = remember(history) { StatsData.courses(history) }
    val filter = StatsFilter(window, courseName)
    val picked = remember(history, filter) { StatsData.pick(history, filter) }

    // Re-run whenever a hole is saved: SQLDelight tells us when the hole table changes.
    val view by produceState<StatsView?>(null, db, picked) {
        val d = db ?: return@produceState
        d.statsQueries.putting(picked.map { it.id }).asFlow().collectLatest {
            value = withContext(Dispatchers.Default) { StatsData.load(d, picked, oneCourse = courseName != null) }
        }
    }
    val clubs = remember(tracked, course) {
        val holes = course?.holes?.associateBy { it.number }.orEmpty()
        ClubDistances.of(tracked.filter { it.courseId == course?.id }, { holes }, minShots = 1)
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Stats", style = t.holeNumber.copy(color = c.ink))
        Segmented(listOf(10 to "Last 10", 20 to "Last 20", 0 to "All"), window, { window = it ?: 20 }, Modifier.fillMaxWidth())
        if (courses.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("All courses", courseName == null) { courseName = null }
                courses.forEach { pc -> Chip(shortCourse(pc.name), courseName != null && sameCourse(courseName!!, pc.name)) { courseName = pc.name } }
            }
        }

        val s = view
        when {
            picked.isEmpty() -> TallyCard(padding = 18.dp) {
                Text("No rounds here yet.", style = t.title.copy(color = c.ink))
                Text("Stats appear once you've played or imported a round of 9 holes or more.", style = t.body.copy(color = c.muted))
            }
            s == null -> Spacer(Modifier.height(240.dp))
            else -> {
                Overview(s)
                s.insight?.let { InsightCard(it) }
                Scoring(s)
                s.tee?.let { TeeCard(it) }
                AroundTheGreen(s)
                Putting(s)
                if (s.holeByHole.size >= 9) HoleByHole(s.holeByHole, shortCourse(courseName ?: ""))
            }
        }
        if (clubs.isNotEmpty()) ClubDistanceCard(clubs)
        s?.let {
            Text(
                "${it.rounds} ${if (it.rounds == 1) "round" else "rounds"} · ${it.holes} holes" + (courseName?.let { n -> " at ${shortCourse(n)}" } ?: ""),
                style = t.label.copy(color = c.muted),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun shortCourse(name: String) = name.replace(" Golf & Country Club", "").replace(" Golf Club", "").replace(" Park", " Park")

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = Tally.colors
    Text(
        label,
        style = Tally.type.label.copy(color = if (on) c.bg else c.ink),
        modifier = Modifier.clip(CircleShape).background(if (on) c.ink else c.raise).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Scoring average, the trend, and how far over par a round runs. */
@Composable
private fun Overview(s: StatsView) {
    val c = Tally.colors
    val t = Tally.type
    TallyCard(padding = 18.dp) {
        SectionLabel(if (s.scoringAverage != null) "Scoring average" else "Over par, per 18 holes")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.scoringAverage?.oneDecimal() ?: formatAvg(s.toParPer18), style = t.numberHero.copy(color = c.ink, fontSize = t.numberHero.fontSize * 0.8f))
                Text(
                    listOfNotNull(
                        formatAvg(s.toParPer18) + " to par",
                        s.bestRound?.let { "best $it" },
                    ).joinToString(" · "),
                    style = t.label.copy(color = c.muted),
                )
            }
            if (s.recent.size >= 3) Sparkline(s.recent, Modifier.width(132.dp).height(52.dp))
        }
    }
}

@Composable
private fun InsightCard(text: String) {
    val c = Tally.colors
    val t = Tally.type
    TallyCard(padding = 18.dp) {
        SectionLabel("Biggest pattern")
        Spacer(Modifier.height(6.dp))
        Text(text, style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.15f, lineHeight = t.title.fontSize * 1.6f))
    }
}

/** What your holes add up to: the mix of scores, and par 3s, 4s and 5s. */
@Composable
private fun Scoring(s: StatsView) {
    val c = Tally.colors
    val t = Tally.type
    val m = s.mix
    val parts = listOf(
        Triple("Birdie+", m.underPar, c.accent),
        Triple("Par", m.pars, c.mapFairway),
        Triple("Bogey", m.bogeys, c.mapSand),
        Triple("Double", m.doubles, c.flag.copy(alpha = 0.75f)),
        Triple("Worse", m.worse, c.warn.copy(alpha = 0.8f)),
    )
    TallyCard(padding = 18.dp) {
        SectionLabel("Scoring")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp))) {
            parts.filter { it.second > 0 }.forEach { (_, n, colour) ->
                Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colour))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            parts.forEach { (label, n, colour) ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
                        Spacer(Modifier.width(5.dp))
                        Text(label, style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
                    }
                    Text("${pct(n, m.holes)}%", style = t.numberMedium.copy(color = c.ink))
                    Text("${(n * 18.0 / m.holes.coerceAtLeast(1)).oneDecimal()}/rd", style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
                }
            }
        }
        if (s.byPar.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { par ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.raise).padding(12.dp)) {
                        Text(s.byPar[par]?.let { formatAvg(it) } ?: "–", style = t.numberLarge.copy(color = c.ink, fontSize = t.numberLarge.fontSize * 0.85f))
                        Text("Par ${par}s", style = t.label.copy(color = c.muted))
                    }
                }
            }
        }
    }
}

/** Where tee shots finish, overall and club by club, and what you go on to score. */
@Composable
private fun TeeCard(tee: me.vanpra.tally.round.TeeView) {
    val c = Tally.colors
    val t = Tally.type
    TallyCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionLabel("Off the tee", Modifier.weight(1f))
            Text("par 4s and 5s", style = t.label.copy(color = c.muted))
        }
        Spacer(Modifier.height(12.dp))
        FairwayLane(tee.leftPct, tee.fairwayPct, tee.rightPct)
        if (tee.clubs.size > 1 || tee.clubs.singleOrNull()?.shots?.let { it < tee.shots } == true) {
            Spacer(Modifier.height(14.dp))
            Row {
                Text("CLUB", style = t.caps.copy(color = c.muted), modifier = Modifier.width(64.dp))
                Text("LEFT · FAIRWAY · RIGHT", style = t.caps.copy(color = c.muted), modifier = Modifier.weight(1f))
                Text("SCORE", style = t.caps.copy(color = c.muted), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tee.clubs.forEach { TeeClubRow(it) } }
            Spacer(Modifier.height(8.dp))
            Text("Score is the average against par on holes you started with that club.", style = t.label.copy(color = c.muted))
        }
    }
}

@Composable
private fun TeeClubRow(club: TeeClub) {
    val c = Tally.colors
    val t = Tally.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(64.dp)) {
            Text(club.club, style = t.title.copy(color = c.ink))
            Text("${club.shots}", style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
        }
        Row(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(6.dp))) {
            listOf(club.leftPct to c.mapRough, club.fairwayPct to c.mapFairway, club.rightPct to c.mapRough).forEachIndexed { i, (v, colour) ->
                if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(colour), contentAlignment = Alignment.Center) {
                    if (v >= 14) Text("${v.roundToInt()}", style = t.label.copy(color = c.ink.copy(alpha = 0.8f), fontSize = t.caps.fontSize))
                }
                if (i < 2 && v > 0) Spacer(Modifier.width(1.dp))
            }
        }
        Text(formatAvg(club.toPar), style = t.numberMedium.copy(color = c.ink), modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

/** Hitting greens, and getting up and down when you don't. */
@Composable
private fun AroundTheGreen(s: StatsView) {
    TallyCard(padding = 18.dp) {
        SectionLabel("Approach and short game")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(s.girPct?.let { "${it.roundToInt()}%" } ?: "–", "Greens in reg", Modifier.weight(1f))
            Tile(s.scramblingPct?.let { "${it.roundToInt()}%" } ?: "–", "Par saved after a miss", Modifier.weight(1f))
            Tile(s.penaltiesPer18.oneDecimal(), "Penalties a round", Modifier.weight(1f))
        }
    }
}

/** Putts a round, one- and three-putts, and from your notes how first putts go by length. */
@Composable
private fun Putting(s: StatsView) {
    val c = Tally.colors
    val t = Tally.type
    TallyCard(padding = 18.dp) {
        SectionLabel("Putting")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(s.puttsPer18?.oneDecimal() ?: "–", "Putts a round", Modifier.weight(1f))
            Tile(s.onePuttPct?.let { "${it.roundToInt()}%" } ?: "–", "One-putts", Modifier.weight(1f))
            Tile(s.threePuttsPer18?.oneDecimal() ?: "–", "Three-putts a round", Modifier.weight(1f))
        }
        s.puttsPerGir?.let {
            Spacer(Modifier.height(8.dp))
            Text("${it.oneDecimal()} putts on greens you hit in regulation.", style = t.label.copy(color = c.muted))
        }
        if (s.puttBands.isNotEmpty()) PuttLengths(s.puttBands)
    }
}

/**
 * How first putts go by length, from your notes: one bar per length split into holed, two putts and
 * three or more, the same way tee shots split left, fairway and right.
 */
@Composable
private fun PuttLengths(bands: List<me.vanpra.tally.round.PuttBand>) {
    val c = Tally.colors
    val t = Tally.type
    val holed = c.blue
    val two = c.line
    val three = c.warn.copy(alpha = 0.85f)
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("FIRST PUTT", style = t.caps.copy(color = c.muted), modifier = Modifier.weight(1f))
        listOf("Holed" to holed, "2 putts" to two, "3+" to three).forEach { (label, colour) ->
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(colour))
            Spacer(Modifier.width(4.dp))
            Text(label, style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
        }
    }
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bands.forEach { b ->
            val twoPct = (100 - b.holedPct - b.threePuttPct).coerceAtLeast(0.0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, style = t.title.copy(color = c.ink, fontSize = t.label.fontSize), modifier = Modifier.width(68.dp))
                Row(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(6.dp))) {
                    listOf(b.holedPct to holed, twoPct to two, b.threePuttPct to three).filter { it.first > 0 }.forEachIndexed { i, (v, colour) ->
                        if (i > 0) Spacer(Modifier.width(1.dp))
                        Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(colour), contentAlignment = Alignment.Center) {
                            if (v >= 22) Text("${v.roundToInt()}%", style = t.label.copy(color = if (colour == two) c.ink else c.bg, fontSize = t.caps.fontSize))
                        }
                    }
                }
                Text("${b.holes}", style = t.label.copy(color = c.muted), modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("From the first-putt lengths in your notes; the number on the right is how many. Fills in as you log more rounds in Tally.", style = t.label.copy(color = c.muted))
}

/** Every hole on one course, shaded by how it plays for you, with the toughest and kindest called out. */
@Composable
private fun HoleByHole(holes: List<HoleAverage>, courseName: String) {
    val c = Tally.colors
    val t = Tally.type
    // Shaded against your own average hole, so your hardest and easiest holes stand out at any handicap.
    val usual = holes.mapNotNull { it.toPar }.average().takeIf { !it.isNaN() } ?: 0.0
    fun shade(toPar: Double?) = when {
        toPar == null -> c.raise
        toPar <= usual - 0.25 -> c.mapFairway
        toPar < usual + 0.25 -> c.raise
        toPar < usual + 0.6 -> c.mapSand
        else -> c.flag.copy(alpha = 0.55f)
    }
    val ranked = holes.filter { it.toPar != null && it.played >= 2 }.sortedBy { it.toPar }
    TallyCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionLabel("Hole by hole", Modifier.weight(1f))
            Text(courseName, style = t.label.copy(color = c.muted))
        }
        Spacer(Modifier.height(12.dp))
        holes.chunked(9).forEach { nine ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                nine.forEach { h ->
                    Column(
                        Modifier.weight(1f).aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)).background(shade(h.toPar)).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${h.number}", style = t.label.copy(color = c.ink.copy(alpha = 0.6f), fontSize = t.caps.fontSize))
                        Text(h.toPar?.let { formatAvg(it) } ?: "–", style = t.label.copy(color = c.ink, fontSize = t.caps.fontSize * 1.05f))
                    }
                }
                repeat(9 - nine.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (ranked.size >= 6) {
            Spacer(Modifier.height(6.dp))
            Text("Toughest: " + ranked.takeLast(3).reversed().joinToString(", ") { "${it.number} (${formatAvg(it.toPar!!)})" }, style = t.label.copy(color = c.ink))
            Text("Kindest: " + ranked.take(3).joinToString(", ") { "${it.number} (${formatAvg(it.toPar!!)})" }, style = t.label.copy(color = c.muted))
            Text("Shaded against your average hole here (${formatAvg(usual)}).", style = t.label.copy(color = c.muted))
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    val c = Tally.colors
    val t = Tally.type
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.raise).padding(12.dp)) {
        Text(value, style = t.numberLarge.copy(color = c.ink, fontSize = t.numberLarge.fontSize * 0.85f))
        Text(label, style = t.label.copy(color = c.muted), minLines = 2)
    }
}

/** How far you hit each club: a bar for where most shots land, a tick for the middle, on one shared scale. */
@Composable
private fun ClubDistanceCard(clubs: List<ClubDistance>) {
    val c = Tally.colors
    val t = Tally.type
    val top = (clubs.maxOf { it.highYards } * 1.08).coerceAtLeast(50.0)
    TallyCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionLabel("Club distances", Modifier.weight(1f))
            Text("from shots you marked", style = t.label.copy(color = c.muted))
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            clubs.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.club, style = t.title.copy(color = c.ink), modifier = Modifier.width(60.dp))
                    BoxWithConstraints(Modifier.weight(1f).height(22.dp)) {
                        val w = maxWidth
                        fun x(y: Int) = w * (y / top).toFloat().coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(c.raise))
                        Box(
                            Modifier.offset(x = x(d.lowYards)).width((x(d.highYards) - x(d.lowYards)).coerceAtLeast(6.dp)).height(12.dp)
                                .align(Alignment.CenterStart).clip(RoundedCornerShape(6.dp)).background(c.mapFairway),
                        )
                        Box(Modifier.offset(x = x(d.typicalYards) - 1.5.dp).width(3.dp).height(20.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(1.5.dp)).background(c.accent))
                    }
                    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.End) {
                        Text("${d.typicalYards}", style = t.numberMedium.copy(color = c.ink))
                        Text(if (d.shots == 1) "1 shot" else "${d.lowYards}–${d.highYards} · ${d.shots}", style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
                    }
                }
            }
        }
    }
}

/** Tee misses drawn as a fairway with rough either side, rather than a chart to decode. */
@Composable
private fun FairwayLane(left: Double, fairway: Double, right: Double) {
    val c = Tally.colors
    val t = Tally.type
    val total = (left + fairway + right).coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(16.dp))) {
        listOf(Triple(left, "Left", c.mapRough), Triple(fairway, "Fairway", c.mapFairway), Triple(right, "Right", c.mapRough)).forEach { (v, label, colour) ->
            Box(
                Modifier.weight((v / total).toFloat().coerceAtLeast(0.14f)).fillMaxHeight().background(colour),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${v.roundToInt()}%", style = t.numberLarge.copy(color = c.ink, fontSize = t.numberLarge.fontSize * 0.75f), textAlign = TextAlign.Center)
                    Text(label, style = t.label.copy(color = c.ink.copy(alpha = 0.75f)))
                }
            }
            if (label != "Right") Spacer(Modifier.width(2.dp))
        }
    }
}

private fun pct(n: Int, d: Int) = if (d == 0) 0 else (n * 100.0 / d).roundToInt()
