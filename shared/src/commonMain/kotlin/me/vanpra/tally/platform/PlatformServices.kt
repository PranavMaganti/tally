package me.vanpra.tally.platform

import androidx.compose.runtime.Composable
import me.vanpra.tally.ai.NoNoteReader
import me.vanpra.tally.ai.NoVoiceInput
import me.vanpra.tally.ai.VoiceInput
import me.vanpra.tally.ai.NoteReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.vanpra.tally.location.LocationSource
import me.vanpra.tally.location.NoLocation

/** Small text files in app-private storage. Writes must be atomic (no half-written files on a crash). */
interface TextStore {
    fun read(name: String): String?
    fun write(name: String, text: String)
}

class InMemoryTextStore : TextStore {
    private val files = mutableMapOf<String, String>()
    override fun read(name: String) = files[name]
    override fun write(name: String, text: String) { files[name] = text }
}

class ImportFile(val name: String, val text: String)

/** Everything the shared app needs from the platform it runs on. */
class PlatformServices(
    val location: LocationSource = NoLocation,
    val store: TextStore = InMemoryTextStore(),
    /** Opens the system file picker for CSV files and hands back their contents. Null where unsupported. */
    val pickFiles: ((onPicked: (List<ImportFile>) -> Unit) -> Unit)? = null,
    /** Files dropped into the app's import folder (e.g. with adb). Each call consumes them. */
    val takeImportFolderFiles: () -> List<ImportFile> = { emptyList() },
    /** On-device model that reads hole notes into card fields. */
    val noteReader: NoteReader = NoNoteReader,
    /** A stronger reader used online (Gemini Flash-Lite); unconfigured when there's no key. */
    val cloudReader: NoteReader = NoNoteReader,
    /** Whether there's a working internet connection. */
    val online: StateFlow<Boolean> = MutableStateFlow(false),
    /** Speech to text for voice notes. */
    val voice: VoiceInput = NoVoiceInput,
    /** Opens the app's SQLite database. Null in previews, where rounds aren't kept. */
    val database: (() -> app.cash.sqldelight.db.SqlDriver)? = null,
)

expect fun currentTimeMillis(): Long

/** Today's local date as yyyy-MM-dd. */
expect fun todayIso(): String

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
