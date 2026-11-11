package com.madhusudhan.biosphereai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madhusudhan.biosphereai.algo.CgmMetrics
import com.madhusudhan.biosphereai.algo.EdgeForecaster
import com.madhusudhan.biosphereai.algo.ForecastCheck
import com.madhusudhan.biosphereai.algo.ForecastEvaluator
import com.madhusudhan.biosphereai.algo.MealPrediction
import com.madhusudhan.biosphereai.algo.MealResponse
import com.madhusudhan.biosphereai.algo.SafetyLevel
import com.madhusudhan.biosphereai.algo.SafetyState
import com.madhusudhan.biosphereai.algo.TrendArrow
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.sim.Reading
import com.madhusudhan.biosphereai.sim.SimulationSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

enum class SimSpeed(val label: String, val detail: String, val msPerReading: Long?) {
    PAUSED("Paused", "Clock stopped", null),
    REAL_TIME("Real time", "1 reading every 5 min", 5 * 60_000L),
    FAST("Fast", "1 reading every 5 s", 5_000L),
    DEMO("Demo", "1 reading per second", 1_000L),
}

data class AppSettings(
    val cohort: Cohort = Cohort.T1D,
    val speed: SimSpeed = SimSpeed.FAST,
    val lowAlert: Double = 70.0,
    val highAlert: Double = 180.0,
    val autoMeals: Boolean = true,
    val safetyFilter: Boolean = true,
)

enum class GlucoseStatus { URGENT_LOW, LOW, IN_RANGE, HIGH }

enum class AlertKind { URGENT_LOW, LOW, LOW_SOON, HIGH, HIGH_SOON, NONE }

data class GlucoseAlert(val kind: AlertKind, val title: String, val detail: String)

