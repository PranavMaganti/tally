package me.vanpra.tally.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.vanpra.tally.ui.theme.Tally

/** A raised block for grouping related content. Used sparingly: one card per idea. */
@Composable
fun TallyCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Tally.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Tally.type.caps.copy(color = Tally.colors.muted), modifier = modifier)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Icon? = null) {
    val c = Tally.colors
    Row(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) c.accent else c.accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TallyIcon(icon, c.onAccent, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Tally.type.title.copy(color = c.onAccent))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Tally.colors.ink, icon: Icon? = null) {
    val c = Tally.colors
    Row(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.raise)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TallyIcon(icon, color, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Tally.type.title.copy(color = color))
    }
}

/** A row of mutually exclusive options. Tapping the selected one again clears it when [allowNone]. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T?, onSelect: (T?) -> Unit, modifier: Modifier = Modifier, allowNone: Boolean = false) {
    val c = Tally.colors
    Row(modifier.clip(CircleShape).background(c.raise).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(if (on && allowNone) null else value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Tally.type.label.copy(color = if (on) c.ink else c.muted, fontSize = Tally.type.body.fontSize))
            }
        }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 15, label: String) {
    val c = Tally.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoundIconButton("−", enabled = value > min, description = "Fewer $label") { onChange(value - 1) }
        Text(value.toString(), style = Tally.type.numberLarge.copy(color = c.ink, fontSize = Tally.type.numberLarge.fontSize * 0.8f), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
        RoundIconButton("+", enabled = value < max, description = "More $label") { onChange(value + 1) }
    }
}

@Composable
private fun RoundIconButton(glyph: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    val c = Tally.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.dp, c.line, CircleShape)
            .background(c.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = Tally.type.title.copy(color = if (enabled) c.ink else c.line, fontSize = Tally.type.title.fontSize * 1.3f)) }
}

/** A labelled row inside a form card. */
@Composable
fun FieldRow(label: String, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tally.type.title.copy(color = Tally.colors.ink), modifier = Modifier.weight(1f))
        content()
    }
}

/** Simple line chart of recent scores, newest on the right, emphasised. */
@Composable
fun Sparkline(values: List<Int>, modifier: Modifier = Modifier) {
    val c = Tally.colors
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val lo = values.min() - 2; val hi = values.max() + 2
        val step = size.width / (values.size - 1)
        fun y(v: Int) = size.height - (v - lo).toFloat() / (hi - lo) * size.height
        val pts = values.mapIndexed { i, v -> Offset(i * step, y(v)) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.surface, 5.dp.toPx(), pts.last())
        drawCircle(c.accent, 3.5.dp.toPx(), pts.last())
    }
}

enum class Icon { HOME, STATS, COURSES, PROFILE, CHEVRON_DOWN, PLUS, FLAG, CARD, MAP, DOWNLOAD, MIC, BALL }

/** Line icons drawn to match the type weight, so no icon font or library is needed. */
@Composable
fun TallyIcon(icon: Icon, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) { drawIcon(icon, color) }
}

