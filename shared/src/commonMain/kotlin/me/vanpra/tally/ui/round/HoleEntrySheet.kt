package me.vanpra.tally.ui.round

import androidx.compose.animation.AnimatedContent
import me.vanpra.tally.ai.NoteRead
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import me.vanpra.tally.ai.NoteSpan
import me.vanpra.tally.ai.NoteEvidence
import me.vanpra.tally.round.PuttMiss
import me.vanpra.tally.round.ReadBy
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import me.vanpra.tally.ai.VoiceEvent
import me.vanpra.tally.ai.VoiceFailure
import me.vanpra.tally.ai.VoiceInput
import me.vanpra.tally.ui.components.Icon
import me.vanpra.tally.ui.components.TallyIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import me.vanpra.tally.round.ShotTrack
import me.vanpra.tally.round.CardCounts
import me.vanpra.tally.round.BagClubs
import me.vanpra.tally.round.TrackedShot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import me.vanpra.tally.ai.Clubs
import me.vanpra.tally.ai.HoleContext
import me.vanpra.tally.ai.NotePrompt
import me.vanpra.tally.ai.NoteReader
import me.vanpra.tally.ai.NoteReading
import me.vanpra.tally.ai.ReaderState
import me.vanpra.tally.course.Hole
import me.vanpra.tally.course.HoleDistances
import me.vanpra.tally.course.METRES_TO_YARDS
import me.vanpra.tally.platform.currentTimeMillis
import me.vanpra.tally.round.Field
import me.vanpra.tally.round.HoleEntryForm
import me.vanpra.tally.round.HoleNote
import me.vanpra.tally.round.HoleScore
import me.vanpra.tally.round.NoteSource
import me.vanpra.tally.round.Origin
import me.vanpra.tally.round.TeeShot
import me.vanpra.tally.round.oneDecimal
import me.vanpra.tally.round.scoreName
import me.vanpra.tally.ui.components.PrimaryButton
import me.vanpra.tally.ui.components.Segmented
import me.vanpra.tally.ui.components.Stepper
import me.vanpra.tally.ui.theme.Tally
import kotlin.math.roundToInt

/** What you'd entered when the sheet was closed without saving, so nothing typed is ever lost. */
data class HoleDraft(val note: String, val form: HoleEntryForm, val model: NoteReading, val readBy: ReadBy? = null)

