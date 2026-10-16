package com.roster.data.remote

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.Logger
import platform.Foundation.NSLog

actual fun platformHttpClientEngine(): HttpClientEngine = Darwin.create()

actual fun platformHttpLogger(): Logger =
    object : Logger {
        override fun log(message: String) {
            NSLog("%s", "RosterHttp: $message")
        }
    }
