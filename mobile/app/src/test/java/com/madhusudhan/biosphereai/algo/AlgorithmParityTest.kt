package com.madhusudhan.biosphereai.algo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values were produced by the Python reference implementation
 * (simulation/ai_modules.py and benchmarks/benchmark_simglucose.py) so the
 * app and the research code stay in sync.
 */
class AlgorithmParityTest {

    private val series = listOf(
        110, 112, 115, 119, 124, 130, 137, 145, 152, 158, 162, 163, 161, 156,
        149, 140, 130, 121, 112, 104, 97, 91, 86, 82, 79, 77, 76, 77,
    ).map { it.toDouble() }

    private fun forecastAt(i: Int, horizon: Int) = EdgeForecaster.forecast(series.subList(0, i + 1), horizon)!!

    @Test
    fun forecaster_matchesPython_30min() {
        assertEquals(147.119294, forecastAt(6, 30), 1e-5)
        assertEquals(156.805843, forecastAt(7, 30), 1e-5)
        assertEquals(164.500462, forecastAt(8, 30), 1e-5)
        assertEquals(79.194157, forecastAt(21, 30), 1e-5)
    }

    @Test
    fun forecaster_matchesPython_60min() {
        assertEquals(143.969726, forecastAt(6, 60), 1e-5)
        assertEquals(153.131347, forecastAt(7, 60), 1e-5)
        assertEquals(160.615996, forecastAt(8, 60), 1e-5)
        assertEquals(131.568653, forecastAt(15, 60), 1e-5)
    }

    @Test
    fun forecaster_fromFullHistory() {
        assertEquals(76.450672, EdgeForecaster.forecast(series, 5)!!, 1e-5)
        assertEquals(75.809416, EdgeForecaster.forecast(series, 30)!!, 1e-5)
        assertEquals(76.176865, EdgeForecaster.forecast(series, 60)!!, 1e-5)
        assertEquals(12, EdgeForecaster.trajectory(series).size)
    }

    @Test
    fun forecaster_needsSevenReadings() {
        assertNull(EdgeForecaster.forecast(series.take(6), 30))
        assertTrue(EdgeForecaster.trajectory(series.take(6)).isEmpty())
    }

    @Test
    fun metrics_matchPython() {
        val m = CgmMetrics.from(listOf(50, 65, 70, 100, 150, 180, 181, 200, 250, 120).map { it.toDouble() })!!
        assertEquals(136.6, m.meanGlucose, 0.05)
        assertEquals(50.0, m.timeInRangePct, 1e-9)
        assertEquals(20.0, m.timeBelowPct, 1e-9)
        assertEquals(30.0, m.timeAbovePct, 1e-9)
        assertEquals(46.1, m.cvPct, 0.05)
        assertEquals(6.58, m.gmiPct, 0.005)
        assertEquals(10.0, m.veryLowPct, 1e-9)
        assertEquals(0.0, m.veryHighPct, 1e-9)
    }

    @Test
    fun mealResponse_matchesPython() {
        MealResponse.predict(75.0, 142.0, 13.0).let {
            assertEquals(205.75, it.predictedPeak, 1e-9) // Python shows round(…, 1) = 205.8
            assertEquals(63.75, it.spike, 1e-9)
            assertTrue(it.isHighExcursion)
        }
        MealResponse.predict(60.0, 120.0, 20.0).let {
            assertEquals(178.65, it.predictedPeak, 1e-9)
            assertEquals(58.65, it.spike, 1e-9)
            assertTrue(!it.isHighExcursion)
        }
        assertEquals(25.5, MealResponse.predict(30.0, 100.0, 8.0).spike, 1e-9)
    }

    @Test
    fun safetyBarrier_levels() {
        fun eval(vararg g: Int) = SafetyBarrier.evaluate(g.map { it.toDouble() })!!

        eval(120, 110, 100).let {
            assertEquals(50.0, it.projectedGlucose, 1e-9)
            assertEquals(SafetyLevel.SUSPENDED, it.level)
        }
        eval(110, 108, 106).let {
            assertEquals(96.0, it.projectedGlucose, 1e-9)
            assertEquals(0.6, it.deliveryFraction, 1e-9)
            assertEquals(SafetyLevel.THROTTLED, it.level)
        }
        assertEquals(SafetyLevel.CLEAR, eval(100, 102, 104).level)
        // Below G_min right now -> suspended even though rising
        assertEquals(0.0, eval(70, 72, 74).deliveryFraction, 1e-9)
        assertNull(SafetyBarrier.evaluate(listOf(100.0)))
    }

    @Test
    fun trendArrow_bands() {
        assertEquals(TrendArrow.STEADY, TrendArrow.fromRate(0.4))
        assertEquals(TrendArrow.RISING_SLOW, TrendArrow.fromRate(1.5))
        assertEquals(TrendArrow.RISING, TrendArrow.fromRate(2.5))
        assertEquals(TrendArrow.RISING_FAST, TrendArrow.fromRate(3.5))
        assertEquals(TrendArrow.FALLING_SLOW, TrendArrow.fromRate(-1.5))
        assertEquals(TrendArrow.FALLING_FAST, TrendArrow.fromRate(-4.0))
        assertEquals(TrendArrow.UNKNOWN, TrendArrow.fromRate(null))
    }

    @Test
    fun forecastEvaluator_countsSamples() {
        val check = ForecastEvaluator.evaluate(series, 30)!!
        assertEquals(series.size - 6 - 6, check.samples)
        assertTrue(check.forecasterMae >= 0 && check.naiveMae >= 0)
    }
}
