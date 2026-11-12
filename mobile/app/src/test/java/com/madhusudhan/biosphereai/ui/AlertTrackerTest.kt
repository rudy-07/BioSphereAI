package com.madhusudhan.biosphereai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertTrackerTest {

    @Test
    fun logsOnlyNewAlerts_andIgnoresFlicker() {
        val t = AlertTracker(repeatAfterMin = 15)
        val logged = listOf(
            0 to AlertKind.NONE,
            5 to AlertKind.LOW_SOON,   // new -> log
            10 to AlertKind.LOW_SOON,  // same -> no
            12 to AlertKind.NONE,
            18 to AlertKind.LOW_SOON,  // back within 15 min -> no
            25 to AlertKind.LOW,       // new kind -> log
            30 to AlertKind.NONE,
            60 to AlertKind.LOW_SOON,  // 55 min after the last one -> log
        ).filter { (minute, kind) -> t.shouldLog(kind, minute) }.map { it.second }
        assertEquals(listOf(AlertKind.LOW_SOON, AlertKind.LOW, AlertKind.LOW_SOON), logged)
    }
}
