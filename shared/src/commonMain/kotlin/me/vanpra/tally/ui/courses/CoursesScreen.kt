package me.vanpra.tally.ui.courses

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.vanpra.tally.course.Course
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.oneDecimal
import me.vanpra.tally.round.sameCourse
import me.vanpra.tally.ui.components.PrimaryButton
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally

/** Courses you can play with GPS, then the others you've played, with your record at each. */
@Composable
fun CoursesScreen(course: Course?, history: List<Round>, roundInProgress: Boolean, onStartRound: () -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    val played = remember(history) {
        history.groupBy { it.courseName }
            .map { (name, rounds) -> Triple(name, rounds.size, rounds.filter { it.isFullRound }.map { it.strokes }.takeIf { it.isNotEmpty() }?.average()) }
            .sortedByDescending { it.second }
    }
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Courses", style = t.holeNumber.copy(color = c.ink))
        if (course != null) {
            SectionLabel("GPS ready", Modifier.padding(start = 4.dp, top = 4.dp))
            val mine = played.firstOrNull { sameCourse(it.first, course.name) }
            TallyCard(padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(course.name, style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.2f))
                        Text("${course.holes.size} holes · Par ${course.holes.sumOf { it.par ?: 0 }}", style = t.body.copy(color = c.muted))
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                }
                if (mine != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("Played ${mine.second} times" + (mine.third?.let { " · average ${it.oneDecimal()}" } ?: ""), style = t.label.copy(color = c.muted))
                }
                if (!roundInProgress) {
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Start round here", onClick = onStartRound, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val others = played.filter { course == null || !sameCourse(it.first, course.name) }
        if (others.isNotEmpty()) {
            SectionLabel("Played before", Modifier.padding(start = 4.dp, top = 8.dp))
            TallyCard(padding = 0.dp) {
                others.forEachIndexed { i, (name, n, avg) ->
                    if (i > 0) HorizontalDivider(color = c.line, modifier = Modifier.padding(start = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = t.title.copy(color = c.ink))
                            Text("$n ${if (n == 1) "round" else "rounds"}" + (avg?.let { " · average ${it.oneDecimal()}" } ?: ""), style = t.label.copy(color = c.muted))
                        }
                        Text("Not mapped yet", style = t.label.copy(color = c.muted))
                    }
                }
            }
        }
        Text("More courses will be added from OpenStreetMap. Portlethen and West Essex are already mapped there.", style = t.label.copy(color = c.muted), modifier = Modifier.padding(horizontal = 4.dp))
    }
}
