package com.madhusudhan.biosphereai.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {

    @Test
    fun everyPersona_hasPlausibleDay() {
        for (p in Personas.all) {
            for (seed in 1L..3L) {
                val s = SimulationSession.startingNow(p, nowMinuteOfDay = 0, seed = seed, autoMeals = true, safetyFilter = true)
                s.advance(24 * 60)
                val values = s.readings.map { it.value }
                assertTrue("${p.id} min ${values.min()}", values.min() >= 40.0)
                assertTrue("${p.id} max ${values.max()}", values.max() <= 400.0)
                val hr = s.readings.map { it.heartRate }
                assertTrue("${p.id} hr ${hr.min()}..${hr.max()}", hr.min() > 45.0 && hr.max() < 190.0)
            }
        }
    }

    @Test
    fun personaRoutine_isFollowed() {
        val s = SimulationSession.startingNow(Personas.aarav, nowMinuteOfDay = 0, seed = 1, autoMeals = true, safetyFilter = false)
        val activities = s.events.filterIsInstance<ExerciseEvent>()
        assertEquals(1, activities.size)
        assertEquals("Football", activities.single().activity)
        assertEquals(3, s.events.count { it is InsulinEvent })

        val t2 = SimulationSession.startingNow(Personas.linda, nowMinuteOfDay = 0, seed = 1, autoMeals = true, safetyFilter = true)
        assertEquals(0, t2.events.count { it is InsulinEvent })
    }

    @Test
    fun heartRate_risesDuringExercise() {
        val s = SimulationSession(Personas.maya, 0, seed = 2, autoMeals = false)
        s.advance(30)
        val resting = s.readings.last().heartRate
        s.logExercise(30.0, 1.5, "Running")
        s.advance(20)
        assertTrue("resting $resting, active ${s.readings.last().heartRate}", s.readings.last().heartRate > resting + 40)
    }
}