/**
 * Logging one hole. Say or type how it went and the card fills itself in underneath; tap anything to
 * change it. The note is kept forever, so stats can be re-read from it later. A score is all that's required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoleEntrySheet(
    hole: Hole,
    existing: HoleScore?,
    average: Pair<Double, Int>?,
    reader: NoteReader,
    voice: VoiceInput,
    draft: HoleDraft?,
    onDismiss: (HoleDraft?) -> Unit,
    onSave: (HoleScore) -> Unit,
) {
    val c = Tally.colors
    val t = Tally.type
    val previousNote = existing?.notes?.lastOrNull()?.text.orEmpty()
    val startNote = draft?.note ?: previousNote
    var note by remember { mutableStateOf(startNote) }
    var form by remember { mutableStateOf(draft?.form ?: HoleEntryForm.start(hole.number, hole.par, existing)) }
    var model by remember { mutableStateOf(draft?.model ?: NoteReading.EMPTY) }
    /** Who produced [model]: saved with the hole so notes read offline can be read again online. */
    var readBy by remember { mutableStateOf(draft?.readBy) }
    var thinking by remember { mutableStateOf(false) }
    /** The keyboard is up and you're writing; false once you close it or finish speaking. */
    var typing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    var spoke by remember { mutableStateOf(false) }
    val readerState by reader.state.collectAsStateWithLifecycle()
    val par = form.parOrDefault
    val context = remember(hole) { HoleContext(hole.number, par) }
    val yards = remember(hole) { (HoleDistances(hole).lengthMetres * METRES_TO_YARDS).roundToInt() }
    // Words in the note behind each field the note filled; a field you've since tapped is no longer the note's.
    val spans = remember(note, form) { NoteEvidence.spans(note, form.fromNote(), par) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The spots you marked while playing, with clubs from the note unless you've picked them.
    var marks by remember { mutableStateOf(existing?.shots.orEmpty()) }
    /** Shots you removed here, by time, so one found while the sheet is open is kept but these aren't. */
    var removed by remember { mutableStateOf(emptySet<Long>()) }
    val card = CardCounts(form.strokes.value.takeIf { !form.noScore.value }, form.putts.value, form.penalties.value)
    val clubbed = remember(marks, note, form.teeClub.value, card) { ShotTrack.withClubsFromNote(hole, marks, note, form.teeClub.value, card) }

    LaunchedEffect(Unit) {
        reader.warmUp()
        // An old note is what the card was saved from: read it first so editing it only applies what changed.
        // Quick (cached or on the phone) and never waits on the network, so editing can start straight away.
        if (draft == null && previousNote.isNotBlank()) {
            model = withTimeoutOrNull(BASELINE_MS) { reader.draftRead(previousNote, context)?.reading } ?: NoteReading.EMPTY
            readBy = existing?.noteReadBy
            form = form.withBaseline(NotePrompt.quickRead(previousNote, par) over model)
        }
        // While you type (or talk): the phone's model reads each pause, instantly and offline.
        // Once you've finished (keyboard closed, done speaking, or a longer pause): the cloud reads it once,
        // with the phone's reading shown meanwhile so a slow signal never leaves the card empty.
        snapshotFlow { note to typing }.collectLatest { (text, stillTyping) ->
            // The saved note was read above. A draft is read again (from the cache) in case it was left mid-read.
            if (draft == null && text == previousNote) return@collectLatest
            val quick = NotePrompt.quickRead(text, par)
            if (text.isBlank()) { model = NoteReading.EMPTY; readBy = null; form = form.read(NoteReading.EMPTY); return@collectLatest }
            form = form.read(quick over model)
            var latest: NoteRead? = null
            fun apply(r: NoteRead) { latest = r; model = r.reading; readBy = r.by; form = form.read(quick over r.reading) }
            if (stillTyping) {
                delay(PAUSE_MS)
                thinking = true
                try { reader.draftRead(text, context)?.let(::apply) } finally { thinking = false }
                // Already read in the cloud, word for word (e.g. you deleted and retyped): nothing more to do.
                if (latest?.by == ReadBy.CLOUD) return@collectLatest
                delay(SETTLE_MS - PAUSE_MS)
            }
            thinking = true
            try {
                coroutineScope {
                    val cloud = async { reader.finalRead(text, context) }
                    // Finished without a pause first (keyboard closed straight away): the phone reads it meanwhile.
                    if (latest == null) launch { reader.draftRead(text, context)?.let { if (latest?.by != ReadBy.CLOUD) apply(it) } }
                    cloud.await()?.let(::apply)
                }
            } finally { thinking = false }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            val untouched = note == previousNote && form == HoleEntryForm.start(hole.number, hole.par, existing).withBaseline(form.reading)
            onDismiss(if (untouched) null else HoleDraft(note, form, model, readBy))
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line)) },
    ) {
        val focus = LocalFocusManager.current
        fun toggleMic() {
            if (listening) { voice.finish(); return }
            focus.clearFocus()
            val before = note.trim()
            voiceProblem = null
            scope.launch {
                listening = true
                typing = true
                try {
                    voice.listen().collect { e ->
                        when (e) {
                            is VoiceEvent.Partial -> { note = joinNote(before, e.text); spoke = true }
                            is VoiceEvent.Final -> if (e.text.isNotBlank()) { note = joinNote(before, e.text); spoke = true }
                            is VoiceEvent.Level -> level = e.level
                            is VoiceEvent.Failed -> voiceProblem = when (e.reason) {
                                VoiceFailure.NO_PERMISSION -> "Tally needs the microphone for voice notes. You can still type."
                                VoiceFailure.NOTHING_HEARD -> "Didn't catch that. Tap the mic and try again."
                                VoiceFailure.BUSY -> "The microphone is busy. Try again in a moment."
                                else -> "Voice isn't working right now. You can still type."
                            }
                            VoiceEvent.Listening -> {}
                        }
                    }
                } finally {
                    listening = false
                    typing = false
                    level = 0f
                }
            }
        }
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Hole ${hole.number}", style = t.holeNumber.copy(color = c.ink, fontSize = t.holeNumber.fontSize * 0.75f))
                    Text("  Par $par · $yards yd", style = t.title.copy(color = c.muted), modifier = Modifier.padding(bottom = 4.dp).weight(1f))
                    average?.let { (avg, n) ->
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 2.dp)) {
                            Text(avg.oneDecimal(), style = t.numberMedium.copy(color = c.ink))
                            Text("your avg · $n rounds", style = t.label.copy(color = c.muted, fontSize = t.caps.fontSize))
                        }
                    }
                }

                // The note first: it's the quickest way in and the richest record of the hole.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.5.dp, c.line, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HOW DID IT GO?", style = t.caps.copy(color = c.muted), modifier = Modifier.weight(1f))
                        if (voice.isAvailable) MicButton(listening, level, onClick = ::toggleMic)
                    }
                    Box {
                        if (note.isEmpty()) Text(
                            if (voice.isAvailable) "Tap the mic and say it, or type. e.g. “6, drive right into the trees, chipped out, two putts”"
                            else "e.g. “6, drive right into the trees, chipped out, two putts”",
                            style = t.body.copy(color = c.muted, fontSize = t.title.fontSize),
                        )
                        BasicTextField(
                            value = note,
                            onValueChange = { if (listening) voice.finish(); note = it },
                            textStyle = t.body.copy(color = c.ink, fontSize = t.title.fontSize, lineHeight = t.title.fontSize * 1.45f),
                            onTextLayout = { layout = it },
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).onFocusChanged { typing = it.isFocused || listening }.drawBehind { layout?.let { underline(it, spans, note.length) { f -> fieldColor(f, c) } } },
                        )
                    }
                    Assistant(note = note, form = form, thinking = thinking, readerState = readerState, listening = listening, voiceProblem = voiceProblem)
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Rows the note filled get a soft rounded highlight inset in the card; dividers only between plain rows.
                    val filled = listOfNotNull(
                        form.strokes.origin == Origin.NOTE,
                        form.putts.origin == Origin.NOTE,
                        if (par >= 4) form.teeShot.origin == Origin.NOTE || form.teeClub.origin == Origin.NOTE else form.teeClub.origin == Origin.NOTE,
                        form.penalties.origin == Origin.NOTE,
                        form.bunkerShots.origin == Origin.NOTE,
                    )
                    fun line(i: Int) = i > 0 && !filled[i] && !filled[i - 1]
                    Noted(form.strokes, HoleEntryForm.STROKES, top = 8.dp, bottom = 8.dp, divider = false) {
                        Text("SCORE", style = t.caps.copy(color = c.muted), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                        ScorePicker(par = par, selected = form.strokes.value, onSelect = { s -> focus.clearFocus(); form = form.tapStrokes(s) })
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    form.noScore.value -> "No score. Left out of your totals."
                                    form.strokes.value != null -> resultName(form.strokes.value!!, par)
                                    else -> "Pick a score, or say it in your note"
                                },
                                style = t.label.copy(color = if (form.strokes.value == null) c.muted else fieldColor(HoleEntryForm.STROKES, c)),
                                modifier = Modifier.weight(1f),
                            )
                            FromNote(if (form.noScore.origin == Origin.NOTE) form.noScore else form.strokes, HoleEntryForm.STROKES)
                            Spacer(Modifier.width(8.dp))
                            // Picked up, or not counting it: the hole's done without a score.
                            val off = form.noScore.value
                            Text(
                                "No score",
                                style = t.label.copy(color = if (off) c.bg else c.muted),
                                modifier = Modifier.clip(CircleShape).background(if (off) c.ink else c.raise)
                                    .clickable(role = Role.Switch) { focus.clearFocus(); form = form.tapNoScore(!off) }
                                    .padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Noted(form.putts, HoleEntryForm.PUTTS, divider = line(1)) {
                        FieldLine("Putts", form.putts, HoleEntryForm.PUTTS) { Stepper(form.putts.value, { focus.clearFocus(); form = form.tapPutts(it) }, min = 0, max = form.maxFor(HoleEntryForm.PUTTS) ?: 6, label = "putts") }
                        PuttDistances(form, fieldColor(HoleEntryForm.PUTTS, c), onChange = { focus.clearFocus(); form = it })
                    }
                    if (par >= 4) {
                        Noted(if (form.teeShot.origin == Origin.NOTE) form.teeShot else form.teeClub, HoleEntryForm.TEE_SHOT, divider = line(2)) {
                            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tee shot", style = t.title.copy(color = c.ink), modifier = Modifier.weight(1f))
                                    FromNote(if (form.teeShot.origin == Origin.NOTE) form.teeShot else form.teeClub, HoleEntryForm.TEE_SHOT)
                                }
                                Segmented(
                                    options = listOf(TeeShot.LEFT to "Left", TeeShot.FAIRWAY to "Fairway", TeeShot.RIGHT to "Right"),
                                    selected = form.teeShot.value,
                                    onSelect = { focus.clearFocus(); form = form.tapTeeShot(it) },
                                    allowNone = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                ClubChips(form.teeClub.value) { focus.clearFocus(); form = form.tapTeeClub(it) }
                            }
                        }
                    } else {
                        Noted(form.teeClub, HoleEntryForm.TEE_CLUB, divider = line(2)) {
                            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tee club", style = t.title.copy(color = c.ink), modifier = Modifier.weight(1f))
                                    FromNote(form.teeClub, HoleEntryForm.TEE_CLUB)
                                }
                                ClubChips(form.teeClub.value) { focus.clearFocus(); form = form.tapTeeClub(it) }
                            }
                        }
                    }
                    Noted(form.penalties, HoleEntryForm.PENALTIES, divider = line(filled.size - 2)) {
                        FieldLine("Penalty strokes", form.penalties, HoleEntryForm.PENALTIES) { Stepper(form.penalties.value, { focus.clearFocus(); form = form.tapPenalties(it) }, max = form.maxFor(HoleEntryForm.PENALTIES) ?: 6, label = "penalty strokes") }
                    }
                    Noted(form.bunkerShots, HoleEntryForm.BUNKER_SHOTS, divider = line(filled.size - 1)) {
                        FieldLine("Bunker shots", form.bunkerShots, HoleEntryForm.BUNKER_SHOTS) { Stepper(form.bunkerShots.value, { focus.clearFocus(); form = form.tapBunkerShots(it) }, max = form.maxFor(HoleEntryForm.BUNKER_SHOTS) ?: 6, label = "bunker shots") }
                    }
                }
                val tracked = ShotTrack.shots(hole, clubbed, form.teeClub.value, card, note)
                if (tracked.any { !it.inferred }) {
                    val first = clubbed.size - tracked.count { !it.inferred }
                    TrackedShots(
                        tracked,
                        onClub = { number, club ->
                            focus.clearFocus()
                            marks = clubbed.mapIndexed { i, m -> if (i == first + number - 1) m.copy(club = club, clubByYou = club != null) else m }
                            if (number == 1) form = form.tapTeeClub(club)
                        },
                        onRemove = { number ->
                            clubbed.getOrNull(first + number - 1)?.let { gone -> removed = removed + gone.timeMillis }
                            marks = clubbed.filterIndexed { i, _ -> i != first + number - 1 }
                        },
                    )
                }
                Spacer(Modifier.size(2.dp))
            }

            // Always reachable, even with the keyboard up: write the note, glance at the card, save.
            PrimaryButton(
                when {
                    !form.canSave -> "Pick a score to save"
                    form.noScore.value -> "Save hole ${hole.number} · no score"
                    else -> "Save hole ${hole.number}"
                },
                enabled = form.canSave,
                onClick = {
                    val notes = existing?.notes.orEmpty().let { old ->
                        val text = note.trim()
                        if (text.isEmpty() || text == previousNote.trim()) old else old + HoleNote(text, if (spoke) NoteSource.VOICE else NoteSource.TYPED, currentTimeMillis())
                    }
                    val text = note.trim()
                    form.toHoleScore(existing, notes)
                        ?.copy(shots = (clubbed + existing?.shots.orEmpty().filter { s -> s.timeMillis !in removed && clubbed.none { it.timeMillis == s.timeMillis } }).sortedBy { it.timeMillis }, noteReadBy = when {
                            text.isEmpty() -> null
                            text == previousNote.trim() -> existing?.noteReadBy
                            else -> readBy ?: ReadBy.INSTANT
                        })
                        ?.let(onSave)
                },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp).navigationBarsPadding(),
            )
        }
    }
}

