package me.vanpra.tally.ui.round

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlin.math.abs
import me.vanpra.tally.location.smoothed
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import me.vanpra.tally.platform.currentTimeMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.vanpra.tally.ai.NoteReader
import me.vanpra.tally.ai.VoiceInput
import me.vanpra.tally.course.Course
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.HoleDistances
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.METRES_TO_YARDS
import me.vanpra.tally.course.distanceMetres
import me.vanpra.tally.location.LocationFix
import me.vanpra.tally.location.LocationSource
import me.vanpra.tally.platform.PlatformBackHandler
import me.vanpra.tally.round.HoleScore
import me.vanpra.tally.round.ShotMark
import me.vanpra.tally.round.ShotTrack
import me.vanpra.tally.round.CardCounts
import me.vanpra.tally.round.Round
import me.vanpra.tally.round.formatToPar
import me.vanpra.tally.round.holeAverage
import me.vanpra.tally.round.scoreName
import me.vanpra.tally.ui.components.Icon
import me.vanpra.tally.ui.components.PrimaryButton
import me.vanpra.tally.ui.components.SecondaryButton
import me.vanpra.tally.ui.components.TallyIcon
import me.vanpra.tally.ui.theme.Tally
import kotlin.math.roundToInt

/** GPS fixes worse than this are ignored rather than shown as a jumpy position. */
private const val MAX_ACCURACY_M = 25.0
/** Further than this from the hole and we assume you're not on the course: simulate instead. */
private const val ON_COURSE_RADIUS_M = 1500.0
/** How far beyond a hole's length you can be before we assume you're not playing it yet. */
private const val AWAY_MARGIN_M = 120.0

private enum class RoundView { HOLE, CARD }

/**
 * Round mode: full screen, no app tabs. Swipe between holes, switch to the card, log each hole,
 * minimise to browse the app (the round keeps going) or finish it.
 */
