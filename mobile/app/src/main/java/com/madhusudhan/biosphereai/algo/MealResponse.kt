package com.madhusudhan.biosphereai.algo

import kotlin.math.max
import kotlin.math.roundToInt

data class MealPrediction(
    val carbs: Double,
    val preMealGlucose: Double,
    val spike: Double,
    val predictedPeak: Double,
) {
    val isHighExcursion get() = predictedPeak > 180.0
}

/**
 * Post-meal glucose response predictor and coaching text.
 * Port of `T2D_BehavioralEngine.predict_ppgr` / `generate_gemini_nudge` in `simulation/ai_modules.py`
 * (template-based text, no language model involved).
 */
object MealResponse {
    fun predict(carbs: Double, preMealGlucose: Double, hourOfDay: Double): MealPrediction {
        // Insulin sensitivity drops in the evening
        val circadian = 1.0 + 0.25 * max(0.0, (hourOfDay - 14.0) / 10.0)
        val spike = carbs * 0.85 * circadian
        return MealPrediction(carbs, preMealGlucose, spike, preMealGlucose + spike)
    }

    fun coaching(prediction: MealPrediction): String {
        val peak = prediction.predictedPeak.roundToInt()
        val spike = prediction.spike.roundToInt()
        val carbs = prediction.carbs.roundToInt()
        return if (prediction.isHighExcursion) {
            "This meal ($carbs g carbs) is projected to raise glucose to about $peak mg/dL (+$spike). " +
                "A brisk 15-minute walk starting within 20 minutes of eating uses muscle glucose uptake " +
                "and typically blunts the peak by 25-35 mg/dL."
        } else {
            "This meal ($carbs g carbs) is projected to peak around $peak mg/dL (+$spike), " +
                "which stays inside the target range."
        }
    }
}