private fun DrawScope.drawIcon(icon: Icon, color: Color) {
    val s = size.minDimension / 24f
    val stroke = Stroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg xy: Float) = Path().apply { moveTo(xy[0] * s, xy[1] * s); for (i in 2 until xy.size step 2) lineTo(xy[i] * s, xy[i + 1] * s) }
    when (icon) {
        Icon.HOME -> drawPath(p(4f, 11f, 12f, 4f, 20f, 11f, 20f, 20f, 4f, 20f, 4f, 11f), color, style = stroke)
        Icon.STATS -> { drawPath(p(5f, 19f, 5f, 12f), color, style = stroke); drawPath(p(12f, 19f, 12f, 5f), color, style = stroke); drawPath(p(19f, 19f, 19f, 9f), color, style = stroke) }
        Icon.COURSES, Icon.FLAG -> { drawPath(p(7f, 21f, 7f, 3f), color, style = stroke); drawPath(p(7f, 4f, 18f, 8f, 7f, 12f), color, style = stroke) }
        Icon.PROFILE -> { drawCircle(color, 4f * s, Offset(12f * s, 8f * s), style = stroke); drawPath(p(4f, 21f, 6f, 17f, 9f, 15f, 15f, 15f, 18f, 17f, 20f, 21f), color, style = stroke) }
        Icon.CHEVRON_DOWN -> drawPath(p(6f, 9f, 12f, 15f, 18f, 9f), color, style = stroke)
        Icon.PLUS -> { drawPath(p(12f, 5f, 12f, 19f), color, style = stroke); drawPath(p(5f, 12f, 19f, 12f), color, style = stroke) }
        Icon.CARD -> { drawRoundRect(color, Offset(4f * s, 4f * s), androidx.compose.ui.geometry.Size(16f * s, 16f * s), androidx.compose.ui.geometry.CornerRadius(3f * s), style = stroke); drawPath(p(4f, 10f, 20f, 10f), color, style = stroke); drawPath(p(10f, 4f, 10f, 20f), color, style = stroke) }
        Icon.MAP -> drawPath(p(4f, 6f, 9f, 4f, 15f, 6f, 20f, 4f, 20f, 18f, 15f, 20f, 9f, 18f, 4f, 20f, 4f, 6f), color, style = stroke)
        Icon.MIC -> {
            drawRoundRect(color, Offset(9f * s, 3f * s), androidx.compose.ui.geometry.Size(6f * s, 11f * s), androidx.compose.ui.geometry.CornerRadius(3f * s))
            drawPath(Path().apply { moveTo(5.5f * s, 11f * s); cubicTo(5.5f * s, 19f * s, 18.5f * s, 19f * s, 18.5f * s, 11f * s) }, color, style = stroke)
            drawPath(p(12f, 17.5f, 12f, 21f), color, style = stroke)
        }
        Icon.BALL -> {
            drawCircle(color, 6.5f * s, Offset(12f * s, 9.5f * s), style = stroke)
            drawPath(p(12f, 16f, 12f, 21f), color, style = stroke)
            drawPath(p(8f, 21f, 16f, 21f), color, style = stroke)
        }
        Icon.DOWNLOAD -> { drawPath(p(12f, 4f, 12f, 15f), color, style = stroke); drawPath(p(7f, 10f, 12f, 15f, 17f, 10f), color, style = stroke); drawPath(p(5f, 20f, 19f, 20f), color, style = stroke) }
    }
}

enum class Tab(val label: String, val icon: Icon) { HOME("Home", Icon.HOME), STATS("Stats", Icon.STATS), COURSES("Courses", Icon.COURSES), PROFILE("Profile", Icon.PROFILE) }

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Tally.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 6.dp, bottom = 6.dp)) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Tab) { onSelect(tab) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    TallyIcon(tab.icon, if (on) c.accent else c.muted, Modifier.size(22.dp))
                    Text(tab.label, style = Tally.type.label.copy(color = if (on) c.accent else c.muted, fontSize = Tally.type.caps.fontSize))
                }
            }
        }
    }
}

/** Shown on every tab while a round is in progress: one tap back to the hole you left. */
@Composable
fun ResumeBar(title: String, subtitle: String, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tally.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.ink)
            .clickable(role = Role.Button, onClick = onResume)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        Column(Modifier.weight(1f)) {
            Text(title, style = Tally.type.title.copy(color = c.bg))
            Text(subtitle, style = Tally.type.label.copy(color = c.bg.copy(alpha = 0.7f)))
        }
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Resume", style = Tally.type.label.copy(color = c.onAccent, fontSize = Tally.type.body.fontSize))
        }
    }
}

/** Score shapes from a paper card: circle for under par, square for over, double square for double bogey or worse. */
@Composable
fun ScoreMark(strokes: Int?, par: Int?, modifier: Modifier = Modifier, size: Dp = 30.dp, highlight: Boolean = false, showNumber: Boolean = true, noScore: Boolean = false) {
    val c = Tally.colors
    val diff = if (strokes != null && par != null) strokes - par else null
    val colour = when {
        diff == null -> c.muted
        diff < 0 -> c.accent
        diff >= 2 -> c.warn
        else -> c.ink
    }
    Box(
        modifier
            .size(size)
            .then(if (highlight) Modifier.clip(RoundedCornerShape(8.dp)).background(c.raise) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size - 4.dp)) {
            val w = 1.5.dp.toPx()
            when {
                diff == null -> Unit
                diff <= -2 -> { drawCircle(colour, this.size.minDimension / 2 - w, style = Stroke(w)); drawCircle(colour, this.size.minDimension / 2 - w * 3, style = Stroke(w)) }
                diff == -1 -> drawCircle(colour, this.size.minDimension / 2 - w, style = Stroke(w))
                diff == 1 -> drawRect(colour, Offset(w, w), androidx.compose.ui.geometry.Size(this.size.width - 2 * w, this.size.height - 2 * w), style = Stroke(w))
                diff >= 2 -> {
                    drawRect(colour, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w), style = Stroke(w))
                    drawRect(colour, Offset(w * 2.5f, w * 2.5f), androidx.compose.ui.geometry.Size(this.size.width - w * 5, this.size.height - w * 5), style = Stroke(w))
                }
            }
        }
        if (showNumber) Text(strokes?.toString() ?: if (noScore) "–" else "·", style = Tally.type.numberMedium.copy(color = colour))
    }
}
