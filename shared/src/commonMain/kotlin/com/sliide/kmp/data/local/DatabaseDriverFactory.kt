package com.sliide.kmp.data.local

import app.cash.sqldelight.db.SqlDriver

/** Android needs a `Context` to open a database and iOS does not, so the factory is per-platform. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

internal const val DATABASE_NAME = "sliide_users.db"