/** One quiet line under the note: what the assistant is doing, or what it took from the note, in each field's colour. */
@Composable
private fun Assistant(note: String, form: HoleEntryForm, thinking: Boolean, readerState: ReaderState, listening: Boolean, voiceProblem: String?) {
    val c = Tally.colors
    val t = Tally.type
    val filled = readback(form)
    val status: String? = when {
        voiceProblem != null -> voiceProblem
        listening && note.isBlank() -> "Listening. Say it how you'd tell a friend."
        listening && filled.isEmpty() -> "Listening. Tap the mic when you're done."
        note.isBlank() -> when (readerState) {
            is ReaderState.Preparing -> "Getting the on-device assistant ready" + (readerState.progress?.let { " · ${(it * 100).roundToInt()}%" } ?: "…")
            ReaderState.Ready -> "Type or dictate it how you'd say it. Tally fills in the card."
            ReaderState.Unavailable -> "Saved with the hole, so it can be read into your stats later."
        }
        thinking && filled.isEmpty() -> "Reading your note…"
        filled.isNotEmpty() -> null
        readerState == ReaderState.Ready -> "Nothing to fill in from that. It's saved with the hole."
        else -> "Saved with the hole."
    }
    val active = status == null || listening || (thinking && filled.isEmpty())
    val key = status ?: filled.joinToString(" · ") { it.second }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.heightIn(min = 20.dp)) {
        Box(Modifier.padding(top = 2.dp)) {
            when {
                thinking -> RollingBall()
                listening -> Pulse(true, true)
                filled.isNotEmpty() -> BallInCup()
                else -> Pulse(false, active)
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = key, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "assistant") { _ ->
            if (status != null) Text(status, style = t.label.copy(color = if (active) c.accent else c.muted))
            else Text(
                buildAnnotatedString {
                    filled.forEachIndexed { i, (field, text) ->
                        if (i > 0) withStyle(SpanStyle(color = c.muted)) { append("  ·  ") }
                        withStyle(SpanStyle(color = fieldColor(field, c))) { append(text) }
                    }
                },
                style = t.label,
            )
        }
    }
}

