package me.vanpra.tally.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.vanpra.tally.platform.TextStore

/**
 * Whether notes may be read online (Gemini). On by default; only the note text and the hole's par are
 * ever sent. When it's off, or there's no signal, notes are read on the phone.
 */
class CloudSettings(private val store: TextStore) {
    private val _enabled = MutableStateFlow(store.read(FILE)?.trim() != "false")
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        store.write(FILE, on.toString())
    }

    private companion object { const val FILE = "cloud_reading.txt" }
}
