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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.vanpra.tally.platform.PlatformBackHandler
import me.vanpra.tally.platform.todayIso
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.RoundSource
import me.vanpra.tally.round.displayDate
import me.vanpra.tally.round.formatToPar
import me.vanpra.tally.round.scoreName
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.round.Legend
import me.vanpra.tally.ui.round.RoundSummary
import me.vanpra.tally.ui.round.ScorecardGrid
import me.vanpra.tally.ui.theme.Tally

/** A finished round: summary, the card, and your notes hole by hole. */
@Composable
fun RoundDetailScreen(round: Round, onBack: () -> Unit, onDelete: () -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    var confirmDelete by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled = true, onBack = onBack)
    val numbers = round.holes.map { it.number }.ifEmpty { (1..18).toList() }
    val pars = round.holes.associate { it.number to it.par }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(c.raise).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Text("‹", style = t.holeNumber.copy(color = c.ink, fontSize = t.title.fontSize * 1.8f)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(round.courseName, style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.2f))
                Text(listOfNotNull(displayDate(round.date, todayIso().take(4)), round.tee, if (round.source == RoundSource.GOLFSHOT) "From Golfshot" else null).joinToString(" · "), style = t.label.copy(color = c.muted))
            }
        }
        RoundSummary(round)
        ScorecardGrid(round, numbers.take(9), pars, null, "Out", null)
        if (numbers.size > 9) ScorecardGrid(round, numbers.drop(9), pars, null, "In", null)
        Legend()

        val noted = round.holes.filter { it.notes.isNotEmpty() }
        if (noted.isNotEmpty()) {
            SectionLabel("Your notes", Modifier.padding(top = 8.dp, start = 4.dp))
            TallyCard {
                noted.forEachIndexed { i, h ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        "Hole ${h.number}" + (if (h.strokes != null && h.par != null) " · ${h.strokes} · ${scoreName(h.strokes, h.par)}" else ""),
                        style = t.label.copy(color = c.muted),
                    )
                    h.notes.forEach { Text(it.text, style = t.body.copy(color = c.ink)) }
                }
            }
        }

        val pens = round.played.sumOf { it.penalties ?: 0 }
        val sand = round.played.sumOf { it.bunkerShots ?: 0 }
        if (round.played.isNotEmpty()) {
            Text(
                "Penalty strokes $pens · Bunker shots $sand · ${formatToPar(round.toPar)} over ${round.played.size} ${if (round.played.size == 1) "hole" else "holes"}",
                style = t.label.copy(color = c.muted),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Delete round",
            style = t.title.copy(color = c.warn),
            modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { confirmDelete = true }.padding(horizontal = 4.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this round?", style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.25f)) },
            text = { Text("It will be removed from your history and stats. This can't be undone.", style = t.body.copy(color = c.muted)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", style = t.title.copy(color = c.warn)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = t.title.copy(color = c.muted)) } },
            containerColor = c.surface,
        )
    }
}
