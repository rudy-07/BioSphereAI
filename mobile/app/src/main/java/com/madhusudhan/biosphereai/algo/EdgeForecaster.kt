package com.madhusudhan.biosphereai.algo

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * On-device glucose forecaster.
 *
 * Kotlin port of `MultiHorizonForecaster.forecast` in `simulation/ai_modules.py`:
 *  - 4-point least-squares (Savitzky-Golay) velocity over the last 15 minutes
 *  - momentum extrapolation damped by exp(-h / 30)
 *  - soft pull towards the median of the last 7 readings
 *
 * Expects readings every [STEP_MIN] minutes, oldest first.
 */
object EdgeForecaster {
    const val STEP_MIN = 5
    const val MIN_HISTORY = 7

    /** Predicted glucose [horizonMin] minutes after the last reading, or null if history is too short. */
    fun forecast(history: List<Double>, horizonMin: Int): Double? {
        if (history.size < MIN_HISTORY) return null
        val n = history.size
        val current = history[n - 1]
        val velocity = velocity(history) ?: return null
        val delta = velocity * horizonMin * exp(-horizonMin / 30.0)
        val baseline = median(history.subList(n - 7, n))
        val alpha = min(0.10, horizonMin / 300.0)
        val prediction = (1.0 - alpha) * (current + delta) + alpha * baseline
        return prediction.coerceIn(40.0, 400.0)
    }

    /** Forecast points every [STEP_MIN] minutes up to [maxHorizonMin], as (minutes ahead, mg/dL). */
    fun trajectory(history: List<Double>, maxHorizonMin: Int = 60): List<Pair<Int, Double>> {
        if (history.size < MIN_HISTORY) return emptyList()
        return (STEP_MIN..maxHorizonMin step STEP_MIN).map { h -> h to forecast(history, h)!! }
    }

    /** Rate of change in mg/dL per minute from the last four readings. */
    fun velocity(history: List<Double>): Double? {
        if (history.size < 4) return null
        val n = history.size
        return (3.0 * history[n - 1] + history[n - 2] - history[n - 3] - 3.0 * history[n - 4]) / 50.0
    }

    internal fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}

/** How well the forecaster did on a stretch of history, compared with "glucose stays the same". */
data class ForecastCheck(
    val horizonMin: Int,
    val samples: Int,
    val forecasterMae: Double,
    val naiveMae: Double,
    val forecasterZoneAPct: Double,
)

object ForecastEvaluator {
    /** Mirrors the evaluation loop in ai_modules.py (needs 30 min of history per prediction). */
    fun evaluate(values: List<Double>, horizonMin: Int): ForecastCheck? {
        val steps = horizonMin / EdgeForecaster.STEP_MIN
        var n = 0
        var errForecaster = 0.0
        var errNaive = 0.0
        var zoneA = 0
        for (i in 6 until values.size - steps) {
            val prediction = EdgeForecaster.forecast(values.subList(0, i + 1), horizonMin) ?: continue
            val target = values[i + steps]
            errForecaster += abs(prediction - target)
            errNaive += abs(values[i] - target)
            if (ClarkeGrid.isZoneA(prediction, target)) zoneA++
            n++
        }
        if (n == 0) return null
        return ForecastCheck(horizonMin, n, errForecaster / n, errNaive / n, zoneA * 100.0 / n)
    }
}

object ClarkeGrid {
    /** Zone A rule used in ai_modules.py: within 20 % of the reference, or both at/below 70 mg/dL. */
    fun isZoneA(predicted: Double, reference: Double): Boolean =
        (reference <= 70.0 && predicted <= 70.0) || abs(predicted - reference) / reference <= 0.20
}
