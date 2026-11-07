package me.vanpra.tally.round

import app.cash.sqldelight.db.SqlDriver

/** A fresh, empty in-memory database with the schema created. */
expect fun testDriver(): SqlDriver
