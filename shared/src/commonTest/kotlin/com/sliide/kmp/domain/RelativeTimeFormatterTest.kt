package com.sliide.kmp.domain

import com.sliide.kmp.domain.time.RelativeTimeFormatter
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RelativeTimeFormatterTest {
    private val formatter = RelativeTimeFormatter()
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun ago(elapsed: Duration): String = formatter.format(now - elapsed, now = now)

    @Test
    fun `sub-minute ages read as just now`() {
        assertEquals("just now", ago(Duration.ZERO))
        assertEquals("just now", ago(1.seconds))
        assertEquals("just now", ago(59.seconds))
    }

    @Test
    fun `minutes are singular at one and plural above`() {
        assertEquals("1 minute ago", ago(1.minutes))
        assertEquals("5 minutes ago", ago(5.minutes))
        assertEquals("59 minutes ago", ago(59.minutes))
    }

    @Test
    fun `hours take over at the hour boundary`() {
        assertEquals("1 hour ago", ago(1.hours))
        assertEquals("23 hours ago", ago(23.hours))
    }

    @Test
    fun `days take over at the day boundary`() {
        assertEquals("1 day ago", ago(1.days))
        assertEquals("6 days ago", ago(6.days))
    }

    @Test
    fun `weeks months and years round down`() {
        assertEquals("1 week ago", ago(7.days))
        assertEquals("4 weeks ago", ago(29.days))
        assertEquals("1 month ago", ago(30.days))
        assertEquals("11 months ago", ago(359.days))
        assertEquals("1 year ago", ago(365.days))
        assertEquals("2 years ago", ago(800.days))
    }

    @Test
    fun `a clock skewed into the future degrades to just now rather than a negative age`() {
        assertEquals("just now", formatter.format(now + 10.minutes, now = now))
    }

    @Test
    fun `truncation is toward zero so a partial unit never rounds up`() {
        assertEquals("1 minute ago", ago(1.minutes + 59.seconds))
        assertEquals("1 hour ago", ago(1.hours + 59.minutes))
    }
}
