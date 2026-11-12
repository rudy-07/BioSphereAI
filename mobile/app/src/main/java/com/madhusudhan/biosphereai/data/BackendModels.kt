package com.madhusudhan.biosphereai.data

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/*
 * Kotlin ports of the backend's model code, used by the mock repository so the app shows
 * the same numbers the real server will return. Keep these in step with backend/app/models/.
 */

private fun round1(x: Double) = (x * 10.0).roundToLong() / 10.0
private fun clip(x: Double, lo: Double, hi: Double) = min(hi, max(lo, x))

/** backend/app/models/latent_ode.py */
object LatentOdePort {
    private const val GAMMA_DAMPING = 0.045
    private const val K_HOMEOSTASIS = 0.012
    private const val LAMBDA_MOMENTUM = 0.035

    fun predict(recent: List<Double>): GlucoseForecastResponse {
        val start = System.nanoTime()
        require(recent.size >= 2) { "At least 2 readings required" }
        val n = recent.size
        val g0 = recent[n - 1]
        val v0 = when {
            n >= 4 -> (3.0 * recent[n - 1] + recent[n - 2] - recent[n - 3] - 3.0 * recent[n - 4]) / 50.0
            else -> (recent[n - 1] - recent[n - 2]) / 5.0
        }
        val m0 = if (n >= 3) (recent[n - 1] - 2.0 * recent[n - 2] + recent[n - 3]) / 25.0 else 0.0
        val baseline = median(recent.takeLast(min(12, n)))

        val dt = 0.5
        val steps = (60.0 / dt).toInt()
        var z = doubleArrayOf(g0, v0, m0)
        var at30 = g0
        for (i in 0 until steps) {
            val t = i * dt
            val k1 = field(z, t, baseline)
            val k2 = field(add(z, k1, 0.5 * dt), t + 0.5 * dt, baseline)
            val k3 = field(add(z, k2, 0.5 * dt), t + 0.5 * dt, baseline)
            val k4 = field(add(z, k3, dt), t + dt, baseline)
            z = DoubleArray(3) { j -> z[j] + dt / 6.0 * (k1[j] + 2 * k2[j] + 2 * k3[j] + k4[j]) }
            z[0] = clip(z[0], 35.0, 450.0)
            if (i + 1 == (30.0 / dt).toInt()) at30 = z[0]
        }
        val at60 = z[0]
        val sigma60 = 3.5 * sqrt(1.0 + 0.08 * 60.0)
        val elapsed = (System.nanoTime() - start) / 1e6
        return GlucoseForecastResponse(
            horizon30m = round1(at30),
            horizon60m = round1(at60),
            confidenceInterval95 = listOf(
                round1(clip(at60 - 1.96 * sigma60, 35.0, 450.0)),
                round1(clip(at60 + 1.96 * sigma60, 35.0, 450.0)),
            ),
            inferenceTimeMs = (elapsed * 100).roundToLong() / 100.0,
        )
    }

    private fun field(z: DoubleArray, t: Double, baseline: Double): DoubleArray {
        val dv = -GAMMA_DAMPING * z[1] - K_HOMEOSTASIS * (z[0] - baseline) + z[2] * kotlin.math.exp(-LAMBDA_MOMENTUM * t)
        return doubleArrayOf(z[1], dv, -LAMBDA_MOMENTUM * z[2])
    }

    private fun add(z: DoubleArray, k: DoubleArray, h: Double) = DoubleArray(3) { z[it] + h * k[it] }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

/** backend/app/models/cuffless_bp.py (no PTT / PIR hardware input). */
object CufflessBpPort {
    fun predict(recentGlucose: Double, heartRate: Double): BpResponse {
        val hr = clip(heartRate, 40.0, 180.0)
        val gl = clip(recentGlucose, 40.0, 450.0)
        val ptt = clip(280.0 - 0.12 * (gl - 100.0) - 0.45 * (hr - 72.0), 160.0, 360.0)
        val pir = clip(1.15 + 0.0005 * (gl - 100.0), 0.75, 1.70)
        val pw50 = clip(0.30 - 0.0003 * (hr - 72.0), 0.20, 0.40)
        val aix = clip(0.42 + 0.001 * (gl - 100.0), 0.20, 0.80)

        val sv = 70.0 * (pw50 / 0.28) * (1.0 - 0.15 * (aix - 0.5))
        val rp = 1.05 * pir * (300.0 / ptt).pow(0.35)
        val co = hr * sv / 1000.0
        val map = 75.0 + (co * rp * 80.0 / 60.0 - 5.5) * 6.5
        val c = 1.15 * (ptt / 280.0).pow(0.4)
        val pp = sv / c

        val sbp = clip(map + 2.0 / 3.0 * pp, 80.0, 200.0)
        val dbp = clip(map - 1.0 / 3.0 * pp, 45.0, 125.0)
        val meanAp = clip((sbp + 2.0 * dbp) / 3.0, 55.0, 145.0)
        val status = when {
            sbp < 120.0 && dbp < 80.0 -> "NORMAL"
            sbp >= 120.0 && sbp < 130.0 && dbp < 80.0 -> "ELEVATED"
            (sbp >= 130.0 && sbp < 140.0) || (dbp >= 80.0 && dbp < 90.0) -> "STAGE_1"
            else -> "STAGE_2"
        }
        return BpResponse(round1(sbp), round1(dbp), round1(meanAp), "mmHg", status)
    }
}

/** backend/app/models/activity_analyzer.py */
object ActivityPort {
    private val metTable = listOf(
        "walking" to 3.5, "running" to 8.5, "jogging" to 7.0, "cycling" to 6.8, "swimming" to 7.2,
        "strength" to 4.5, "weightlifting" to 4.5, "hiit" to 9.5, "yoga" to 2.5, "cardio" to 6.5,
    )

