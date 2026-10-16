package com.roster.domain.time

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Turns an instant into "5 minutes ago"-style text. Shared across Android and iOS so both
 * platforms phrase elapsed time identically.
 */
class RelativeTimeFormatter(
    private val clock: Clock = Clock.System,
) {
    fun format(
        timestamp: Instant,
        now: Instant = clock.now(),
    ): String {
        val elapsed = now - timestamp
        return when {
            elapsed.isNegative() -> JUST_NOW
            elapsed < 1.minutes -> JUST_NOW
            elapsed < 1.hours -> elapsed.inWholeMinutes.pluralise("minute")
            elapsed < 1.days -> elapsed.inWholeHours.pluralise("hour")
            elapsed < WEEK -> elapsed.inWholeDays.pluralise("day")
            elapsed < MONTH -> (elapsed.inWholeDays / 7).pluralise("week")
            elapsed < YEAR -> (elapsed.inWholeDays / 30).pluralise("month")
            else -> (elapsed.inWholeDays / 365).pluralise("year")
        }
    }

    private fun Long.pluralise(unit: String): String = if (this == 1L) "1 $unit ago" else "$this ${unit}s ago"

    private companion object {
        const val JUST_NOW = "just now"
        val WEEK: Duration = 7.days
        val MONTH: Duration = 30.days
        val YEAR: Duration = 365.days
    }
}
