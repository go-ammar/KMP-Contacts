package com.sliide.kmp.data.remote

import android.util.Log
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger

actual fun platformHttpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun platformHttpLogger(): Logger =
    object : Logger {
        override fun log(message: String) {
            Log.d(HTTP_LOG_TAG, message)
        }
    }

private const val HTTP_LOG_TAG = "SliideHttp"