data class UiState(
    val settings: AppSettings,
    val nowMinute: Int,
    val startMinuteOfDay: Int,
    val readings: List<Reading>,
    val current: Reading?,
    val status: GlucoseStatus,
    val ratePerMin: Double?,
    val trend: TrendArrow,
    val forecast30: Double?,
    val forecast60: Double?,
    val trajectory: List<Pair<Int, Double>>,
    val safety: SafetyState?,
    val alert: GlucoseAlert,
    val metrics24h: CgmMetrics?,
    val forecastCheck30: ForecastCheck?,
    val forecastCheck60: ForecastCheck?,
    val events: List<CareEvent>,
) {
    fun clock(minute: Int): String {
        val m = Math.floorMod(startMinuteOfDay + minute, 1440)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    val hourOfDay: Double get() = Math.floorMod(startMinuteOfDay + nowMinute, 1440) / 60.0
}

class GlucoseViewModel : ViewModel() {
    private var settings = AppSettings()
    private var session = newSession()
    private var ticker: Job? = null

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        startTicker()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = settings
        settings = transform(settings)
        when {
            settings.cohort != old.cohort -> session = newSession()
            else -> {
                session.autoMeals = settings.autoMeals
                session.safetyFilter = settings.safetyFilter
            }
        }
        if (settings.speed != old.speed) startTicker()
        publish()
    }

    fun resetSimulation() {
        session = newSession()
        publish()
    }

    fun predictMeal(carbs: Double): MealPrediction? {
        val current = session.readings.lastOrNull() ?: return null
        return MealResponse.predict(carbs, current.value, _state.value.hourOfDay)
    }

    fun logMeal(carbs: Double, label: String, bolusUnits: Double?) {
        if (bolusUnits != null && bolusUnits > 0) session.logInsulin(bolusUnits)
        session.logMeal(carbs, label.ifBlank { "Meal" })
        publish()
    }

    fun logInsulin(units: Double) {
        session.logInsulin(units)
        publish()
    }

    fun logExercise(durationMin: Double, intensity: Double) {
        session.logExercise(durationMin, intensity)
        publish()
    }

    private fun newSession(): SimulationSession {
        val now = LocalTime.now()
        return SimulationSession.startingNow(
            cohort = settings.cohort,
            nowMinuteOfDay = now.hour * 60 + now.minute,
            seed = System.nanoTime(),
            autoMeals = settings.autoMeals,
            safetyFilter = settings.safetyFilter,
        )
    }

    private fun startTicker() {
        ticker?.cancel()
        val interval = settings.speed.msPerReading ?: return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(interval)
                session.advance(SimulationSession.READING_STEP_MIN)
                publish()
            }
        }
    }

    private fun publish() {
        _state.value = buildState()
    }

    private fun buildState(): UiState {
        val readings = session.readings.toList()
        val values = readings.map { it.value }
        val current = readings.lastOrNull()
        val rate = EdgeForecaster.velocity(values)
        val trajectory = EdgeForecaster.trajectory(values)
        val last24h = values.takeLast(288)
        return UiState(
            settings = settings,
            nowMinute = session.now,
            startMinuteOfDay = session.startMinuteOfDay,
            readings = readings,
            current = current,
            status = statusOf(current?.value),
            ratePerMin = rate,
            trend = TrendArrow.fromRate(rate),
            forecast30 = EdgeForecaster.forecast(values, 30),
            forecast60 = EdgeForecaster.forecast(values, 60),
            trajectory = trajectory,
            safety = session.safety,
            alert = alertFor(current?.value, trajectory, session.safety),
            metrics24h = CgmMetrics.from(last24h),
            forecastCheck30 = ForecastEvaluator.evaluate(last24h, 30),
            forecastCheck60 = ForecastEvaluator.evaluate(last24h, 60),
            events = session.events.toList(),
        )
    }

    private fun statusOf(value: Double?): GlucoseStatus = when {
        value == null -> GlucoseStatus.IN_RANGE
        value < URGENT_LOW -> GlucoseStatus.URGENT_LOW
        value < settings.lowAlert -> GlucoseStatus.LOW
        value > settings.highAlert -> GlucoseStatus.HIGH
        else -> GlucoseStatus.IN_RANGE
    }

    private fun alertFor(value: Double?, trajectory: List<Pair<Int, Double>>, safety: SafetyState?): GlucoseAlert {
        val low = settings.lowAlert
        val high = settings.highAlert
        return when {
            value == null -> GlucoseAlert(AlertKind.NONE, "Warming up", "Waiting for sensor readings")
            value < URGENT_LOW -> GlucoseAlert(AlertKind.URGENT_LOW, "Urgent low", "Glucose is below ${URGENT_LOW.toInt()} mg/dL")
            value < low -> GlucoseAlert(AlertKind.LOW, "Low", "Glucose is below ${low.toInt()} mg/dL")
            safety?.level == SafetyLevel.SUSPENDED ->
                GlucoseAlert(AlertKind.LOW_SOON, "Falling toward a low", "Projected below ${SAFETY_FLOOR} mg/dL within 25 min")
            trajectory.firstOrNull { it.second < low } != null -> {
                val minutes = trajectory.first { it.second < low }.first
                GlucoseAlert(AlertKind.LOW_SOON, "Low predicted in ~$minutes min", "Forecast drops below ${low.toInt()} mg/dL")
            }
            value > high -> GlucoseAlert(AlertKind.HIGH, "High", "Glucose is above ${high.toInt()} mg/dL")
            trajectory.firstOrNull { it.second > high } != null -> {
                val minutes = trajectory.first { it.second > high }.first
                GlucoseAlert(AlertKind.HIGH_SOON, "High predicted in ~$minutes min", "Forecast rises above ${high.toInt()} mg/dL")
            }
            else -> GlucoseAlert(AlertKind.NONE, "In range", "No alerts in the next hour")
        }
    }

    companion object {
        const val URGENT_LOW = 55.0
        const val SAFETY_FLOOR = 75
    }
}