@Composable
fun RoundScreen(
    course: Course,
    round: Round,
    history: List<Round>,
    location: LocationSource,
    onSaveHole: (HoleScore) -> Unit,
    /** You marked your ball on a hole: saved at once, so it survives the app closing. */
    onMarkShot: (hole: Int, ShotMark) -> Unit,
    onUndoMark: (hole: Int) -> Unit,
    noteReader: NoteReader,
    voice: VoiceInput,
    /** Unsaved hole entries by hole number. Owned by the caller so they survive minimising the round. */
    drafts: MutableMap<Int, HoleDraft>,
    onMinimise: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    val count = course.holes.size
    var holeIndex by rememberSaveable(round.id) {
        mutableIntStateOf(course.holes.indexOfFirst { round.hole(it.number)?.isDone != true }.takeIf { it >= 0 } ?: 0)
    }
    var direction by remember { mutableIntStateOf(1) }
    var view by rememberSaveable { mutableStateOf(RoundView.HOLE) }
    var entryHole by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val previous = { direction = -1; holeIndex = (holeIndex - 1 + count) % count }
    val next = { direction = 1; holeIndex = (holeIndex + 1) % count }

    // The entry sheet handles its own back press; one press should never also minimise the round.
    PlatformBackHandler(enabled = entryHole == null && !confirmFinish && !confirmDiscard) {
        if (view == RoundView.CARD) view = RoundView.HOLE else onMinimise()
    }

    // Collected only while the screen is visible: GPS switches off with the screen.
    // Steadied: raw GPS wanders a few metres a second even standing still.
    val fixFlow = remember(location) { location.fixes().smoothed() }
    val fix: LocationFix? by fixFlow.collectAsStateWithLifecycle(initialValue = null)

    val hole = course.holes[holeIndex]
    val c = Tally.colors
    // Measured once and then held still, so switching Hole/Card (which animates the header) doesn't
    // re-fit the map or re-lay out the card on every frame.
    var headerHeight by remember { mutableIntStateOf(0) }
    var topRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        AnimatedContent(
            targetState = view,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier.fillMaxSize(),
            label = "view",
        ) { v ->
            when (v) {
                RoundView.HOLE -> AnimatedContent(
                    targetState = holeIndex,
                    transitionSpec = {
                        slideInHorizontally(tween(220)) { w -> direction * w } togetherWith slideOutHorizontally(tween(220)) { w -> -direction * w }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "hole",
                ) { index ->
                    val h = course.holes[index]
                    HolePage(
                        course = course,
                        hole = h,
                        score = round.hole(h.number),
                        fix = fix,
                        headerHeight = headerHeight,
                        onPrevious = previous,
                        onNext = next,
                        hasDraft = drafts.containsKey(h.number),
                        onEnterScore = { entryHole = h.number },
                        onMark = { onMarkShot(h.number, it) },
                        onUndoMark = { onUndoMark(h.number) },
                    )
                }
                RoundView.CARD -> RoundCard(
                    course = course,
                    round = round,
                    currentHole = hole.number,
                    topPadding = with(density) { (statusBarTop + topRowHeight).toDp() + 18.dp },
                    onHole = { n -> entryHole = n },
                    onBackToHole = { view = RoundView.HOLE },
                    onFinish = { confirmFinish = true },
                    onDiscard = { confirmDiscard = true },
                )
            }
        }

        RoundHeader(
            hole = hole,
            round = round,
            view = view,
            onView = { view = it },
            onMinimise = onMinimise,
            onPrevious = previous,
            onNext = next,
            onTopRowHeight = { topRowHeight = it },
            modifier = Modifier.onSizeChanged { if (view == RoundView.HOLE && it.height > headerHeight) headerHeight = it.height }.then(if (view == RoundView.HOLE) Modifier.swipeBetweenHoles(previous, next) else Modifier),
        )
    }

    entryHole?.let { number ->
        val courseHole = course.holes.first { it.number == number }
        HoleEntrySheet(
            hole = courseHole,
            existing = round.hole(number),
            average = remember(history, number) { holeAverage(history, course.name, number) },
            reader = noteReader,
            voice = voice,
            draft = drafts[number],
            onDismiss = { d -> if (d == null) drafts.remove(number) else drafts[number] = d; entryHole = null },
            onSave = { saved ->
                drafts.remove(number)
                onSaveHole(saved)
                entryHole = null
                val allDone = course.holes.all { h -> h.number == number || round.hole(h.number)?.isDone == true }
                when {
                    allDone -> view = RoundView.CARD
                    view == RoundView.HOLE && number == hole.number && holeIndex < count - 1 -> next()
                    view == RoundView.CARD -> holeIndex = course.holes.indexOfFirst { it.number == number }.coerceAtLeast(0)
                }
            },
        )
    }

    if (confirmDiscard) {
        val played = round.played.size
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this round?", style = Tally.type.title.copy(color = c.ink, fontSize = Tally.type.title.fontSize * 1.25f)) },
            text = {
                Text(
                    if (played == 0) "Nothing has been scored yet." else "The ${if (played == 1) "hole" else "$played holes"} you've scored and their notes won't be saved. This can't be undone.",
                    style = Tally.type.body.copy(color = c.muted),
                )
            },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard", style = Tally.type.title.copy(color = c.warn)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep playing", style = Tally.type.title.copy(color = c.muted)) } },
            containerColor = c.surface,
        )
    }

    if (confirmFinish) {
        val played = round.done.size
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish this round?", style = Tally.type.title.copy(color = c.ink, fontSize = Tally.type.title.fontSize * 1.25f)) },
            text = {
                Text(
                    when {
                        played == 0 -> "No holes have a score yet, so nothing will be saved."
                        played < count -> "You've done $played of $count holes. Holes without a score won't count."
                        else -> "All $count holes are scored. The round will go into your history and stats."
                    },
                    style = Tally.type.body.copy(color = c.muted),
                )
            },
            confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text("Finish round", style = Tally.type.title.copy(color = c.accent)) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep playing", style = Tally.type.title.copy(color = c.muted)) } },
            containerColor = c.surface,
        )
    }
}

