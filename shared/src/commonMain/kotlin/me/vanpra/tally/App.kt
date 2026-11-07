package me.vanpra.tally

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import me.vanpra.tally.platform.PlatformServices
import me.vanpra.tally.ui.AppRoot

@Composable
fun App(services: PlatformServices = PlatformServices()) {
    AppRoot(services, systemDark = isSystemInDarkTheme())
}
