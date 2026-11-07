package me.vanpra.tally.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.vanpra.tally.course.Course
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.TeeShot
import me.vanpra.tally.round.formatToPar
import me.vanpra.tally.ui.components.PrimaryButton
import me.vanpra.tally.ui.components.ScoreMark
import me.vanpra.tally.ui.components.SecondaryButton
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally

/** The card inside round mode: totals, both nines, and the way back to the hole or to finish. */
@Composable
internal fun RoundCard(
    course: Course,
    round: Round,
    currentHole: Int,
    topPadding: Dp,
    onHole: (Int) -> Unit,
    onBackToHole: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val c = Tally.colors
    val numbers = course.holes.map { it.number }
    val pars = course.holes.associate { it.number to it.par }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundSummary(round)
            ScorecardGrid(round, numbers.take(9), pars, currentHole, "Out", onHole)
            if (numbers.size > 9) ScorecardGrid(round, numbers.drop(9), pars, currentHole, "In", onHole)
            Legend()
            Text("Tap a score to change it.", style = Tally.type.label.copy(color = c.muted))
            // Quiet on purpose: it's there when you need it, never in the way of Finish.
            Text(
                "Discard round",
                style = Tally.type.label.copy(color = c.warn),
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onDiscard).padding(vertical = 8.dp, horizontal = 2.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(c.bg).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton("Back to hole $currentHole", onClick = onBackToHole, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Finish round", onClick = onFinish, modifier = Modifier.fillMaxWidth(), color = c.warn)
        }
    }
}

/** Four headline numbers. Everything else lives in Stats. */
@Composable
fun RoundSummary(round: Round) {
    val played = round.played
    val drives = played.filter { (it.par ?: 0) >= 4 && it.teeShot != null }
    val girKnown = played.mapNotNull { it.gir }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile(if (played.isEmpty()) "–" else round.strokes.toString(), if (played.isEmpty()) "Score" else formatToPar(round.toPar), Modifier.weight(1f))
        SummaryTile(played.mapNotNull { it.putts }.takeIf { it.isNotEmpty() }?.sum()?.toString() ?: "–", "Putts", Modifier.weight(1f))
        SummaryTile(if (drives.isEmpty()) "–" else "${drives.count { it.teeShot == TeeShot.FAIRWAY }}/${drives.size}", "Fairways", Modifier.weight(1f))
        SummaryTile(if (girKnown.isEmpty()) "–" else "${girKnown.count { it }}/${girKnown.size}", "Greens", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier) {
    val c = Tally.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = Tally.type.numberLarge.copy(color = c.ink, fontSize = Tally.type.numberLarge.fontSize * 0.8f))
        Text(label, style = Tally.type.label.copy(color = c.muted))
    }
}

/** One nine of the card. Columns: hole numbers, par, score marks, putts, and a total. */
@Composable
fun ScorecardGrid(
    round: Round,
    holes: List<Int>,
    pars: Map<Int, Int?>,
    currentHole: Int?,
    totalLabel: String,
    onHole: ((Int) -> Unit)?,
) {
    val c = Tally.colors
    val t = Tally.type
    val scores = holes.mapNotNull { round.hole(it)?.strokes }
    val putts = holes.mapNotNull { round.hole(it)?.putts }
    TallyCard(padding = 10.dp) {
        @Composable
        fun line(label: String, content: @Composable (Int) -> Unit, total: String) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize), modifier = Modifier.width(40.dp))
                holes.forEach { n -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content(n) } }
                Text(total, style = t.numberMedium.copy(color = c.ink), modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
            }
        }
        line("Hole", { n -> Text(n.toString(), style = t.label.copy(color = if (n == currentHole) c.accent else c.muted)) }, totalLabel)
        Spacer(Modifier.height(2.dp))
        line("Par", { n -> Text(pars[n]?.toString() ?: "–", style = t.label.copy(color = c.muted)) }, holes.sumOf { pars[it] ?: 0 }.toString())
        Spacer(Modifier.height(4.dp))
        line(
            "Score",
            { n ->
                val h = round.hole(n)
                ScoreMark(
                    strokes = h?.strokes,
                    noScore = h?.pickedUp == true,
                    par = pars[n] ?: h?.par,
                    size = 30.dp,
                    highlight = n == currentHole,
                    modifier = if (onHole != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onHole(n) } else Modifier,
                )
            },
            if (scores.isEmpty()) "–" else scores.sum().toString(),
        )
        Spacer(Modifier.height(4.dp))
        line("Putts", { n -> Text(round.hole(n)?.putts?.toString() ?: "", style = t.label.copy(color = c.ink)) }, if (putts.isEmpty()) "" else putts.sum().toString())
    }
}

@Composable
fun Legend() {
    val c = Tally.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(-1 to "Birdie", 1 to "Bogey", 2 to "Double+").forEach { (d, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScoreMark(strokes = 4 + d, par = 4, size = 18.dp, showNumber = false)
                Text(label, style = Tally.type.label.copy(color = c.muted))
            }
        }
    }
}