private fun readback(form: HoleEntryForm): List<Pair<String, String>> = buildList {
    val par = form.parOrDefault
    if (form.strokes.origin == Origin.NOTE) form.strokes.value?.let { add(HoleEntryForm.STROKES to "$it · ${resultName(it, par)}") }
    if (form.putts.origin == Origin.NOTE) add(HoleEntryForm.PUTTS to plural(form.putts.value, "putt"))
    if (form.firstPutt.origin == Origin.NOTE && form.showsFirstPutt) form.firstPutt.value?.let { add(HoleEntryForm.FIRST_PUTT to "from $it ft") }
    if (form.puttMiss.origin == Origin.NOTE && form.showsMiss) form.puttMiss.value?.let { add(HoleEntryForm.PUTT_MISS to missName(it)) }
    if (form.leave.origin == Origin.NOTE && form.showsMiss) form.leave.value?.let { add(HoleEntryForm.LEAVE to "left $it ft") }
    if (form.teeShot.origin == Origin.NOTE) form.teeShot.value?.let { add(HoleEntryForm.TEE_SHOT to when (it) { TeeShot.LEFT -> "Tee shot left"; TeeShot.FAIRWAY -> "Fairway"; TeeShot.RIGHT -> "Tee shot right" }) }
    if (form.teeClub.origin == Origin.NOTE) form.teeClub.value?.let { add(HoleEntryForm.TEE_CLUB to "$it off the tee") }
    if (form.penalties.origin == Origin.NOTE) add(HoleEntryForm.PENALTIES to plural(form.penalties.value, "penalty stroke"))
    if (form.bunkerShots.origin == Origin.NOTE) add(HoleEntryForm.BUNKER_SHOTS to plural(form.bunkerShots.value, "bunker shot"))
}

