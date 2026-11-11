package com.madhusudhan.biosphereai.algo

import kotlin.math.sqrt

/**
 * Standard CGM summary statistics.
 * TIR / TBR / TAR / CV / GMI follow `CardiometabolicAnalyzer.calculate_cgm_metrics`
 * in `simulation/ai_modules.py`; the very-low / very-high splits are the usual
 * consensus bands (<54 and >250 mg/dL).
 */
data class CgmMetrics(
    val readings: Int,
    val meanGlucose: Double,
    val timeInRangePct: Double,   // 70-180 mg/dL inclusive
    val timeBelowPct: Double,     // < 70
    val timeAbovePct: Double,     // > 180
    val veryLowPct: Double,       // < 54
    val veryHighPct: Double,      // > 250
    val cvPct: Double,
    val gmiPct: Double,
) {
    val lowOnlyPct get() = timeBelowPct - veryLowPct
    val highOnlyPct get() = timeAbovePct - veryHighPct

    companion object {
        fun from(values: List<Double>): CgmMetrics? {
            if (values.isEmpty()) return null
            val n = values.size.toDouble()
            val mean = values.sum() / n
            val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / n) // population SD, like numpy
            fun pct(predicate: (Double) -> Boolean) = values.count(predicate) * 100.0 / n
            return CgmMetrics(
                readings = values.size,
                meanGlucose = mean,
                timeInRangePct = pct { it in 70.0..180.0 },
                timeBelowPct = pct { it < 70.0 },
                timeAbovePct = pct { it > 180.0 },
                veryLowPct = pct { it < 54.0 },
                veryHighPct = pct { it > 250.0 },
                cvPct = sd / mean * 100.0,
                gmiPct = 3.31 + 0.02392 * mean,
            )
        }
    }
}

enum class TrendArrow(val symbol: String, val label: String) {
    RISING_FAST("↑↑", "Rising quickly"),
    RISING("↑", "Rising"),
    RISING_SLOW("↗", "Rising slowly"),
    STEADY("→", "Steady"),
    FALLING_SLOW("↘", "Falling slowly"),
    FALLING("↓", "Falling"),
    FALLING_FAST("↓↓", "Falling quickly"),
    UNKNOWN("", "Not enough data");

    companion object {
        /** Conventional CGM trend bands in mg/dL per minute. */
        fun fromRate(ratePerMin: Double?): TrendArrow = when {
            ratePerMin == null -> UNKNOWN
            ratePerMin > 3.0 -> RISING_FAST
            ratePerMin > 2.0 -> RISING
            ratePerMin > 1.0 -> RISING_SLOW
            ratePerMin >= -1.0 -> STEADY
            ratePerMin >= -2.0 -> FALLING_SLOW
            ratePerMin >= -3.0 -> FALLING
            else -> FALLING_FAST
        }
    }
}
