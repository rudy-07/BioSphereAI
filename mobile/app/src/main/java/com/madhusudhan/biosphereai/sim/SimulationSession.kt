package com.madhusudhan.biosphereai.sim

import com.madhusudhan.biosphereai.algo.SafetyBarrier
import com.madhusudhan.biosphereai.algo.SafetyState
import kotlin.math.roundToInt

data class Reading(val minute: Int, val value: Double)

/**
 * A running simulation: one virtual patient, the care events logged so far and the
 * 5-minute sensor readings. Minute 0 corresponds to [startMinuteOfDay] on the clock.
 */
class SimulationSession(
    val cohort: Cohort,
    val startMinuteOfDay: Int,
    seed: Long,
    var autoMeals: Boolean = true,
    var safetyFilter: Boolean = true,
) {
    private val patient = VirtualPatient(PatientProfile.of(cohort), seed)
    private val _events = mutableListOf<CareEvent>()
    private val _readings = ArrayList<Reading>()

    val events: List<CareEvent> get() = _events
    val readings: List<Reading> get() = _readings
    val now: Int get() = patient.minute

    var safety: SafetyState? = null
        private set

    private val safetyActive get() = cohort == Cohort.T1D && safetyFilter

    fun minuteOfDay(minute: Int): Int = Math.floorMod(startMinuteOfDay + minute, 1440)

    fun advance(minutes: Int) = repeat(minutes) { stepOnce() }

    fun logMeal(carbs: Double, label: String): MealEvent =
        MealEvent(now, carbs, label).also { _events.add(it) }

    /** Logs a bolus; with the safety filter on, only the permitted share is delivered. */
    fun logInsulin(units: Double): InsulinEvent {
        val fraction = if (safetyActive) safety?.deliveryFraction ?: 1.0 else 1.0
        val delivered = (units * fraction * 100.0).roundToInt() / 100.0
        return InsulinEvent(now, units, delivered).also { _events.add(it) }
    }

    fun logExercise(durationMin: Double, intensity: Double): ExerciseEvent =
        ExerciseEvent(now, durationMin, intensity).also { _events.add(it) }

    private fun stepOnce() {
        if (autoMeals) runDailyRoutine()
        val active = _events.filter { it.minute >= patient.minute - EVENT_MEMORY_MIN }
        val value = patient.step(active)
        if (patient.minute % READING_STEP_MIN == 0) {
            _readings.add(Reading(patient.minute, value))
            if (_readings.size > MAX_READINGS) _readings.subList(0, _readings.size - MAX_READINGS).clear()
            updateSafety()
        }
    }

    private fun updateSafety() {
        safety = SafetyBarrier.evaluate(_readings.takeLast(3).map { it.value })
        patient.basalFraction = if (safetyActive) safety?.deliveryFraction ?: 1.0 else 1.0
    }

    /** Default daily routine (same meals, boluses and walk as run_demo.py). */
    private fun runDailyRoutine() {
        when (minuteOfDay(patient.minute)) {
            7 * 60 + 55 -> if (cohort == Cohort.T1D) logInsulin(5.0)
            8 * 60 -> logMeal(55.0, "Breakfast")
            12 * 60 + 55 -> if (cohort == Cohort.T1D) logInsulin(7.0)
            13 * 60 -> logMeal(75.0, "Lunch")
            17 * 60 -> logExercise(30.0, 1.0)
            19 * 60 + 25 -> if (cohort == Cohort.T1D) logInsulin(6.0)
            19 * 60 + 30 -> logMeal(65.0, "Dinner")
        }
    }

    companion object {
        const val READING_STEP_MIN = 5
        const val EVENT_MEMORY_MIN = 6 * 60        // effects of older events are negligible
        const val MAX_READINGS = 2 * 288           // keep 48 h of readings

        /** Starts a session whose "now" is the current clock time, with the last 24 h pre-simulated. */
        fun startingNow(
            cohort: Cohort,
            nowMinuteOfDay: Int,
            seed: Long,
            autoMeals: Boolean,
            safetyFilter: Boolean,
        ): SimulationSession {
            val session = SimulationSession(cohort, nowMinuteOfDay, seed, autoMeals = true, safetyFilter = safetyFilter)
            session.advance(24 * 60)
            session.autoMeals = autoMeals
            return session
        }
    }
}