/** "Bogey", "Triple bogey", then plain words past that: "4 over" reads better than "+4". */
private fun resultName(strokes: Int, par: Int): String = (strokes - par).let { d -> if (d > 3) "$d over" else scoreName(strokes, par) }

private fun missName(m: PuttMiss) = when (m) {
    PuttMiss.SHORT -> "missed short"; PuttMiss.LONG -> "missed long"; PuttMiss.LEFT -> "missed left"; PuttMiss.RIGHT -> "missed right"
    PuttMiss.LOW -> "missed low"; PuttMiss.HIGH -> "missed high"; PuttMiss.LIPPED -> "lipped out"
}

/**
 * Each field has its own colour, used for its underline in the note, its words in the readback and its
 * "from note" tag, so you can see which words filled which field. Putting details share the putts colour.
 */
private fun fieldColor(field: String, c: me.vanpra.tally.ui.theme.TallyColors): Color = when (field) {
    HoleEntryForm.STROKES -> c.accent
    HoleEntryForm.PUTTS, HoleEntryForm.FIRST_PUTT, HoleEntryForm.PUTT_MISS, HoleEntryForm.LEAVE -> c.blue
    HoleEntryForm.TEE_SHOT, HoleEntryForm.TEE_CLUB -> c.flag
    HoleEntryForm.PENALTIES -> c.warn
    else -> c.mapSandEdge
}

