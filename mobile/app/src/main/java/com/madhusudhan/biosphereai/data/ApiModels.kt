package com.madhusudhan.biosphereai.data

/*
 * Request/response bodies of the BioSphereAI backend (backend/app/models/schemas.py).
 * Property order and meaning match the JSON exactly; the JSON name is given in each KDoc
 * so a serializer can be added later with @SerialName.
 */

/** POST /api/v1/predict/glucose */
data class GlucoseForecastRequest(
    /** patient_id */ val patientId: String,
    /** recent_glucose, mg/dL at 5 min intervals, oldest first */ val recentGlucose: List<Double>,
    /** timestamps, "HH:mm" */ val timestamps: List<String>? = null,
    /** model */ val model: String = "latentode",
)

data class GlucoseForecastResponse(
    /** horizon_30m */ val horizon30m: Double,
    /** horizon_60m */ val horizon60m: Double,
    /** confidence_interval_95, [lower, upper] at 60 min */ val confidenceInterval95: List<Double>,
    /** inference_time_ms */ val inferenceTimeMs: Double,
)

/** POST /api/v1/predict/bp */
data class BpRequest(
    /** patient_id */ val patientId: String,
    /** recent_glucose */ val recentGlucose: Double,
    /** heart_rate */ val heartRate: Double,
)

data class BpResponse(
    /** systolic */ val systolic: Double,
    /** diastolic */ val diastolic: Double,
    /** mean_arterial_pressure */ val meanArterialPressure: Double,
    /** unit */ val unit: String,
    /** status: NORMAL, ELEVATED, STAGE_1, STAGE_2 */ val status: String,
)

/** POST /api/v1/analyze/meal */
data class MealAnalysisRequest(
    /** query */ val query: String,
    /** patient_type: T1D or T2D */ val patientType: String,
)

data class MealAnalysisResponse(
    /** carbs_g */ val carbsG: Double,
    /** protein_g */ val proteinG: Double,
    /** fat_g */ val fatG: Double,
    /** fiber_g */ val fiberG: Double,
    /** glycemic_load: LOW, MODERATE, HIGH */ val glycemicLoad: String,
    /** expected_glucose_delta, mg/dL */ val expectedGlucoseDelta: Double,
    /** peak_time_minutes */ val peakTimeMinutes: Int,
    /** clinical_summary */ val clinicalSummary: String,
)

/** POST /api/v1/analyze/activity */
data class ActivityAnalysisRequest(
    /** activity_type */ val activityType: String,
    /** duration_minutes */ val durationMinutes: Int,
    /** intensity: LOW, MODERATE, HIGH */ val intensity: String,
)

data class ActivityAnalysisResponse(
    /** estimated_glucose_drop, mg/dL */ val estimatedGlucoseDrop: Double,
    /** insulin_sensitivity_multiplier, 1.0 to 1.5 */ val insulinSensitivityMultiplier: Double,
    /** risk_of_hypo: LOW, MODERATE, HIGH */ val riskOfHypo: String,
)
