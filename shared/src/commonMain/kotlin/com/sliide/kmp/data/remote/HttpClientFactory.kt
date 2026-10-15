package com.sliide.kmp.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Each platform supplies its own engine - OkHttp on Android, Darwin (NSURLSession) on iOS -
 * while the plugin stack below is configured once in common code.
 */
expect fun platformHttpClientEngine(): HttpClientEngine

/**
 * Ktor's default logger writes to stdout, which Android discards - the plugin looks installed
 * but nothing ever reaches Logcat. Each platform therefore supplies a logger that targets its
 * own log system.
 */
expect fun platformHttpLogger(): Logger

fun createHttpClient(
    engine: HttpClientEngine = platformHttpClientEngine(),
    token: String = ApiConfig.TOKEN,
    logLevel: LogLevel = LogLevel.HEADERS,
): HttpClient =
    HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                },
            )
        }

        install(Logging) {
            logger = platformHttpLogger()
            level = logLevel
            // Never let the bearer token reach a log; it is the one header worth protecting.
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }

        defaultRequest {
            header(HttpHeaders.Accept, ContentType.Application.Json)
            if (token.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

private const val REQUEST_TIMEOUT_MILLIS = 20_000L
private const val CONNECT_TIMEOUT_MILLIS = 10_000L