/** Draws a soft coloured underline under each span, following the text across line breaks. */
private fun DrawScope.underline(layout: TextLayoutResult, spans: List<NoteSpan>, length: Int, color: (String) -> Color) {
    val thickness = 2.5.dp.toPx()
    val gap = 3.dp.toPx()
    spans.forEach { span ->
        val start = span.start.coerceIn(0, length)
        val end = span.end.coerceIn(0, length)
        if (end <= start) return@forEach
        val first = layout.getLineForOffset(start)
        val last = layout.getLineForOffset(end - 1)
        for (line in first..last) {
            val from = maxOf(start, layout.getLineStart(line))
            val to = minOf(end, layout.getLineEnd(line, visibleEnd = true))
            if (to <= from) continue
            val x1 = layout.getHorizontalPosition(from, usePrimaryDirection = true)
            val x2 = layout.getHorizontalPosition(to, usePrimaryDirection = true)
            val y = layout.getLineBaseline(line) + gap
            drawLine(color(span.field), Offset(minOf(x1, x2), y), Offset(maxOf(x1, x2), y), strokeWidth = thickness, cap = StrokeCap.Round)
        }
    }
}

/** The club off the tee: a scrolling row of chips, tap again to clear. */
@Composable
private fun ClubChips(selected: String?, onSelect: (String?) -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Your bag, plus whatever the note named if it isn't in there.
        val bag = LocalBagClubs.current
        BagClubs.order(if (selected == null || selected in bag) bag else bag + selected).forEach { club ->
            val on = club == selected
            Text(
                club,
                style = t.label.copy(color = if (on) c.bg else c.ink),
                modifier = Modifier.clip(CircleShape).background(if (on) c.ink else c.raise)
                    .clickable(role = Role.RadioButton) { onSelect(if (on) null else club) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
}

/** The clubs in your bag, for the club chips. Provided by the app from your saved bag. */
val LocalBagClubs = androidx.compose.runtime.staticCompositionLocalOf { BagClubs.STARTER }

/** The shots you marked on the hole: how far each went and with what. Tap one to set its club. */
@Composable
private fun TrackedShots(shots: List<TrackedShot>, onClub: (number: Int, club: String?) -> Unit, onRemove: (number: Int) -> Unit) {
    val c = Tally.colors
    val t = Tally.type
    var open by remember { mutableStateOf<Int?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("YOUR SHOTS", style = t.caps.copy(color = c.muted), modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        shots.forEach { s ->
            val on = open == s.number
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .then(if (s.inferred) Modifier else Modifier.clickable(role = Role.Button) { open = if (on) null else s.number })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.raise), contentAlignment = Alignment.Center) {
                    Text(s.number.toString(), style = t.label.copy(color = c.ink))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(s.club ?: if (s.inferred) "Onto the green" else "Club?", style = t.title.copy(color = if (s.club == null) c.muted else c.ink))
                    if (s.inferred) Text("To the middle of the green, from your score", style = t.label.copy(color = c.muted))
                }
                if (s.auto) {
                    Spacer(Modifier.width(8.dp))
                    Text("FOUND", style = t.caps.copy(color = c.accent))
                }
                Spacer(Modifier.weight(1f))
                Text((if (s.inferred) "≈ " else "") + "${(s.metres * METRES_TO_YARDS).roundToInt()} yd", style = t.numberMedium.copy(color = if (s.inferred) c.muted else c.ink))
            }
            AnimatedVisibility(visible = on) {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClubChips(s.club) { club -> onClub(s.number, club); open = null }
                    Text(
                        if (s.auto) "Not a shot? Remove it" else "Remove this shot",
                        style = t.label.copy(color = c.warn),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onRemove(s.number); open = null }.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Adds what was just said to what was already written. */
private fun joinNote(before: String, said: String): String {
    val s = said.trim().replaceFirstChar { it.uppercase() }
    return when {
        before.isEmpty() -> s
        before.last() in ".!?," -> "$before $s"
        else -> "$before. $s"
    }
}

/** The mic: a ring that moves with your voice while it listens. */
@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val c = Tally.colors
    val ring by animateFloatAsState(if (listening) 1f + level * 0.5f else 1f, tween(90), label = "ring")
    val bg by animateColorAsState(if (listening) c.accent else c.raise, tween(180), label = "mic")
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(44.dp).scale(ring).clip(CircleShape).background(c.accent.copy(alpha = 0.18f)))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(bg)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (listening) "Stop voice note" else "Speak your note" },
            contentAlignment = Alignment.Center,
        ) {
            if (listening) Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c.onAccent))
            else TallyIcon(Icon.MIC, c.ink, Modifier.size(22.dp))
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

/**
 * While the note is being read: a golf ball rolls along to the cup and drops in, again and again.
 * Small enough to sit at the start of the line, and gone the moment the reading lands.
 */
@Composable
private fun RollingBall() {
    val c = Tally.colors
    val roll = rememberInfiniteTransition(label = "roll")
    val t by roll.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "t")
    Canvas(Modifier.size(width = 34.dp, height = 16.dp)) {
        val ground = 12.dp.toPx()
        val cupX = size.width - 4.dp.toPx()
        val r = 3.2.dp.toPx()
        drawLine(c.line, Offset(0f, ground), Offset(cupX - 5.dp.toPx(), ground), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawOval(c.ink.copy(alpha = 0.75f), topLeft = Offset(cupX - 4.dp.toPx(), ground - 1.5.dp.toPx()), size = Size(8.dp.toPx(), 3.dp.toPx()))
        // Roll (slowing as it nears the hole), then drop.
        val rollPart = 0.8f
        val p = (t / rollPart).coerceAtMost(1f)
        val eased = 1 - (1 - p) * (1 - p)
        val x = r + eased * (cupX - r)
        val drop = if (t > rollPart) (t - rollPart) / (1 - rollPart) else 0f
        val y = ground - r - 1.dp.toPx() + drop * 4.dp.toPx()
        val alpha = 1f - drop
        drawCircle(Color.Black.copy(alpha = 0.12f * alpha), r, Offset(x, y + 1.dp.toPx()))
        drawCircle(Color.White.copy(alpha = alpha), r, Offset(x, y))
        drawCircle(c.accent.copy(alpha = alpha), r, Offset(x, y), style = Stroke(1.2.dp.toPx()))
    }
}

/** The reading has landed: the ball sits in the cup. */
@Composable
private fun BallInCup() {
    val c = Tally.colors
    Canvas(Modifier.size(width = 34.dp, height = 16.dp)) {
        val ground = 12.dp.toPx()
        val cupX = size.width - 4.dp.toPx()
        drawLine(c.line, Offset(0f, ground), Offset(cupX - 5.dp.toPx(), ground), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawOval(c.ink.copy(alpha = 0.75f), topLeft = Offset(cupX - 4.dp.toPx(), ground - 1.5.dp.toPx()), size = Size(8.dp.toPx(), 3.dp.toPx()))
        drawCircle(Color.White, 3.2.dp.toPx(), Offset(cupX, ground - 1.6.dp.toPx()))
        drawCircle(c.accent, 3.2.dp.toPx(), Offset(cupX, ground - 1.6.dp.toPx()), style = Stroke(1.2.dp.toPx()))
        drawLine(c.ink, Offset(cupX, ground - 2.dp.toPx()), Offset(cupX, 0f), strokeWidth = 1.dp.toPx())
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(cupX, 0f); lineTo(cupX + 6.dp.toPx(), 2.dp.toPx()); lineTo(cupX, 4.dp.toPx()); close() }, c.flag)
    }
}

