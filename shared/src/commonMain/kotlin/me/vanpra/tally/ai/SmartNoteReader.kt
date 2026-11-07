package me.vanpra.tally.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.vanpra.tally.round.ReadBy

/**
 * Cloud first, on-device when offline. When online the cloud model reads the note; if it hasn't
 * answered within [patienceMs], the on-device reading is shown meanwhile and replaced when the cloud
 * answers. A failed cloud read leaves the on-device one standing.
 */
class SmartNoteReader(
    private val device: NoteReader,
    private val cloud: NoteReader,
    /** Whether the cloud may be used right now (allowed, configured and online). */
    private val cloudReady: StateFlow<Boolean>,
    scope: CoroutineScope,
    private val patienceMs: Long = 2_500,
) : NoteReader {
    private val cloudUsable: () -> Boolean = { cloudReady.value }

    /** Ready when either reader can read right now, so the sheet never says notes can't be read when Gemini can. */
    override val state: StateFlow<ReaderState> = combine(device.state, cloudReady) { d, c -> if (c) ReaderState.Ready else d }
        .stateIn(scope, SharingStarted.Eagerly, if (cloudReady.value) ReaderState.Ready else device.state.value)
    override fun prepare() = device.prepare()
    override fun warmUp() = device.warmUp()

    override suspend fun read(note: String, hole: HoleContext): NoteReading? =
        (if (cloudUsable()) cloud.read(note, hole) else null) ?: device.read(note, hole)

    /** Readings already made, so a note back to words it had before (delete and retype) costs no new call. */
    private val recent = LinkedHashMap<String, NoteRead>()
    private fun remember(k: String, r: NoteRead): NoteRead {
        recent.remove(k); recent[k] = r
        while (recent.size > 24) recent.remove(recent.keys.first())
        return r
    }
    private fun key(note: String, hole: HoleContext) = "${hole.par}|${note.trim().lowercase()}"

    /** While typing: the phone's model (or a cloud reading of these exact words, if we already have one). */
    override suspend fun draftRead(note: String, hole: HoleContext): NoteRead? {
        if (note.isBlank()) return NoteRead(NoteReading.EMPTY, ReadBy.INSTANT)
        val k = key(note, hole)
        recent[k]?.let { return it }
        return device.read(note, hole)?.let { remember(k, NoteRead(it, device.by)) }
    }

    /** Once you've finished: the cloud, once per wording. Null offline or when it's refused or slow. */
    override suspend fun finalRead(note: String, hole: HoleContext): NoteRead? {
        if (note.isBlank() || !cloudUsable()) return null
        val k = key(note, hole)
        recent[k]?.takeIf { it.by == ReadBy.CLOUD }?.let { return it }
        return cloud.read(note, hole)?.let { remember(k, NoteRead(it, ReadBy.CLOUD)) }
    }

    override fun reads(note: String, hole: HoleContext): Flow<NoteRead> = channelFlow {
        if (note.isBlank()) { send(NoteRead(NoteReading.EMPTY, ReadBy.INSTANT)); return@channelFlow }
        val k = key(note, hole)
        recent[k]?.takeIf { it.by == ReadBy.CLOUD || !cloudUsable() }?.let { send(it); return@channelFlow }
        if (!cloudUsable()) {
            device.read(note, hole)?.let { send(NoteRead(it, device.by).let { r -> remember(k, r) }) }
            return@channelFlow
        }
        val fromCloud = async { runCatching { cloud.read(note, hole) }.getOrNull() }
        val quick = withTimeoutOrNull(patienceMs) { fromCloud.await() }
        if (quick != null) { send(NoteRead(quick, ReadBy.CLOUD).let { remember(k, it) }); return@channelFlow }

        // The cloud is slow (weak signal): show the on-device reading meanwhile, never after the cloud's.
        val lock = Mutex()
        var cloudAnswered = false
        launch {
            val d = device.read(note, hole) ?: return@launch
            lock.withLock { if (!cloudAnswered) send(NoteRead(d, device.by)) }
        }
        fromCloud.await()?.let { c -> lock.withLock { cloudAnswered = true; send(NoteRead(c, ReadBy.CLOUD).let { remember(k, it) }) } }
    }
}
