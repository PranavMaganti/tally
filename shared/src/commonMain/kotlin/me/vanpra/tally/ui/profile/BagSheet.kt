package me.vanpra.tally.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.vanpra.tally.round.Bag
import me.vanpra.tally.round.BagClubs
import me.vanpra.tally.round.ClubDistance
import me.vanpra.tally.round.YardageSource
import me.vanpra.tally.ui.components.SectionLabel
import me.vanpra.tally.ui.components.TallyCard
import me.vanpra.tally.ui.theme.Tally

/** The Profile card: what's in the bag at a glance, tap to change it. */
@Composable
internal fun BagCard(bag: Bag, tracked: List<ClubDistance>, onOpen: () -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    val yours = bag.clubs.count { it in bag.yards }
    TallyCard(Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onOpen), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${bag.clubs.size + 1} clubs", style = t.title.copy(color = c.ink, fontSize = t.title.fontSize * 1.2f))
                Text(
                    bag.clubs.joinToString(" · ") + " · Putter",
                    style = t.label.copy(color = c.muted),
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text("Edit", style = t.label.copy(color = c.ink), modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.raise).padding(horizontal = 12.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                yours == 0 && tracked.isEmpty() -> "Add your yardages so the caddie knows your game. Until then it uses typical distances."
                else -> listOfNotNull(
                    if (yours == 0) "No yardages of your own yet" else "Your yardage for $yours ${if (yours == 1) "club" else "clubs"}",
                    tracked.size.takeIf { it > 0 }?.let { "$it measured from shots you marked" },
                ).joinToString(" · ") + "."
            },
            style = t.label.copy(color = c.muted),
        )
    }
}

/** Choose your clubs, then say how far each one goes. Saved as you go. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BagSheet(bag: Bag, tracked: List<ClubDistance>, onToggle: (String) -> Unit, onYards: (String, Int?) -> Unit, onDismiss: () -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    val focus = LocalFocusManager.current
    val measured = remember(tracked) { tracked.associateBy { it.club } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("My bag", style = t.holeNumber.copy(color = c.ink, fontSize = t.holeNumber.fontSize * 0.7f))

            SectionLabel("Clubs you carry", Modifier.padding(start = 4.dp))
            TallyCard(padding = 14.dp) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BagClubs.ALL.forEach { club ->
                        val on = club in bag.clubs
                        Text(
                            club,
                            style = t.label.copy(color = if (on) c.bg else c.ink),
                            modifier = Modifier.clip(CircleShape).background(if (on) c.ink else c.raise)
                                .clickable(role = Role.Checkbox) { onToggle(club) }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                        )
                    }
                    Text("Putter", style = t.label.copy(color = c.bg), modifier = Modifier.clip(CircleShape).background(c.ink.copy(alpha = 0.5f)).padding(horizontal = 13.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${bag.clubs.size + 1} clubs" + if (bag.clubs.size + 1 > 14) ". The rules allow 14 in a competition." else ".",
                    style = t.label.copy(color = if (bag.clubs.size + 1 > 14) c.warn else c.muted),
                )
            }

            SectionLabel("How far you hit them", Modifier.padding(start = 4.dp, top = 4.dp))
            TallyCard(padding = 14.dp) {
                Text("Your usual total distance in yards, carry and roll, on a normal swing. Leave one blank to use a typical distance.", style = t.label.copy(color = c.muted))
                Spacer(Modifier.height(6.dp))
                bag.clubs.forEachIndexed { i, club ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    val m = measured[club]
                    val use = bag.distanceFor(club, m)
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(club, style = t.title.copy(color = c.ink), modifier = Modifier.width(64.dp))
                        Column(Modifier.weight(1f)) {
                            if (m != null) Text("Measured ${m.typicalYards} yd · ${m.shots} shot${if (m.shots == 1) "" else "s"}", style = t.label.copy(color = c.accent))
                            if (use.source == YardageSource.TRACKED) Text("Caddie uses what's measured", style = t.label.copy(color = c.muted))
                        }
                        YardsField(
                            value = bag.yards[club],
                            placeholder = BagClubs.TYPICAL[club],
                            onChange = { onYards(club, it) },
                            onDone = { focus.clearFocus() },
                            label = "$club yards",
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("yd", style = t.label.copy(color = c.muted))
                    }
                }
            }
            Text(
                "Once you've marked 5 shots with a club, the caddie goes by those instead.",
                style = t.label.copy(color = c.muted),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun YardsField(value: Int?, placeholder: Int?, onChange: (Int?) -> Unit, onDone: () -> Unit, label: String) {
    val c = Tally.colors
    val t = Tally.type
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val style = t.numberMedium.copy(color = c.ink, textAlign = TextAlign.Center)
    BasicTextField(
        value = text,
        onValueChange = { s ->
            val digits = s.filter(Char::isDigit).take(3)
            text = digits
            onChange(digits.toIntOrNull())
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.width(72.dp).semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty() && placeholder != null) Text(placeholder.toString(), style = style.copy(color = c.muted.copy(alpha = 0.6f)))
                inner()
            }
        },
    )
}