/** The assistant's mark: a dot that breathes while it reads, and holds when it has filled something in. */
@Composable
private fun Pulse(thinking: Boolean, active: Boolean) {
    val c = Tally.colors
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(8.dp).alpha(if (thinking) a else 1f).clip(CircleShape).background(if (thinking || active) c.accent else c.line))
}

/** A row the note filled in gets a soft tint, so you can see at a glance what was done for you. */
@Composable
private fun Noted(
    field: Field<*>,
    key: String,
    top: androidx.compose.ui.unit.Dp = 0.dp,
    bottom: androidx.compose.ui.unit.Dp = 0.dp,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Tally.colors
    val bg by animateColorAsState(if (field.origin == Origin.NOTE) fieldColor(key, c).copy(alpha = 0.08f) else Color.Transparent, tween(260), label = "noted")
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRoundRect(bg, cornerRadius = CornerRadius(12.dp.toPx()))
                if (divider) {
                    val inset = 8.dp.toPx()
                    drawLine(c.line, Offset(inset, 0f), Offset(size.width - inset, 0f), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(start = 8.dp, end = 8.dp, top = top, bottom = bottom),
        content = content,
    )
}

@Composable
private fun FromNote(field: Field<*>, key: String) {
    if (field.origin != Origin.NOTE) return
    Text("FROM NOTE", style = Tally.type.caps.copy(color = fieldColor(key, Tally.colors)))
}

@Composable
private fun FieldLine(label: String, field: Field<*>, key: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tally.type.title.copy(color = Tally.colors.ink))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { FromNote(field, key) }
        control()
    }
}


