package com.journalgallery.shared.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.journalgallery.shared.db.JournalDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(JournalDatabase.Schema, context, "journal.db")
}
