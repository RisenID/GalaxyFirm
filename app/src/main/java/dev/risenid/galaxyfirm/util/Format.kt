package dev.risenid.galaxyfirm.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatStamp(context: Context, millis: Long): String {
    val locale = Locale.getDefault()
    val datePart = SimpleDateFormat("dd MMM", locale).format(Date(millis))
    return "$datePart ${formatTime(context, millis)}"
}

fun formatTime(context: Context, millis: Long): String {
    val locale = Locale.getDefault()
    return if (DateFormat.is24HourFormat(context)) {
        SimpleDateFormat("HH:mm", locale).format(Date(millis))
    } else {
        SimpleDateFormat("hh:mm a", locale).format(Date(millis)).lowercase(locale)
    }
}

fun intervalLabel(minutes: Int): String = when (minutes) {
    15 -> "15 min"
    30 -> "30 min"
    60 -> "1 hour"
    120 -> "2 hours"
    180 -> "3 hours"
    else -> if (minutes % 60 == 0) "${minutes / 60} hours" else "$minutes min"
}

fun intervalShort(minutes: Int): String = when (minutes) {
    15 -> "15m"
    30 -> "30m"
    60 -> "1h"
    120 -> "2h"
    180 -> "3h"
    else -> if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
}