/** A pause in typing (or speaking) before the phone's model reads the note. */
private const val PAUSE_MS = 900L

/** A longer pause that counts as finished: then the cloud reads it, once. */
private const val SETTLE_MS = 2_500L

/** How long reopening a saved hole waits to re-read its old note before you can edit. */
private const val BASELINE_MS = 3_000L

/** Big tap targets for the likely scores around par, with + for a bad hole. */
@Composable
private fun ScorePicker(par: Int, selected: Int?, onSelect: (Int) -> Unit) {
    val c = Tally.colors
    val low = (par - 2).coerceAtLeast(1)
    val high = maxOf(par + 3, selected ?: 0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (low..high).forEach { s ->
            val on = s == selected
            val diff = s - par
            val bg by animateColorAsState(if (on) c.ink else if (diff == 0) c.raise else c.surface, tween(180), label = "score")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(1.dp, if (on) c.ink else c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { onSelect(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s.toString(), style = Tally.type.numberLarge.copy(color = if (on) c.bg else c.ink, fontSize = Tally.type.numberLarge.fontSize * 0.8f), textAlign = TextAlign.Center)
            }
        }
        Box(
            Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { onSelect((selected ?: high) + 1) },
            contentAlignment = Alignment.Center,
        ) { Text("+", style = Tally.type.numberLarge.copy(color = c.muted, fontSize = Tally.type.numberLarge.fontSize * 0.8f)) }
    }
}
