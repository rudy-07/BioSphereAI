package com.madhusudhan.biosphereai.sim

/** One thing a persona does every day at a fixed clock time. */
sealed interface RoutineItem {
    /** Minute of the day, 0 to 1439. */
    val at: Int

    data class Meal(override val at: Int, val carbs: Double, val label: String) : RoutineItem
    data class Bolus(override val at: Int, val units: Double) : RoutineItem
    data class Activity(override val at: Int, val minutes: Double, val intensity: Double, val name: String) : RoutineItem
}

/** A named virtual patient: model parameters plus a daily routine. Fictional people. */
data class Persona(
    val id: String,
    val name: String,
    val age: Int,
    val summary: String,
    val profile: PatientProfile,
    val routine: List<RoutineItem>,
) {
    val cohort: Cohort get() = profile.cohort
}

private fun hm(h: Int, m: Int = 0) = h * 60 + m

object Personas {
    /** Reference adult with the run_demo.py protocol; matches the Python engine's defaults. */
    val maya = Persona(
        id = "t1d_maya", name = "Maya", age = 34,
        summary = "Reference adult. Pump user, regular meals, afternoon walk.",
        profile = PatientProfile.type1().copy(restingHr = 68.0),
        routine = listOf(
            RoutineItem.Bolus(hm(7, 55), 5.0), RoutineItem.Meal(hm(8), 55.0, "Breakfast"),
            RoutineItem.Bolus(hm(12, 55), 7.0), RoutineItem.Meal(hm(13), 75.0, "Lunch"),
            RoutineItem.Activity(hm(17), 30.0, 1.0, "Walking"),
            RoutineItem.Bolus(hm(19, 25), 6.0), RoutineItem.Meal(hm(19, 30), 65.0, "Dinner"),
        ),
    )

    val aarav = Persona(
        id = "t1d_aarav", name = "Aarav", age = 16,
        summary = "Active teenager. Sensitive to insulin, football after school.",
        profile = PatientProfile.type1().copy(p3 = 0.000016, g0 = 105.0, tauGut = 40.0, restingHr = 62.0),
        routine = listOf(
            RoutineItem.Bolus(hm(7, 25), 5.0), RoutineItem.Meal(hm(7, 30), 60.0, "Breakfast"),
            RoutineItem.Bolus(hm(12, 25), 6.0), RoutineItem.Meal(hm(12, 30), 80.0, "Lunch"),
            RoutineItem.Activity(hm(16), 60.0, 1.5, "Football"),
            RoutineItem.Bolus(hm(19, 55), 5.0), RoutineItem.Meal(hm(20), 70.0, "Dinner"),
        ),
    )

    val robert = Persona(
        id = "t1d_robert", name = "Robert", age = 58,
        summary = "Long-standing type 1. Less insulin sensitive, slower digestion.",
        profile = PatientProfile.type1().copy(p3 = 0.000011, g0 = 120.0, tauGut = 50.0, restingHr = 74.0),
        routine = listOf(
            RoutineItem.Bolus(hm(8, 25), 5.0), RoutineItem.Meal(hm(8, 30), 45.0, "Breakfast"),
            RoutineItem.Bolus(hm(13, 25), 7.0), RoutineItem.Meal(hm(13, 30), 60.0, "Lunch"),
            RoutineItem.Activity(hm(18), 20.0, 0.7, "Walking"),
            RoutineItem.Bolus(hm(19, 55), 7.0), RoutineItem.Meal(hm(20), 70.0, "Dinner"),
        ),
    )

    /** Reference type 2 adult with the run_demo.py meals. */
    val james = Persona(
        id = "t2d_james", name = "James", age = 52,
        summary = "Reference type 2. No insulin, three meals, afternoon walk.",
        profile = PatientProfile.type2().copy(restingHr = 76.0),
        routine = listOf(
            RoutineItem.Meal(hm(8), 55.0, "Breakfast"),
            RoutineItem.Meal(hm(13), 75.0, "Lunch"),
            RoutineItem.Activity(hm(17), 30.0, 1.0, "Walking"),
            RoutineItem.Meal(hm(19, 30), 65.0, "Dinner"),
        ),
    )

    val priya = Persona(
        id = "t2d_priya", name = "Priya", age = 45,
        summary = "Recently diagnosed. Good insulin reserve, evening walks.",
        profile = PatientProfile.type2().copy(p3 = 0.0000060, g0 = 125.0, beta = 0.05, restingHr = 72.0),
        routine = listOf(
            RoutineItem.Meal(hm(8), 50.0, "Breakfast"),
            RoutineItem.Meal(hm(13), 65.0, "Lunch"),
            RoutineItem.Activity(hm(18, 30), 30.0, 1.0, "Walking"),
            RoutineItem.Meal(hm(20), 60.0, "Dinner"),
        ),
    )

    val linda = Persona(
        id = "t2d_linda", name = "Linda", age = 67,
        summary = "Long-standing type 2. Insulin resistant, short morning walk.",
        profile = PatientProfile.type2().copy(p3 = 0.0000035, g0 = 150.0, beta = 0.035, tauGut = 60.0, restingHr = 78.0),
        routine = listOf(
            RoutineItem.Meal(hm(7, 30), 45.0, "Breakfast"),
            RoutineItem.Activity(hm(10), 15.0, 0.7, "Walking"),
            RoutineItem.Meal(hm(12, 30), 70.0, "Lunch"),
            RoutineItem.Meal(hm(19), 75.0, "Dinner"),
        ),
    )

    val all = listOf(maya, aarav, robert, james, priya, linda)

    fun byId(id: String): Persona = all.firstOrNull { it.id == id } ?: maya

    fun reference(cohort: Cohort): Persona = if (cohort == Cohort.T1D) maya else james
}
