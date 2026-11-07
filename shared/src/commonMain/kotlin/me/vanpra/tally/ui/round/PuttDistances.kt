package me.vanpra.tally.ui.round

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import me.vanpra.tally.round.HoleEntryForm
import me.vanpra.tally.round.Origin
import me.vanpra.tally.round.PuttMiss
import me.vanpra.tally.ui.theme.Tally
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Where each putt started, under Putts. Folded to one line by default ("20 ft → 8 ft → 2 ft", or
 * "Add putt distances"); open, it's a putting line with the hole on the right and one ball per putt to
 * slide (or tap the line to move the nearest ball there). The scale is spread out near the hole, where a
 * few feet matter most. Entirely optional.
 */
@Composable
internal fun PuttDistances(form: HoleEntryForm, color: Color, onChange: (HoleEntryForm) -> Unit) {
    if (form.putts.value < 1) return
    val c = Tally.colors
    val t = Tally.type
    var open by remember { mutableStateOf(false) }
    val feet = form.puttFeet
    val known = feet.any { it != null }
    val miss = form.puttMiss.value?.takeIf { form.showsMiss }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { open = !open }
            .semantics { contentDescription = if (open) "Close putt distances" else "Putt distances" }
            .padding(top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (known) feet.joinToString("  →  ") { it?.let { f -> "$f ft" } ?: "?" } + (miss?.let { "  ·  ${missLabel(it).lowercase()}" } ?: "")
            else "Add putt distances",
            style = t.label.copy(color = if (known) c.ink else c.muted),
            modifier = Modifier.weight(1f),
        )
        Text(if (open) "Done" else "›", style = t.title.copy(color = if (open) color else c.muted))
    }

    AnimatedVisibility(open, enter = fadeIn(tween(160)) + expandVertically(tween(200)), exit = fadeOut(tween(120)) + shrinkVertically(tween(180))) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PuttLine(feet, color) { i, f -> onChange(form.tapPuttFeet(i, f)) }
            if (form.showsMiss) {
                Text("First putt missed", style = t.caps.copy(color = c.muted))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PuttMiss.entries.forEach { m ->
                        val on = form.puttMiss.value == m
                        Text(
                            missLabel(m),
                            style = t.label.copy(color = if (on) c.onAccent else c.ink),
                            modifier = Modifier.clip(CircleShape).background(if (on) color else c.raise)
                                .clickable(role = Role.RadioButton) { onChange(form.tapPuttMiss(if (on) null else m)) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slide each ball to where that putt started.", style = t.label.copy(color = c.muted), modifier = Modifier.weight(1f))
                if (known || miss != null) Text(
                    "Clear",
                    style = t.label.copy(color = c.warn),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onChange(form.clearPuttFeet().tapPuttMiss(null)) }.padding(6.dp),
                )
            }
        }
    }
}

private fun missLabel(m: PuttMiss) = when (m) {
    PuttMiss.SHORT -> "Short"; PuttMiss.LONG -> "Long"; PuttMiss.LEFT -> "Left"; PuttMiss.RIGHT -> "Right"
    PuttMiss.LOW -> "Low"; PuttMiss.HIGH -> "High"; PuttMiss.LIPPED -> "Lipped out"
}

private const val MAX_FT = 60
private val TICKS = listOf(3, 6, 10, 20, 40)

/** Where an unset ball sits until you move it. */
private fun suggested(count: Int, i: Int): Int = when (count) {
    1 -> 8
    2 -> listOf(20, 3)[i]
    3 -> listOf(30, 8, 2)[i]
    else -> listOf(40, 12, 5, 2, 1, 1).getOrElse(i) { 1 }
}

