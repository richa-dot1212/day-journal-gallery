package com.journalgallery.shared.data

import app.cash.sqldelight.db.SqlDriver
import com.journalgallery.shared.db.JournalDatabase

/** Platform SQLite driver: AndroidSqliteDriver on Android, NativeSqliteDriver on iOS. */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(factory: DriverFactory): JournalDatabase =
    JournalDatabase(factory.createDriver())
