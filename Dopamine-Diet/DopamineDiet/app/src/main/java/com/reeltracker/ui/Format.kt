package com.reeltracker.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class Clock(is24h: Boolean) {
    private val zone = ZoneId.systemDefault()
    private val timeFull = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a")
    private val seconds = DateTimeFormatter.ofPattern(if (is24h) "HH:mm:ss" else "h:mm:ss")
    private val amPm = DateTimeFormatter.ofPattern("a")
    private val date = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM")
    private val twelve = !is24h

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)

    fun date(ms: Long): String = date.format(at(ms))
    fun clock(ms: Long): String = seconds.format(at(ms))
    fun amPm(ms: Long): String? = if (twelve) amPm.format(at(ms)) else null

    fun ago(then: Long, now: Long): String {
        val m = (now - then) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            at(then).toLocalDate() == LocalDate.now(zone) -> "${m / 60} h ago"
            else -> "${shortDate.format(at(then))}, ${timeFull.format(at(then))}"
        }
    }
}
