package com.madhusudhan.biosphereai.sim

import com.madhusudhan.biosphereai.algo.SafetyBarrier
import com.madhusudhan.biosphereai.algo.SafetyState
import kotlin.math.roundToInt

/** One 5-minute sensor reading; [heartRate] comes from the simulated wearable, bpm. */
data class Reading(val minute: Int, val value: Double, val heartRate: Double = 0.0)

/**
 * A running simulation: one virtual patient, the care events logged so far and the
 * 5-minute sensor readings. Minute 0 corresponds to [startMinuteOfDay] on the clock.
 */
class SimulationSession(
    val persona: Persona,
    val startMinuteOfDay: Int,
    seed: Long,
    var autoMeals: Boolean = true,
    var safetyFilter: Boolean = true,
) {
    constructor(cohort: Cohort, startMinuteOfDay: Int, seed: Long, autoMeals: Boolean = true, safetyFilter: Boolean = true) :
        this(Personas.reference(cohort), startMinuteOfDay, seed, autoMeals, safetyFilter)

    val cohort: Cohort get() = persona.cohort
    private val patient = VirtualPatient(persona.profile, seed)
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

    fun logMeal(carbs: Double, label: String, macros: Macros? = null): MealEvent =
        MealEvent(now, carbs, label, macros).also { _events.add(it) }

    /** Logs a bolus; with the safety filter on, only the permitted share is delivered. */
    fun logInsulin(units: Double): InsulinEvent {
        val fraction = if (safetyActive) safety?.deliveryFraction ?: 1.0 else 1.0
        val delivered = (units * fraction * 100.0).roundToInt() / 100.0
        return InsulinEvent(now, units, delivered).also { _events.add(it) }
    }

    fun logExercise(durationMin: Double, intensity: Double, activity: String = "Activity"): ExerciseEvent =
        ExerciseEvent(now, durationMin, intensity, activity).also { _events.add(it) }

    private fun stepOnce() {
        if (autoMeals) runDailyRoutine()
        val active = _events.filter { it.minute >= patient.minute - EVENT_MEMORY_MIN }
        val value = patient.step(active)
        if (patient.minute % READING_STEP_MIN == 0) {
            _readings.add(Reading(patient.minute, value, (patient.heartRate * 10).roundToInt() / 10.0))
            if (_readings.size > MAX_READINGS) _readings.subList(0, _readings.size - MAX_READINGS).clear()
            updateSafety()
        }
    }

    private fun updateSafety() {
        safety = SafetyBarrier.evaluate(_readings.takeLast(3).map { it.value })
        patient.basalFraction = if (safetyActive) safety?.deliveryFraction ?: 1.0 else 1.0
    }

    /** Runs the persona's daily routine (boluses only for type 1). */
    private fun runDailyRoutine() {
        val clock = minuteOfDay(patient.minute)
        for (item in persona.routine) {
            if (item.at != clock) continue
            when (item) {
                is RoutineItem.Meal -> logMeal(item.carbs, item.label)
                is RoutineItem.Bolus -> if (cohort == Cohort.T1D) logInsulin(item.units)
                is RoutineItem.Activity -> logExercise(item.minutes, item.intensity, item.name)
            }
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
        ): SimulationSession = startingNow(Personas.reference(cohort), nowMinuteOfDay, seed, autoMeals, safetyFilter)

        fun startingNow(
            persona: Persona,
            nowMinuteOfDay: Int,
            seed: Long,
            autoMeals: Boolean,
            safetyFilter: Boolean,
        ): SimulationSession {
            val session = SimulationSession(persona, nowMinuteOfDay, seed, autoMeals = true, safetyFilter = safetyFilter)
            session.advance(24 * 60)
            session.autoMeals = autoMeals
            return session
        }
    }
}
