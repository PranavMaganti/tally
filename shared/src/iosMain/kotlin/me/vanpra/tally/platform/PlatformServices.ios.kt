package me.vanpra.tally.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun todayIso(): String {
    val parts = NSCalendar.currentCalendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, NSDate())
    fun pad(n: Long) = n.toString().padStart(2, '0')
    return "${parts.year}-${pad(parts.month)}-${pad(parts.day)}"
}

/** iOS uses swipe-back gestures handled by the host; nothing to intercept yet. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** Simple persistent store for iOS until a file-based one is needed. */
class UserDefaultsTextStore : TextStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(name: String): String? = defaults.stringForKey("tally.$name")
    override fun write(name: String, text: String) = defaults.setObject(text, "tally.$name")
}
