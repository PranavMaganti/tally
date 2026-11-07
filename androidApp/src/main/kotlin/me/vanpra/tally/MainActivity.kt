package me.vanpra.tally

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.CompletableDeferred
import me.vanpra.tally.ai.AndroidVoiceInput
import me.vanpra.tally.ai.GeminiCloudNoteReader
import me.vanpra.tally.ai.GeminiNanoNoteReader
import me.vanpra.tally.platform.AndroidConnectivity
import me.vanpra.tally.location.AndroidLocationSource
import me.vanpra.tally.platform.FileTextStore
import me.vanpra.tally.platform.ImportFile
import me.vanpra.tally.platform.PlatformServices
import me.vanpra.tally.platform.takeCsvFiles

class MainActivity : ComponentActivity() {
    private val location by lazy { AndroidLocationSource(this) }
    private val noteReader by lazy { GeminiNanoNoteReader() }
    private val cloudReader by lazy { GeminiCloudNoteReader(BuildConfig.GEMINI_API_KEY) }
    private val connectivity by lazy { AndroidConnectivity(this) }
    private var micAnswer: CompletableDeferred<Boolean>? = null
    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAnswer?.complete(granted)
        micAnswer = null
    }
    private val voice by lazy {
        AndroidVoiceInput(this) {
            CompletableDeferred<Boolean>().also { micAnswer = it; askMic.launch(Manifest.permission.RECORD_AUDIO) }.await()
        }
    }
    private var onPicked: ((List<ImportFile>) -> Unit)? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        location.onPermissionResult()
    }

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val files = uris.mapNotNull { uri ->
            runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.csv"
                ImportFile(name, contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            }.getOrNull()
        }
        if (files.isNotEmpty()) onPicked?.invoke(files)
        onPicked = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        val services = PlatformServices(
            location = location,
            store = FileTextStore(filesDir),
            pickFiles = { callback ->
                onPicked = callback
                pickCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"))
            },
            // Developer shortcut: CSVs pushed to Android/data/me.vanpra.tally/files/import are imported on launch.
            takeImportFolderFiles = { takeCsvFiles(getExternalFilesDir("import")) },
            noteReader = noteReader,
            cloudReader = cloudReader,
            online = connectivity.online,
            voice = voice,
            database = { app.cash.sqldelight.driver.android.AndroidSqliteDriver(me.vanpra.tally.db.TallyDatabase.Schema, applicationContext, "tally.db") },
        )
        if (intent.getBooleanExtra("evals", false)) me.vanpra.tally.probe.NoteProbe.runEvals(noteReader, getExternalFilesDir("evals")!!)
        else if (intent.getBooleanExtra("probe", false)) me.vanpra.tally.probe.NoteProbe.run(if (intent.hasExtra("cloud")) cloudReader else noteReader, intent.getStringExtra("note")?.let { listOf(it, me.vanpra.tally.ai.GolfSpeech.tidy(it)) })

        setContent {
            App(services)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
