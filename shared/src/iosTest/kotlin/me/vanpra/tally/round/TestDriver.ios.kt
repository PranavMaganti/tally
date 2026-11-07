package me.vanpra.tally.round

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import me.vanpra.tally.db.TallyDatabase

actual fun testDriver(): SqlDriver = inMemoryDriver(TallyDatabase.Schema)