    fun analyze(activityType: String, durationMinutes: Int, intensity: String): ActivityAnalysisResponse {
        val act = activityType.lowercase()
        val duration = durationMinutes.coerceIn(5, 300)
        val level = intensity.uppercase()
        val baseMet = metTable.firstOrNull { act.contains(it.first) }?.second ?: 4.0
        val factor = when (level) {
            "HIGH" -> 1.30
            "LOW" -> 0.75
            else -> 1.00
        }
        val met = baseMet * factor
        val drop = round1(clip(met * 0.18 * duration, 5.0, 90.0))
        val sensitivity = (clip(1.0 + met * duration / 600.0, 1.05, 1.50) * 100).roundToLong() / 100.0
        val risk = when {
            drop >= 45.0 || (duration >= 45 && level == "HIGH") -> "HIGH"
            drop >= 25.0 || duration >= 30 -> "MODERATE"
            else -> "LOW"
        }
        return ActivityAnalysisResponse(drop, sensitivity, risk)
    }
}

/** Offline fallback of backend/app/models/meal_analyzer.py (the server may use Gemini instead). */
object MealRulePort {
    private class Food(val carbs: Double, val protein: Double, val fat: Double, val fiber: Double, val gl: Int, val peak: Int)

    private val foods = linkedMapOf(
        "burrito bowl" to Food(65.0, 38.0, 16.0, 11.0, 2, 60),
        "burrito" to Food(78.0, 32.0, 24.0, 7.0, 3, 75),
        "pizza" to Food(68.0, 24.0, 22.0, 4.0, 3, 90),
        "pasta" to Food(55.0, 14.0, 12.0, 4.0, 3, 50),
        "oatmeal" to Food(42.0, 10.0, 6.0, 6.0, 2, 45),
        "rice" to Food(45.0, 4.0, 1.0, 1.0, 3, 40),
        "brown rice" to Food(38.0, 5.0, 2.0, 3.5, 2, 55),
        "chicken" to Food(0.0, 35.0, 5.0, 0.0, 1, 90),
        "salmon" to Food(0.0, 34.0, 14.0, 0.0, 1, 90),
        "burger" to Food(44.0, 28.0, 22.0, 2.0, 3, 75),
        "salad" to Food(12.0, 8.0, 10.0, 5.0, 1, 40),
        "apple" to Food(25.0, 0.5, 0.3, 4.4, 1, 35),
        "banana" to Food(27.0, 1.3, 0.3, 3.1, 2, 30),
        "sandwich" to Food(40.0, 18.0, 14.0, 3.0, 2, 50),
        "eggs" to Food(1.5, 13.0, 10.0, 0.0, 1, 60),
        "coffee" to Food(2.0, 1.0, 0.5, 0.0, 1, 20),
        "smoothie" to Food(48.0, 8.0, 3.0, 4.0, 3, 30),
        "bread" to Food(26.0, 5.0, 1.5, 2.0, 3, 35),
    )
    private val explicitCarbs = Regex("""(\d+)\s*(?:g|grams)?\s*(?:of\s*)?carb""")

    fun analyze(query: String, patientType: String): MealAnalysisResponse {
        val q = query.lowercase()
        var carbs = 0.0
        var protein = 0.0
        var fat = 0.0
        var fiber = 0.0
        val peaks = mutableListOf<Int>()
        val gls = mutableListOf<Int>()
        for ((name, f) in foods) {
            if (q.contains(name)) {
                carbs += f.carbs; protein += f.protein; fat += f.fat; fiber += f.fiber
                peaks += f.peak; gls += f.gl
            }
        }
        explicitCarbs.find(q)?.let { carbs = it.groupValues[1].toDouble() }

        var peak: Int
        val load: String
        if (carbs == 0.0 && peaks.isEmpty()) {
            carbs = 45.0; protein = 20.0; fat = 12.0; fiber = 4.0
            peak = 60
            load = "MODERATE"
        } else {
            peak = if (peaks.isNotEmpty()) peaks.sum() / peaks.size else 60
            val score = if (gls.isNotEmpty()) gls.average() else 2.0
            load = if (score >= 2.5) "HIGH" else if (score <= 1.4) "LOW" else "MODERATE"
        }
        if (fat >= 18.0 || protein >= 30.0) peak = min(120, peak + 20)

        val netCarbs = max(5.0, carbs - 0.5 * fiber)
        val carbFactor = if (patientType.uppercase() == "T1D") 0.85 else 0.55
        val delta = round1(netCarbs * carbFactor + protein * 0.15)
        val summary = "Estimated ${halfEven(carbs)}g carbohydrates with ${halfEven(fiber)}g fiber. " +
            "Postprandial glycemic rise of ~${halfEven(delta)} mg/dL peaking at approximately $peak minutes."
        return MealAnalysisResponse(round1(carbs), round1(protein), round1(fat), round1(fiber), load, delta, peak, summary)
    }

    /** Python's "{:.0f}" rounds half to even. */
    private fun halfEven(x: Double) = Math.rint(x).toLong()
}
