package com.madhusudhan.biosphereai.data

/**
 * Everything the app asks of the BioSphereAI backend. One function per endpoint.
 * Failures (no network, server error) come back as [Result.failure], never as exceptions.
 */
interface BioSphereRepository {
    /** Shown in Settings, e.g. "Mock (offline)" or the server address. */
    val sourceLabel: String

    suspend fun forecastGlucose(request: GlucoseForecastRequest): Result<GlucoseForecastResponse>

    suspend fun estimateBp(request: BpRequest): Result<BpResponse>

    suspend fun analyzeMeal(request: MealAnalysisRequest): Result<MealAnalysisResponse>

    suspend fun analyzeActivity(request: ActivityAnalysisRequest): Result<ActivityAnalysisResponse>
}

/** State of one backend value on screen. */
sealed interface Remote<out T> {
    data object Loading : Remote<Nothing>
    data class Ready<T>(val value: T) : Remote<T>
    data class Failed(val message: String) : Remote<Nothing>
}
