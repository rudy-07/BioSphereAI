package com.madhusudhan.biosphereai.sim

import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

enum class Cohort(val title: String, val subtitle: String) {
    T1D("Type 1", "Insulin pump, safety filter available"),
    T2D("Type 2", "No insulin, lifestyle coaching"),
}

/** Parameters of the modified Bergman minimal model, same names and values as `engine.py`. */
data class PatientProfile(
    val cohort: Cohort,
    val p1: Double,
    val p2: Double,
    val p3: Double,
    val n: Double,
    val beta: Double,
    val g0: Double,
    val i0: Double,
    val vg: Double,
    val vi: Double,
    val tauIsf: Double,
    val tauGut: Double,
    val bioavail: Double,
) {
    companion object {
        fun type1() = PatientProfile(
            Cohort.T1D, p1 = 0.028, p2 = 0.025, p3 = 0.000013, n = 0.23, beta = 0.0,
            g0 = 110.0, i0 = 0.0, vg = 120.0, vi = 3500.0, tauIsf = 8.5, tauGut = 45.0, bioavail = 0.85,
        )

        fun type2() = PatientProfile(
            Cohort.T2D, p1 = 0.018, p2 = 0.020, p3 = 0.0000045, n = 0.18, beta = 0.045,
            g0 = 135.0, i0 = 16.0, vg = 130.0, vi = 3800.0, tauIsf = 10.0, tauGut = 55.0, bioavail = 0.88,
        )

        fun of(cohort: Cohort) = if (cohort == Cohort.T1D) type1() else type2()
    }
}

sealed interface CareEvent {
    /** Simulation minute at which the event starts. */
    val minute: Int
}

data class MealEvent(override val minute: Int, val carbs: Double, val label: String) : CareEvent

data class InsulinEvent(
    override val minute: Int,
    val requestedUnits: Double,
    val deliveredUnits: Double,
) : CareEvent

data class ExerciseEvent(
    override val minute: Int,
    val durationMin: Double,
    /** 0.7 light, 1.0 moderate, 1.5 vigorous (same scale as engine.py). */
    val intensity: Double,
) : CareEvent

/**
 * One virtual patient, advanced one minute at a time.
 * Port of `CardiometabolicSimulator.simulate_24h` (glucose part) in `simulation/engine.py`:
 * gut absorption, subcutaneous insulin, exercise, interstitial lag and sensor noise.
 */
class VirtualPatient(val profile: PatientProfile, seed: Long) {
    private val rng = Random(seed)

    var minute = 0
        private set
    var bloodGlucose = profile.g0
        private set
    var interstitialGlucose = profile.g0
        private set
    private var insulinAction = 0.0
    private var plasmaInsulin = profile.i0

    /** Scales the pump's basal rate (set by the safety filter). */
    var basalFraction = 1.0

    /** Advances one minute and returns the sensor reading (mg/dL). */
    fun step(events: List<CareEvent>): Double {
        val p = profile
        val t = minute.toDouble()
        val ra = gutAbsorption(t, events)
        val uexo = exogenousInsulin(t, events)
        val ex = exerciseEffect(t, events)

        val dG = -(p.p1 + ex) * (bloodGlucose - p.g0) - insulinAction * bloodGlucose + ra / p.vg
        val dX = -p.p2 * insulinAction + p.p3 * max(0.0, plasmaInsulin - p.i0)
        val endogenous = p.beta * max(0.0, bloodGlucose - 90.0)
        val dI = -p.n * plasmaInsulin + uexo / p.vi + endogenous

        bloodGlucose = max(35.0, bloodGlucose + dG)
        insulinAction = max(0.0, insulinAction + dX)
        plasmaInsulin = max(0.0, plasmaInsulin + dI)
        interstitialGlucose = max(35.0, interstitialGlucose + (bloodGlucose - interstitialGlucose) / p.tauIsf)
        minute++

        val noise = rng.nextGaussian() * SENSOR_NOISE_SD
        return (interstitialGlucose * (1.0 + noise) * 10.0).roundToInt() / 10.0
    }

    private fun gutAbsorption(t: Double, events: List<CareEvent>): Double {
        var ra = 0.0
        for (e in events) {
            if (e is MealEvent && t >= e.minute) {
                val dt = t - e.minute
                val doseMg = e.carbs * 1000.0 * profile.bioavail
                val tau = profile.tauGut
                ra += doseMg * dt / (tau * tau) * exp(-dt / tau)
            }
        }
        return ra
    }

    private fun exogenousInsulin(t: Double, events: List<CareEvent>): Double {
        if (profile.cohort == Cohort.T2D) return 0.0
        var rate = BASAL_MU_PER_MIN * basalFraction
        for (e in events) {
            if (e is InsulinEvent && t >= e.minute) {
                val dt = t - e.minute
                rate += e.deliveredUnits * 1e6 * dt / (TAU_INSULIN * TAU_INSULIN) * exp(-dt / TAU_INSULIN)
            }
        }
        return rate
    }

    private fun exerciseEffect(t: Double, events: List<CareEvent>): Double {
        var mult = 0.0
        for (e in events) {
            if (e !is ExerciseEvent) continue
            val end = e.minute + e.durationMin
            if (t >= e.minute && t <= end) {
                mult += 0.015 * e.intensity
            } else if (t > end && t <= end + 120.0) {
                mult += 0.010 * e.intensity * exp(-(t - end) / 40.0)
            }
        }
        return mult
    }

    companion object {
        const val SENSOR_NOISE_SD = 0.038          // ~8 % MARD
        const val BASAL_MU_PER_MIN = 12.0 * 1000.0 / 60.0
        const val TAU_INSULIN = 55.0
    }
}
