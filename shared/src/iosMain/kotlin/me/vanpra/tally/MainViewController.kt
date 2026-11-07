package me.vanpra.tally

import androidx.compose.ui.window.ComposeUIViewController
import me.vanpra.tally.platform.PlatformServices
import me.vanpra.tally.platform.UserDefaultsTextStore

fun MainViewController() = ComposeUIViewController { App(PlatformServices(store = UserDefaultsTextStore(), database = { app.cash.sqldelight.driver.native.NativeSqliteDriver(me.vanpra.tally.db.TallyDatabase.Schema, "tally.db") })) }
