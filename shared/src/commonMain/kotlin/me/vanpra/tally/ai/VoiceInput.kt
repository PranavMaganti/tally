package me.vanpra.tally.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

sealed interface VoiceEvent {
    /** The microphone is open. */
    data object Listening : VoiceEvent
    /** Words so far; replaces the previous partial. */
    data class Partial(val text: String) : VoiceEvent
    /** The finished transcript. The flow ends after this. */
    data class Final(val text: String) : VoiceEvent
    /** Input loudness, 0..1, for the mic animation. */
    data class Level(val level: Float) : VoiceEvent
    data class Failed(val reason: VoiceFailure) : VoiceEvent
}

enum class VoiceFailure { NO_PERMISSION, NOTHING_HEARD, UNAVAILABLE, BUSY, OTHER }

/** Speech to text, on the device where possible so it works with no signal. */
interface VoiceInput {
    val isAvailable: Boolean
    /** Starts listening. Cancel collection to abandon; call [finish] to stop and keep what was said. */
    fun listen(): Flow<VoiceEvent>
    fun finish()
}

object NoVoiceInput : VoiceInput {
    override val isAvailable = false
    override fun listen(): Flow<VoiceEvent> = emptyFlow()
    override fun finish() {}
}
