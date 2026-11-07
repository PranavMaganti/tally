package me.vanpra.tally.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.io.File
import java.util.Calendar

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun todayIso(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** Files in the app's private storage, written to a temp file first and renamed so a crash can't corrupt them. */
class FileTextStore(private val dir: File) : TextStore {
    override fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).writeText(text)
            tmp.delete()
        }
    }
}

/** Reads and removes CSV files from [dir]. Used to load exports pushed with adb. */
fun takeCsvFiles(dir: File?): List<ImportFile> {
    val files = dir?.listFiles { f -> f.isFile && f.name.endsWith(".csv", ignoreCase = true) }.orEmpty()
    return files.map { f -> ImportFile(f.name, f.readText()).also { f.renameTo(File(f.parentFile, f.name + ".imported")) } }
}
