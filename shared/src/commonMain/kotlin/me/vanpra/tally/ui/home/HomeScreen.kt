package me.vanpra.tally.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.vanpra.tally.course.Course
import me.vanpra.tally.platform.todayIso
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.RoundSource
import me.vanpra.tally.round.displayDate
import me.vanpra.tally.round.formatToPar
import me.vanpra.tally.round.oneDecimal
import me.vanpra.tally.ui.components.Icon
import me.vanpra.tally.ui.components.PrimaryButton
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.Sparkline
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally

@Composable
fun HomeScreen(
    course: Course?,
    history: List<Round>,
    roundInProgress: Boolean,
    onStartRound: () -> Unit,
    onOpenRound: (Round) -> Unit,
    onImport: () -> Unit,
) {
    val c = Tally.colors
    val t = Tally.type
    val year = remember { todayIso().take(4) }
    var showAll by remember { mutableStateOf(false) }
    val full = remember(history) { history.filter { it.isFullRound } }
    val recent = full.take(10)
    val earlier = full.drop(10).take(10)

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp, bottom = 4.dp)) {
                Text(displayDate(todayIso()).let { if (it.endsWith(year)) it.dropLast(5) else it }, style = t.label.copy(color = c.muted))
                Text("Tally", style = t.holeNumber.copy(color = c.ink))
            }
        }

        if (!roundInProgress && course != null) {
            item {
                TallyCard(padding = 18.dp) {
                    SectionLabel("Ready when you are")
                    Spacer(Modifier.height(6.dp))
                    Text(course.name, style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.3f))
                    Text("${course.holes.size} holes · Par ${course.holes.sumOf { it.par ?: 0 }} · GPS mapped", style = t.body.copy(color = c.muted))
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Start round", onClick = onStartRound, modifier = Modifier.fillMaxWidth(), icon = Icon.FLAG)
                }
            }
        }

        if (recent.size >= 3) {
            item {
                TallyCard(padding = 18.dp) {
                    SectionLabel("Scoring average · last ${recent.size}")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            val avg = recent.map { it.strokes }.average()
                            Text(avg.oneDecimal(), style = t.numberHero.copy(color = c.ink, fontSize = t.numberHero.fontSize * 0.62f, lineHeight = t.numberHero.lineHeight * 0.62f))
                            if (earlier.size >= 3) {
                                val diff = avg - earlier.map { it.strokes }.average()
                                Text(
                                    if (diff <= 0) "${(-diff).oneDecimal()} better than the 10 before" else "${diff.oneDecimal()} worse than the 10 before",
                                    style = t.label.copy(color = if (diff <= 0) c.accent else c.warn),
                                )
                            }
                        }
                        Sparkline(recent.reversed().map { it.strokes }, Modifier.width(140.dp).height(52.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)); SectionLabel("Recent rounds", Modifier.padding(start = 4.dp)) }

        if (history.isEmpty()) {
            item {
                TallyCard(padding = 18.dp) {
                    Text("Your rounds will show here.", style = t.title.copy(color = c.ink))
                    Spacer(Modifier.height(4.dp))
                    Text("Play one with Tally, or bring your history across from Golfshot.", style = t.body.copy(color = c.muted))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Import from Golfshot",
                        style = t.title.copy(color = c.accent),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onImport).padding(vertical = 6.dp),
                    )
                }
            }
        } else {
            val shown = if (showAll) history else history.take(8)
            item {
                TallyCard(padding = 0.dp) {
                    shown.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = c.line, modifier = Modifier.padding(start = 16.dp))
                        RoundRow(r, year, onClick = { onOpenRound(r) })
                    }
                    if (history.size > shown.size) {
                        HorizontalDivider(color = c.line)
                        Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { showAll = true }.padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Show all ${history.size} rounds", style = t.title.copy(color = c.accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundRow(round: Round, year: String, onClick: () -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(round.courseName, style = t.title.copy(color = c.ink))
            val parts = listOfNotNull(
                displayDate(round.date, year),
                round.tee,
                if (round.isFullRound) null else "${round.played.size} ${if (round.played.size == 1) "hole" else "holes"}",
                if (round.source == RoundSource.GOLFSHOT) "Golfshot" else null,
            )
            Text(parts.joinToString(" · "), style = t.label.copy(color = c.muted))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (round.played.isEmpty()) "–" else round.strokes.toString(), style = t.numberLarge.copy(color = c.ink, fontSize = t.numberLarge.fontSize * 0.85f))
            if (round.played.isNotEmpty()) Text(formatToPar(round.toPar), style = t.label.copy(color = c.muted))
        }
    }
}
