package me.vanpra.tally.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import me.vanpra.tally.ai.ReaderState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.Bag
import me.vanpra.tally.round.ClubDistance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.vanpra.tally.round.RoundSource
import me.vanpra.tally.ui.ThemeChoice
import me.vanpra.tally.ui.components.Icon
import me.vanpra.tally.ui.components.SecondaryButton
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.Segmented
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally

@Composable
fun ProfileScreen(
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    history: List<Round>,
    canPickFiles: Boolean,
    onImport: () -> Unit,
    readerState: ReaderState,
    cloudAvailable: Boolean,
    cloudOn: Boolean,
    online: Boolean,
    onCloud: (Boolean) -> Unit,
    bag: Bag,
    /** Club distances measured from shots you've marked. */
    tracked: List<ClubDistance>,
    onToggleClub: (String) -> Unit,
    onClubYards: (String, Int?) -> Unit,
) {
    var editingBag by remember { mutableStateOf(false) }
    if (editingBag) BagSheet(bag, tracked, onToggleClub, onClubYards, onDismiss = { editingBag = false })
    val c = Tally.colors
    val t = Tally.type
    val imported = history.count { it.source == RoundSource.GOLFSHOT }
    val own = history.count { it.source == RoundSource.TALLY }
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profile", style = t.holeNumber.copy(color = c.ink))

        SectionLabel("Appearance", Modifier.padding(start = 4.dp, top = 4.dp))
        TallyCard(padding = 18.dp) {
            Segmented(
                listOf(ThemeChoice.DAY to "Day", ThemeChoice.DUSK to "Dusk", ThemeChoice.SYSTEM to "Match phone"),
                theme,
                { it?.let(onTheme) },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text("Day is built for bright sunlight. Dusk is easier on the eyes in the evening.", style = t.label.copy(color = c.muted))
        }

        SectionLabel("My bag", Modifier.padding(start = 4.dp, top = 8.dp))
        BagCard(bag, tracked, onOpen = { editingBag = true })

        SectionLabel("Your rounds", Modifier.padding(start = 4.dp, top = 8.dp))
        TallyCard(padding = 18.dp) {
            Text("${history.size} rounds", style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.2f))
            Text("$own played with Tally · $imported imported from Golfshot", style = t.body.copy(color = c.muted))
            Spacer(Modifier.height(14.dp))
            if (canPickFiles) {
                SecondaryButton("Import from Golfshot", onClick = onImport, modifier = Modifier.fillMaxWidth(), icon = Icon.DOWNLOAD)
                Spacer(Modifier.height(8.dp))
                Text("Choose both rounds.csv and holes.csv from the Golfshot export. Rounds already imported are skipped.", style = t.label.copy(color = c.muted))
            }
        }

        SectionLabel("Note reading", Modifier.padding(start = 4.dp, top = 8.dp))
        TallyCard(padding = 18.dp) {
            val onDevice = readerState == ReaderState.Ready
            val reading = when {
                cloudAvailable && cloudOn && online -> "Reading notes online"
                onDevice -> "Reading notes on this phone"
                readerState is ReaderState.Preparing -> "Getting the on-device assistant ready"
                else -> "Notes are saved, not read yet"
            }
            val detail = when {
                cloudAvailable && cloudOn && online -> "Gemini fills in your card as you write. With no signal, the phone reads them instead" + (if (onDevice) "." else ", once its on-device model is ready.")
                cloudAvailable && cloudOn -> "No signal right now, so notes are read on the phone. They're read online again once you're back in signal."
                onDevice -> "Notes are read on the phone. Turn on online reading for the most accurate cards."
                else -> "A score at the start of a note is still picked up, and every note is kept so it can be read later."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if ((cloudAvailable && cloudOn && online) || onDevice) c.accent else c.line))
                Spacer(Modifier.width(10.dp))
                Text(reading, style = t.title.copy(color = c.ink))
            }
            Spacer(Modifier.height(4.dp))
            Text(detail, style = t.label.copy(color = c.muted))
            if (cloudAvailable) {
                Spacer(Modifier.height(14.dp))
                Segmented(
                    listOf(true to "Read online", false to "Phone only"),
                    cloudOn,
                    { it?.let(onCloud) },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Online reading sends only the note's text and the hole's par to Google's Gemini. Nothing else leaves your phone.", style = t.label.copy(color = c.muted))
            }
        }

        SectionLabel("About", Modifier.padding(start = 4.dp, top = 8.dp))
        TallyCard(padding = 18.dp) {
            Text("Tally", style = t.title.copy(color = c.ink))
            Text("Course maps © OpenStreetMap contributors. GPS only runs while a hole is on screen.", style = t.label.copy(color = c.muted))
        }
    }
}

