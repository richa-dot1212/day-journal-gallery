package com.journalgallery.shared.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.journalgallery.shared.db.JournalDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(JournalDatabase.Schema, "journal.db")
}
