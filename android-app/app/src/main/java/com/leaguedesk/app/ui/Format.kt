package com.leaguedesk.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

fun fmt1(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    return String.format(Locale.US, "%.1f", value)
}

fun pct(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    return String.format(Locale.US, "%.1f%%", value)
}

fun shortInjury(status: String): String = when (status.uppercase()) {
    "DAY_TO_DAY" -> "DTD"
    "QUESTIONABLE" -> "Q"
    "DOUBTFUL" -> "D"
    "OUT" -> "OUT"
    "SUSPENSION" -> "SUSP"
    "GTD" -> "GTD"
    "INJURED_RESERVE" -> "IR"
    else -> status.take(5).uppercase()
}

fun timeAgo(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    val diff = System.currentTimeMillis() - ms
    if (diff < 0) return formatDate(ms)
    val mins = diff / 60_000L
    if (mins < 1) return "just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7) return "${days}d ago"
    return formatDate(ms)
}

fun formatDate(ms: Long?): String {
    if (ms == null || ms <= 0) return "—"
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
}

fun expiresIn(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val diff = ms - System.currentTimeMillis()
    if (diff <= 0) return "expired"
    val hours = diff / 3_600_000L
    if (hours < 1) return "${(diff / 60_000L).coerceAtLeast(1)}m left"
    if (hours < 48) return "${hours}h left"
    return "${floor(hours / 24.0).toInt()}d left"
}

fun capitalize(value: String?): String {
    if (value.isNullOrBlank()) return "—"
    return value.lowercase().replaceFirstChar { it.uppercase() }
}