/** One hole's map and yardage sheet. Target and simulated position live here, so each hole keeps its own. */
@Composable
private fun HolePage(
    course: Course,
    hole: Hole,
    score: HoleScore?,
    fix: LocationFix?,
    headerHeight: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasDraft: Boolean,
    onEnterScore: () -> Unit,
    onMark: (ShotMark) -> Unit,
    onUndoMark: () -> Unit,
) {
    val distances = remember(hole) { HoleDistances(hole) }
    val greenMiddle = distances.greenMiddle ?: hole.line.last()
    val usableFix = fix?.takeIf { it.accuracyMetres <= MAX_ACCURACY_M }
    val onCourse = usableFix?.takeIf { distanceMetres(it.position, greenMiddle) < ON_COURSE_RADIUS_M }
    // Looking ahead at a hole you haven't walked to yet: show it from its tee instead of from where you stand.
    val awayFromHole = onCourse != null && distanceMetres(onCourse.position, greenMiddle) > distances.lengthMetres + AWAY_MARGIN_M
    val live = onCourse.takeIf { !awayFromHole }

    var simulated by remember { mutableStateOf(hole.line.first()) }
    var anchor by remember { mutableStateOf(hole.line.first()) }
    var target by remember { mutableStateOf<LatLng?>(null) }
    val player = live?.position ?: simulated

    val green = remember(distances, player) { distances.green(player) }
    val marks = score?.shots.orEmpty()
    val shots = remember(hole, score) { ShotTrack.shots(hole, marks, score?.teeClub, score?.let(CardCounts::of), score?.notes?.lastOrNull()?.text) }
    val lastStart = remember(hole, marks) { ShotTrack.starts(hole, marks).last() }
    val hazards = remember(distances, player) { distances.hazardsAhead(player) }

    val c = Tally.colors
    var sheetHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(c.bg)) {
        HoleMap(
            course = course,
            distances = distances,
            anchor = live?.position ?: anchor,
            player = player,
            target = target,
            hazards = hazards,
            shots = shots,
            canMovePlayer = live == null,
            onTargetChange = { target = it },
            onPlayerMove = { simulated = it },
            onPlayerMoveEnd = { anchor = simulated },
            // Pixel room for the flag above the green and your dot above the sheet, on every hole length.
            topInset = headerHeight + with(density) { 36.dp.toPx() },
            bottomInset = sheetHeight + with(density) { 28.dp.toPx() },
            modifier = Modifier.fillMaxSize(),
        )
        // Just marked: what was saved, with a way to take it back.
        var marked by remember { mutableStateOf<String?>(null) }
        var markStamp by remember { mutableIntStateOf(0) }
        LaunchedEffect(markStamp) { if (markStamp > 0) { delay(5_000); marked = null } }
        MarkedConfirmation(
            text = marked,
            onUndo = { onUndoMark(); marked = null },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = with(density) { sheetHeight.toDp() } + 12.dp),
        )
        // Every row in the sheet has a fixed height, so its size (and therefore the map's framing) never changes.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { sheetHeight = it.height }
                .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .swipeBetweenHoles(onPrevious, onNext)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GreenNumbers(green?.front, green?.middle, green?.back, steady = live != null)
            StatusRow(
                gpsOn = live != null,
                text = when {
                    live != null -> "GPS ±${live.accuracyMetres.roundToInt()} m"
                    awayFromHole && onCourse != null -> "You're ${yd(distanceMetres(onCourse.position, hole.line.first()))} yd from this tee. Showing distances from the tee."
                    fix != null && usableFix == null -> "Weak GPS signal. Drag the blue dot meanwhile."
                    usableFix != null -> "${(distanceMetres(usableFix.position, greenMiddle) / 1000).roundToInt()} km from the course. Drag the blue dot to simulate."
                    else -> "Finding GPS… Drag the blue dot to simulate."
                },
                action = when {
                    live == null && (simulated != hole.line.first() || target != null) -> "Back to tee"
                    live != null && target != null -> "Clear"
                    else -> null
                },
                onAction = { simulated = hole.line.first(); anchor = simulated; target = null },
            ) {
                MarkBallButton(hole, player, lastStart, shotsMarked = shots.count { !it.inferred }, onMark = { mark, said -> onMark(mark); marked = said; markStamp++ })
            }
            val s = score?.strokes
            if (score?.pickedUp == true && s == null) {
                SecondaryButton("Hole ${hole.number}: no score  ·  Edit", onClick = onEnterScore, modifier = Modifier.fillMaxWidth())
            } else if (s == null) {
                PrimaryButton(if (hasDraft) "Finish scoring hole ${hole.number}" else "Score hole ${hole.number}", onClick = onEnterScore, modifier = Modifier.fillMaxWidth(), icon = Icon.PLUS)
            } else {
                SecondaryButton(
                    "Hole ${hole.number}: $s" + (hole.par?.let { " · ${scoreName(s, it)}" } ?: "") + "  ·  Edit",
                    onClick = onEnterScore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Horizontal swipe: left for the next hole, right for the previous one. */
internal fun Modifier.swipeBetweenHoles(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        val threshold = 56.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onHorizontalDrag = { change, delta -> total += delta; change.consume() },
            onDragEnd = { if (total < -threshold) onNext() else if (total > threshold) onPrevious() },
        )
    },
)

