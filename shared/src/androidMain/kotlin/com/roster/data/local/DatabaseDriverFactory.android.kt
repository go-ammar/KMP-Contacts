package com.roster.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(
    private val context: Context,
) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = RosterDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
        )
}
