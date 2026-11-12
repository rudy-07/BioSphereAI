package com.madhusudhan.biosphereai.ui

/** An alert that was raised, as shown in the alert history and in notifications. */
data class LoggedAlert(
    val id: Long,
    val kind: AlertKind,
    val title: String,
    val detail: String,
    val valueText: String,
    val clock: String,
    val patient: String,
)

/**
 * Decides when an alert is new enough to log and notify: only when the alert kind changes,
 * and not again for the same kind within [repeatAfterMin] simulated minutes (sensor noise
 * can make a "low soon" flicker on and off).
 */
class AlertTracker(private val repeatAfterMin: Int = 15) {
    private var lastKind = AlertKind.NONE
    private val lastLoggedAt = mutableMapOf<AlertKind, Int>()

    fun shouldLog(kind: AlertKind, minute: Int): Boolean {
        val changed = kind != lastKind
        lastKind = kind
        if (kind == AlertKind.NONE || !changed) return false
        val previous = lastLoggedAt[kind]
        if (previous != null && minute - previous < repeatAfterMin) return false
        lastLoggedAt[kind] = minute
        return true
    }

    fun reset() {
        lastKind = AlertKind.NONE
        lastLoggedAt.clear()
    }
}