@Composable
private fun RoundHeader(
    hole: Hole, round: Round, view: RoundView, onView: (RoundView) -> Unit,
    onMinimise: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onTopRowHeight: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val c = Tally.colors
    val lengthYards = remember(hole) { (HoleDistances(hole).lengthMetres * METRES_TO_YARDS).roundToInt() }
    val played = round.done.size
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to c.bg, 0.72f to c.bg.copy(alpha = 0.94f), 1f to c.bg.copy(alpha = 0f)))
            .statusBarsPadding()
            .padding(start = 12.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top row is the round: minimise, Hole/Card, score. The hole title below only belongs to the Hole view.
        Row(Modifier.fillMaxWidth().onSizeChanged { onTopRowHeight(it.height) }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(56.dp)) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(c.raise).clickable(role = Role.Button, onClick = onMinimise)
                        .semantics { contentDescription = "Minimise round" },
                    contentAlignment = Alignment.Center,
                ) { TallyIcon(Icon.CHEVRON_DOWN, c.ink, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).background(c.raise).padding(3.dp)) {
                listOf(RoundView.HOLE to "Hole", RoundView.CARD to "Card").forEach { (v, label) ->
                    val on = v == view
                    Box(
                        Modifier.clip(CircleShape).background(if (on) c.ink else c.raise).clickable(role = Role.Tab) { onView(v) }
                            .padding(horizontal = 22.dp, vertical = 7.dp),
                    ) { Text(label, style = Tally.type.label.copy(color = if (on) c.bg else c.muted)) }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
                Text(if (played == 0) "–" else formatToPar(round.toPar), style = Tally.type.numberLarge.copy(color = c.ink, fontSize = Tally.type.numberLarge.fontSize * 0.75f))
                Text(if (played == 0) "to par" else "thru $played", style = Tally.type.label.copy(color = c.muted, fontSize = Tally.type.caps.fontSize))
            }
        }
        AnimatedVisibility(visible = view == RoundView.HOLE, enter = fadeIn(tween(180)) + expandVertically(tween(200)), exit = fadeOut(tween(140)) + shrinkVertically(tween(200))) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Arrow("‹", "Previous hole", onPrevious)
                Text(hole.number.toString(), style = Tally.type.holeNumber.copy(color = c.ink), modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                Column(Modifier.width(60.dp)) {
                    Text("Par ${hole.par ?: "–"}", style = Tally.type.title.copy(color = c.ink))
                    Text("$lengthYards yd", style = Tally.type.label.copy(color = c.muted))
                }
                Arrow("›", "Next hole", onNext)
            }
        }
    }
}

