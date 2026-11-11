package com.madhusudhan.biosphereai.algo

enum class SafetyLevel { CLEAR, THROTTLED, SUSPENDED }

data class SafetyState(
    /** Glucose projected [SafetyBarrier.LOOKAHEAD_MIN] minutes ahead from the current slope. */
    val projectedGlucose: Double,
    /** Barrier value h = projected - G_min; negative means the safe set is about to be left. */
    val barrier: Double,
    /** Share of the requested insulin that may be delivered (0..1). */
    val deliveryFraction: Double,
    val level: SafetyLevel,
)

/**
 * Control Barrier Function supervisory filter for insulin delivery.
 * Kotlin port of `SafeCBFController.policy` in `benchmarks/benchmark_simglucose.py`:
 *   h = G + tau * dG/dt - G_min,   alpha = clip(h / margin, 0, 1)
 * Delivery is cut to zero when h <= 0 or the current reading is below G_min.
 */
object SafetyBarrier {
    const val G_MIN = 75.0
    const val LOOKAHEAD_MIN = 25.0
    const val MARGIN = 35.0

    fun evaluate(readings: List<Double>, stepMin: Double = 5.0): SafetyState? {
        if (readings.size < 2) return null
        val n = readings.size
        val current = readings[n - 1]
        val slope = if (n >= 3) (readings[n - 1] - readings[n - 3]) / (2 * stepMin)
        else (readings[n - 1] - readings[n - 2]) / stepMin
        val projected = current + LOOKAHEAD_MIN * slope
        val barrier = projected - G_MIN
        val fraction = when {
            barrier <= 0.0 || current < G_MIN -> 0.0
            barrier < MARGIN -> barrier / MARGIN
            else -> 1.0
        }
        val level = when {
            fraction == 0.0 -> SafetyLevel.SUSPENDED
            fraction < 1.0 -> SafetyLevel.THROTTLED
            else -> SafetyLevel.CLEAR
        }
        return SafetyState(projected, barrier, fraction, level)
    }
}
