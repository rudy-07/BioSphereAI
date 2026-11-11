package com.madhusudhan.biosphereai.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatorTest {

    private val meals = listOf(
        MealEvent(480, 55.0, "Breakfast"),
        MealEvent(780, 75.0, "Lunch"),
        MealEvent(1170, 65.0, "Dinner"),
    )
    private val walk = ExerciseEvent(1020, 30.0, 1.0)
    private val boluses = listOf(
        InsulinEvent(475, 5.0, 5.0),
        InsulinEvent(775, 7.0, 7.0),
        InsulinEvent(1165, 6.0, 6.0),
    )

    /** Runs a day and returns blood glucose after each minute (index k = minute k). */
    private fun runDay(profile: PatientProfile, events: List<CareEvent>): DoubleArray {
        val patient = VirtualPatient(profile, seed = 1)
        val blood = DoubleArray(1441)
        blood[0] = patient.bloodGlucose
        for (k in 1..1440) {
            patient.step(events)
            blood[k] = patient.bloodGlucose
        }
        return blood
    }

    // Reference values from simulation/engine.py (simulate_24h with the run_demo.py protocol).
    // Python samples time on a slightly stretched grid (linspace), so allow ~2 mg/dL.

    @Test
    fun type1Day_matchesPythonEngine() {
        val g = runDay(PatientProfile.type1(), meals + boluses + walk)
        assertEquals(160.04, g.max(), 2.0)
        assertEquals(115.58, g[600], 2.0)
        assertEquals(111.79, g[900], 2.0)
        assertEquals(90.23, g.min(), 2.0)
    }

    @Test
    fun type2Day_matchesPythonEngine() {
        val g = runDay(PatientProfile.type2(), meals + walk)
        assertEquals(256.27, g.max(), 2.0)
        assertEquals(218.31, g[600], 2.0)
        assertEquals(243.42, g[900], 2.0)
        assertEquals(153.66, g[1440], 2.0)
    }

    @Test
    fun insulinLowersAndExerciseBluntsGlucose() {
        val noBolus = runDay(PatientProfile.type1(), listOf(MealEvent(60, 60.0, "m")))
        val withBolus = runDay(PatientProfile.type1(), listOf(MealEvent(60, 60.0, "m"), InsulinEvent(55, 6.0, 6.0)))
        assertTrue(withBolus.max() < noBolus.max() - 20)

        val sedentary = runDay(PatientProfile.type2(), listOf(MealEvent(60, 75.0, "m")))
        val walked = runDay(PatientProfile.type2(), listOf(MealEvent(60, 75.0, "m"), ExerciseEvent(90, 20.0, 1.2)))
        assertTrue(walked.max() < sedentary.max())
    }

    @Test
    fun session_prefillsOneDay() {
        val s = SimulationSession.startingNow(Cohort.T1D, nowMinuteOfDay = 14 * 60, seed = 7, autoMeals = true, safetyFilter = true)
        assertEquals(1440, s.now)
        assertEquals(288, s.readings.size)
        assertEquals(14 * 60, s.minuteOfDay(s.now))
        assertEquals(3, s.events.count { it is MealEvent })
        assertEquals(3, s.events.count { it is InsulinEvent })
        assertTrue(s.readings.all { it.value in 35.0..400.0 })
    }

    @Test
    fun safetyFilter_scalesBolusWhenFalling() {
        val s = SimulationSession(Cohort.T1D, 0, seed = 3, autoMeals = false, safetyFilter = true)
        s.logInsulin(10.0)           // big bolus, no food -> glucose falls
        s.advance(180)
        val bolus = s.logInsulin(4.0)
        assertTrue("delivered ${bolus.deliveredUnits}", bolus.deliveredUnits < bolus.requestedUnits)
    }
}