@Composable
private fun Arrow(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Tally.type.holeNumber.copy(color = Tally.colors.muted, fontSize = Tally.type.title.fontSize * 2))
    }
}

@Composable
private fun GreenNumbers(front: Double?, middle: Double?, back: Double?, steady: Boolean) {
    val c = Tally.colors
    val t = Tally.type
    val f = steadyYards(front, steady)
    val m = steadyYards(middle, steady)
    val b = steadyYards(back, steady)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(f ?: "–", style = t.numberLarge.copy(color = c.ink))
            Text("FRONT", style = t.caps.copy(color = c.muted))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m ?: "–", style = t.numberHero.copy(color = c.ink))
            Text("MIDDLE", style = t.caps.copy(color = c.accent))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(b ?: "–", style = t.numberLarge.copy(color = c.ink))
            Text("BACK", style = t.caps.copy(color = c.muted))
        }
    }
}

/**
 * Yards to show, held steady while GPS is live: a change of less than 2 yards from what's on screen is
 * ignored, so the number doesn't flicker between 151 and 152 as you stand over the ball.
 */
@Composable
private fun steadyYards(metres: Double?, steady: Boolean): String? {
    val now = metres?.let { (it * METRES_TO_YARDS).roundToInt() }
    val shown = remember { mutableStateOf<Int?>(null) }
    val previous = shown.value
    val display = if (steady && now != null && previous != null && abs(now - previous) < 2) previous else now
    SideEffect { shown.value = display }
    return display?.toString()
}

@Composable
private fun StatusRow(gpsOn: Boolean, text: String, action: String?, onAction: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = Tally.colors
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (gpsOn) c.accent else c.muted))
        Text(text, style = Tally.type.label.copy(color = c.muted), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = Tally.type.label.copy(color = c.ink),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.raise).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        trailing()
    }
}

private fun yd(metres: Double) = (metres * METRES_TO_YARDS).roundToInt().toString()

/** Below this, you haven't moved from where the last shot started: no distance to show yet. */
private const val MIN_SHOT_M = 9.0

/**
 * Standing at your ball, tap to mark it. Shows how far you've come since the last shot started, so
 * walking up to your drive tells you how far it went; a mark on the tee starts the hole from there.
 */
@Composable
private fun MarkBallButton(hole: Hole, player: LatLng, lastStart: LatLng, shotsMarked: Int, onMark: (ShotMark, String) -> Unit) {
    val c = Tally.colors
    val haptics = LocalHapticFeedback.current
    val sinceLast = distanceMetres(lastStart, player)
    val onTee = ShotTrack.isTee(hole, player)
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.ink)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onMark(ShotMark(player, currentTimeMillis()), if (onTee) "Tee marked" else "Shot ${shotsMarked + 1} · ${yd(sinceLast)} yd")
            }
            .semantics { contentDescription = "Mark your ball here" }
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TallyIcon(Icon.BALL, c.bg, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Mark ball", style = Tally.type.label.copy(color = c.bg))
        if (!onTee && sinceLast >= MIN_SHOT_M) {
            Spacer(Modifier.width(6.dp))
            Text("${yd(sinceLast)} yd", style = Tally.type.label.copy(color = c.bg.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun MarkedConfirmation(text: String?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tally.colors
    AnimatedVisibility(visible = text != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(220)), modifier = modifier) {
        Row(
            Modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(c.ink).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text.orEmpty(), style = Tally.type.label.copy(color = c.bg))
            Spacer(Modifier.width(6.dp))
            Text(
                "Undo",
                style = Tally.type.label.copy(color = c.accent),
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