@Composable
private fun PuttLine(feet: List<Int?>, color: Color, onSet: (Int, Int) -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(feet)
    val set by rememberUpdatedState(onSet)
    var dragging by remember { mutableIntStateOf(-1) }

    Canvas(
        Modifier.fillMaxWidth().height(100.dp)
            .semantics { contentDescription = "Putting line: " + feet.mapIndexed { i, f -> "putt ${i + 1} ${f?.let { "$it feet" } ?: "not set"}" }.joinToString(", ") }
            .pointerInput(Unit) {
                val pad = 16.dp.toPx(); val right = size.width - pad - 10.dp.toPx(); val left = pad
                fun feetAt(x: Float): Int {
                    val r = ((right - x) / (right - left)).coerceIn(0f, 1f)
                    return (r * r * MAX_FT).roundToInt().coerceIn(1, MAX_FT)
                }
                fun xOf(i: Int) = right - sqrt((current[i] ?: suggested(current.size, i)).toFloat() / MAX_FT) * (right - left)
                fun nearest(x: Float) = current.indices.minByOrNull { abs(xOf(it) - x) } ?: -1
                fun move(i: Int, x: Float) {
                    // Each putt starts no nearer the hole than the next, and no farther than the one before.
                    val before = (0 until i).mapNotNull { current[it] }.minOrNull() ?: MAX_FT
                    val after = (i + 1 until current.size).mapNotNull { current[it] }.maxOrNull() ?: 1
                    val f = feetAt(x).coerceIn(after.coerceAtMost(before), before)
                    if (f != current[i]) { set(i, f); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
                detectTapGestures { p -> nearest(p.x).takeIf { it >= 0 }?.let { move(it, p.x) } }
            }
            .pointerInput(Unit) {
                val pad = 16.dp.toPx(); val right = size.width - pad - 10.dp.toPx(); val left = pad
                fun feetAt(x: Float): Int {
                    val r = ((right - x) / (right - left)).coerceIn(0f, 1f)
                    return (r * r * MAX_FT).roundToInt().coerceIn(1, MAX_FT)
                }
                fun xOf(i: Int) = right - sqrt((current[i] ?: suggested(current.size, i)).toFloat() / MAX_FT) * (right - left)
                detectHorizontalDragGestures(
                    onDragStart = { p -> dragging = current.indices.minByOrNull { abs(xOf(it) - p.x) } ?: -1 },
                    onDragEnd = { dragging = -1 },
                    onDragCancel = { dragging = -1 },
                ) { change, _ ->
                    val i = dragging
                    if (i < 0) return@detectHorizontalDragGestures
                    change.consume()
                    val before = (0 until i).mapNotNull { current[it] }.minOrNull() ?: MAX_FT
                    val after = (i + 1 until current.size).mapNotNull { current[it] }.maxOrNull() ?: 1
                    val f = feetAt(change.position.x).coerceIn(after.coerceAtMost(before), before)
                    if (f != current[i]) { set(i, f); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                }
            },
    ) {
        val pad = 16.dp.toPx(); val right = size.width - pad - 10.dp.toPx(); val left = pad
        val y = 60.dp.toPx()
        val half = 17.dp.toPx()
        fun x(f: Int) = right - sqrt(f.toFloat() / MAX_FT) * (right - left)
        fun spot(i: Int) = x(feet[i] ?: suggested(feet.size, i))

        // The green: a soft strip running up to the cup, with faint distance marks across it.
        drawRoundRect(
            c.mapGreen.copy(alpha = 0.22f),
            topLeft = Offset(left - 12.dp.toPx(), y - half),
            size = Size(right - left + 30.dp.toPx(), half * 2),
            cornerRadius = CornerRadius(half, half),
        )
        TICKS.forEach { f ->
            val tx = x(f)
            drawLine(c.ink.copy(alpha = 0.07f), Offset(tx, y - half + 5.dp.toPx()), Offset(tx, y + half - 5.dp.toPx()), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(if (f == TICKS.last()) "$f ft" else "$f", t.caps.copy(color = c.muted))
            drawText(label, topLeft = Offset(tx - label.size.width / 2f, y + half + 4.dp.toPx()))
        }

        // The cup and flag.
        val cup = Offset(right + 4.dp.toPx(), y)
        drawCircle(c.ink.copy(alpha = 0.12f), 9.dp.toPx(), cup)
        drawCircle(c.ink, 6.dp.toPx(), cup)
        val top = Offset(cup.x, y - 40.dp.toPx())
        drawLine(c.ink, cup, top, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(top.x, top.y); lineTo(top.x + 13.dp.toPx(), top.y + 5.dp.toPx()); lineTo(top.x, top.y + 10.dp.toPx()); close() }, c.flag)

        // Each putt as a hop: from where it started to where the next one did, the last one into the cup.
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        feet.indices.forEach { i ->
            val from = spot(i)
            val to = if (i == feet.lastIndex) cup.x else spot(i + 1)
            if (to - from < 4.dp.toPx()) return@forEach
            val lift = (10 + (to - from) / size.width * 40).dp.toPx().coerceAtMost(26.dp.toPx())
            val placed = feet[i] != null && (i == feet.lastIndex || feet[i + 1] != null)
            drawPath(
                Path().apply { moveTo(from, y); quadraticTo((from + to) / 2, y - lift * 2, to, y) },
                color.copy(alpha = if (placed) 0.75f else 0.3f),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash),
            )
        }

        // The balls: white golf balls ringed in the putts colour; faint until placed.
        feet.indices.reversed().forEach { i ->
            val f = feet[i]
            val center = Offset(spot(i), y)
            val r = (if (i == dragging) 13 else 11).dp.toPx()
            if (f != null) {
                drawCircle(Color.Black.copy(alpha = 0.14f), r, center + Offset(0f, 1.5.dp.toPx()))
                drawCircle(Color.White, r, center)
                drawCircle(color, r, center, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(color.copy(alpha = 0.45f), r, center, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            }
            val n = measurer.measure("${i + 1}", t.caps.copy(color = if (f != null) color else color.copy(alpha = 0.6f)))
            drawText(n, topLeft = Offset(center.x - n.size.width / 2f, y - n.size.height / 2f))
        }

        // Distance pills above the placed balls, staggered when they'd touch.
        var lastRight = Float.NEGATIVE_INFINITY
        var raised = false
        feet.indices.forEach { i ->
            val f = feet[i] ?: return@forEach
            val text = measurer.measure("$f ft", t.caps.copy(color = c.onAccent))
            val w = text.size.width + 12.dp.toPx(); val h = text.size.height + 6.dp.toPx()
            val cx = spot(i).coerceIn(left + w / 2, right - w / 2)
            raised = cx - w / 2 < lastRight + 4.dp.toPx() && !raised
            val pillTop = y - half - h - (if (raised) h + 4.dp.toPx() else 4.dp.toPx())
            drawRoundRect(if (i == dragging) c.ink else color, Offset(cx - w / 2, pillTop), Size(w, h), CornerRadius(h / 2, h / 2))
            drawText(text, topLeft = Offset(cx - text.size.width / 2f, pillTop + 3.dp.toPx()))
            lastRight = cx + w / 2
        }
    }
}
