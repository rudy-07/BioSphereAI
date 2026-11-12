package com.madhusudhan.biosphereai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.random.Random

/**
 * Stands in for the backend until the HTTP client is wired up. Answers come from the
 * Kotlin ports in BackendModels.kt after a short, network-like delay.
 */
class MockBioSphereRepository(
    private val latencyMs: LongRange = 300L..900L,
) : BioSphereRepository {

    /** When true every call fails, to exercise the app's offline / error states. */
    @Volatile
    var simulateFailure: Boolean = false

    override val sourceLabel = "Mock server (offline, on this phone)"

    override suspend fun forecastGlucose(request: GlucoseForecastRequest) = call {
        LatentOdePort.predict(request.recentGlucose)
    }

    override suspend fun estimateBp(request: BpRequest) = call {
        CufflessBpPort.predict(request.recentGlucose, request.heartRate)
    }

    override suspend fun analyzeMeal(request: MealAnalysisRequest) = call {
        require(request.query.isNotBlank()) { "Meal description cannot be empty" }
        MealRulePort.analyze(request.query, request.patientType)
    }

    override suspend fun analyzeActivity(request: ActivityAnalysisRequest) = call {
        require(request.durationMinutes > 0) { "Duration must be greater than 0 minutes" }
        ActivityPort.analyze(request.activityType, request.durationMinutes, request.intensity)
    }

    private suspend fun <T> call(block: () -> T): Result<T> {
        delay(Random.nextLong(latencyMs.first, latencyMs.last + 1))
        if (simulateFailure) return Result.failure(IOException("Server unreachable (simulated)"))
        return withContext(Dispatchers.Default) { runCatching(block) }
    }
}
