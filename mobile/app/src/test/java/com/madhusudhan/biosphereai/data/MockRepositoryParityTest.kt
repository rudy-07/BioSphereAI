package com.madhusudhan.biosphereai.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mock must answer exactly like the real backend. Expected values were produced by running
 * the Python files in backend/app/models (commit a1cf503) on the same inputs.
 */
class MockRepositoryParityTest {

    @Test
    fun latentOde_matchesBackend() {
        val rising = LatentOdePort.predict(listOf(142.0, 145.0, 150.0, 156.0, 161.0))
        assertEquals(142.2, rising.horizon30m, 0.051)
        assertEquals(153.9, rising.horizon60m, 0.051)
        assertEquals(listOf(137.4, 170.5), rising.confidenceInterval95)

        val falling = LatentOdePort.predict(listOf(120.0, 118.0, 112.0, 105.0, 99.0, 94.0, 90.0, 86.0))
        assertEquals(110.6, falling.horizon30m, 0.051)
        assertEquals(97.5, falling.horizon60m, 0.051)
        assertEquals(listOf(80.9, 114.0), falling.confidenceInterval95)
    }

    @Test
    fun cufflessBp_matchesBackend() {
        assertEquals(BpResponse(145.1, 79.0, 101.0, "mmHg", "STAGE_2"), CufflessBpPort.predict(161.0, 74.0))
        assertEquals(BpResponse(169.3, 104.2, 125.9, "mmHg", "STAGE_2"), CufflessBpPort.predict(65.0, 110.0))
    }

    @Test
    fun activity_matchesBackend() {
        assertEquals(ActivityAnalysisResponse(45.9, 1.43, "HIGH"), ActivityPort.analyze("Running", 30, "MODERATE"))
        assertEquals(ActivityAnalysisResponse(6.7, 1.06, "LOW"), ActivityPort.analyze("Yoga", 20, "LOW"))
    }

    @Test
    fun mealFallback_matchesBackend() {
        val bowl = MealRulePort.analyze("Chicken burrito bowl with brown rice, black beans, and salsa", "T1D")
        assertEquals(226.0, bowl.carbsG, 0.0)
        assertEquals(114.0, bowl.proteinG, 0.0)
        assertEquals(48.0, bowl.fatG, 0.0)
        assertEquals(22.5, bowl.fiberG, 0.0)
        assertEquals("MODERATE", bowl.glycemicLoad)
        assertEquals(199.6, bowl.expectedGlucoseDelta, 0.0)
        assertEquals(84, bowl.peakTimeMinutes)
        assertEquals(
            "Estimated 226g carbohydrates with 22g fiber. Postprandial glycemic rise of ~200 mg/dL peaking at approximately 84 minutes.",
            bowl.clinicalSummary,
        )

        val pizza = MealRulePort.analyze("2 slices of pizza", "T2D")
        assertEquals(68.0, pizza.carbsG, 0.0)
        assertEquals("HIGH", pizza.glycemicLoad)
        assertEquals(39.9, pizza.expectedGlucoseDelta, 0.0)
        assertEquals(110, pizza.peakTimeMinutes)

        val explicit = MealRulePort.analyze("something with 50g carbs", "T1D")
        assertEquals(50.0, explicit.carbsG, 0.0)
        assertEquals(42.5, explicit.expectedGlucoseDelta, 0.0)
        assertEquals(60, explicit.peakTimeMinutes)
    }

    @Test
    fun mockRepository_reportsFailureAsResult() = runBlocking {
        val repo = MockBioSphereRepository(latencyMs = 0L..0L)
        assertTrue(repo.estimateBp(BpRequest("T1D-001", 120.0, 70.0)).isSuccess)
        assertTrue(repo.analyzeMeal(MealAnalysisRequest(" ", "T1D")).isFailure)
        repo.simulateFailure = true
        assertTrue(repo.forecastGlucose(GlucoseForecastRequest("T1D-001", listOf(100.0, 101.0))).isFailure)
    }
}
