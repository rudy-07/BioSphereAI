package com.madhusudhan.biosphereai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import com.madhusudhan.biosphereai.data.ActivityAnalysisRequest
import com.madhusudhan.biosphereai.data.ActivityAnalysisResponse
import com.madhusudhan.biosphereai.data.BpRequest
import com.madhusudhan.biosphereai.data.BpResponse
import com.madhusudhan.biosphereai.data.GlucoseForecastRequest
import com.madhusudhan.biosphereai.data.GlucoseForecastResponse
import com.madhusudhan.biosphereai.data.MealAnalysisRequest
import com.madhusudhan.biosphereai.data.MealAnalysisResponse
import com.madhusudhan.biosphereai.data.MockBioSphereRepository
import com.madhusudhan.biosphereai.data.Remote
import com.madhusudhan.biosphereai.data.RepositoryProvider
import com.madhusudhan.biosphereai.notify.AlertNotifier
import com.madhusudhan.biosphereai.sim.CareEvent
import com.madhusudhan.biosphereai.sim.Cohort
import com.madhusudhan.biosphereai.sim.Macros
import com.madhusudhan.biosphereai.sim.Persona
import com.madhusudhan.biosphereai.sim.Personas
import com.madhusudhan.biosphereai.sim.Reading
import com.madhusudhan.biosphereai.sim.SimulationSession
import com.madhusudhan.biosphereai.ui.components.formatGlucose
import com.madhusudhan.biosphereai.ui.theme.ThemeMode
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
    val personaId: String = Personas.maya.id,
    val speed: SimSpeed = SimSpeed.FAST,
    val lowAlert: Double = 70.0,
    val highAlert: Double = 180.0,
    val autoMeals: Boolean = true,
    val safetyFilter: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val cloudForecast: Boolean = true,
    val notifications: Boolean = true,
    /** Testing aid: makes every backend call fail. Not persisted. */
    val simulateServerFailure: Boolean = false,
) {
    val persona: Persona get() = Personas.byId(personaId)
    val cohort: Cohort get() = persona.cohort
}

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
    val cloudForecast: Remote<GlucoseForecastResponse>? = null,
    val bp: Remote<BpResponse>? = null,
    val mealAnalysis: Remote<MealAnalysisResponse>? = null,
    val activityAnalysis: Remote<ActivityAnalysisResponse>? = null,
    val alertLog: List<LoggedAlert> = emptyList(),
    val unreadAlerts: Int = 0,
    val dataSource: String = "",
    val notificationsAllowed: Boolean = true,
) {
    fun clock(minute: Int): String {
        val m = Math.floorMod(startMinuteOfDay + minute, 1440)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    val hourOfDay: Double get() = Math.floorMod(startMinuteOfDay + nowMinute, 1440) / 60.0
    val heartRate: Double? get() = current?.heartRate
}

class GlucoseViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val repository = RepositoryProvider.repository
    private val notifier = AlertNotifier(app)
    private val tracker = AlertTracker()

    private var settings = store.load()
    private var session = newSession()
    private var ticker: Job? = null

    private var cloudForecast: Remote<GlucoseForecastResponse>? = null
    private var bp: Remote<BpResponse>? = null
    private var mealAnalysis: Remote<MealAnalysisResponse>? = null
    private var activityAnalysis: Remote<ActivityAnalysisResponse>? = null
    private var cloudJob: Job? = null
    private var bpJob: Job? = null
    private var mealJob: Job? = null
    private var activityJob: Job? = null
    private val alertLog = ArrayDeque<LoggedAlert>()
    private var unreadAlerts = 0
    private var nextAlertId = 1L

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        applyRepositorySettings()
        startTicker()
        refreshRemote()
        publish()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val old = settings
        settings = transform(settings)
        store.save(settings)
        applyRepositorySettings()
        if (settings.personaId != old.personaId) {
            restart()
            return
        }
        session.autoMeals = settings.autoMeals
        session.safetyFilter = settings.safetyFilter
        if (settings.speed != old.speed) startTicker()
        if (settings.cloudForecast != old.cloudForecast || settings.simulateServerFailure != old.simulateServerFailure) {
            refreshRemote()
        }
        publish()
    }

    fun resetSimulation() = restart()

    fun markAlertsRead() {
        unreadAlerts = 0
        publish()
    }

    fun clearAlertLog() {
        alertLog.clear()
        unreadAlerts = 0
        publish()
    }

    /** On-device meal response estimate (no network). */
    fun predictMeal(carbs: Double): MealPrediction? {
        val current = session.readings.lastOrNull() ?: return null
        return MealResponse.predict(carbs, current.value, _state.value.hourOfDay)
    }

    fun analyzeMeal(query: String) {
        mealJob?.cancel()
        mealAnalysis = Remote.Loading
        publish()
        mealJob = viewModelScope.launch {
            mealAnalysis = repository.analyzeMeal(MealAnalysisRequest(query.trim(), settings.cohort.name)).toRemote()
            publish()
        }
    }

    fun clearMealAnalysis() {
        mealJob?.cancel()
        mealAnalysis = null
        publish()
    }

    fun analyzeActivity(type: String, minutes: Int, intensity: String) {
        activityJob?.cancel()
        activityAnalysis = Remote.Loading
        publish()
        activityJob = viewModelScope.launch {
            activityAnalysis = repository.analyzeActivity(ActivityAnalysisRequest(type, minutes, intensity)).toRemote()
            publish()
        }
    }

    fun clearActivityAnalysis() {
        activityJob?.cancel()
        activityAnalysis = null
        publish()
    }

    fun logMeal(carbs: Double, label: String, bolusUnits: Double?, macros: Macros?) {
        if (bolusUnits != null && bolusUnits > 0) session.logInsulin(bolusUnits)
        session.logMeal(carbs, label.ifBlank { "Meal" }, macros)
        mealJob?.cancel()
        mealAnalysis = null
        publish()
    }

    fun logInsulin(units: Double) {
        session.logInsulin(units)
        publish()
    }

    fun logExercise(durationMin: Double, intensity: Double, activity: String) {
        session.logExercise(durationMin, intensity, activity)
        activityJob?.cancel()
        activityAnalysis = null
        publish()
    }

    private fun restart() {
        session = newSession()
        tracker.reset()
        cloudJob?.cancel()
        bpJob?.cancel()
        cloudForecast = null
        bp = null
        mealAnalysis = null
        activityAnalysis = null
        startTicker()
        refreshRemote()
        publish()
    }

    private fun applyRepositorySettings() {
        (repository as? MockBioSphereRepository)?.simulateFailure = settings.simulateServerFailure
    }

    private fun newSession(): SimulationSession {
        val now = LocalTime.now()
        return SimulationSession.startingNow(
            persona = settings.persona,
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
                refreshRemote()
                publish()
            }
        }
    }

    /** Asks the backend for the cloud forecast and the BP estimate for the newest reading. */
    private fun refreshRemote() {
        val readings = session.readings
        val latest = readings.lastOrNull() ?: return
        val patientId = settings.persona.id.uppercase()

        if (settings.cloudForecast) {
            val window = readings.takeLast(CLOUD_WINDOW)
            val request = GlucoseForecastRequest(
                patientId = patientId,
                recentGlucose = window.map { it.value },
                timestamps = window.map { _state.value.clock(it.minute) },
            )
            cloudJob?.cancel()
            if (cloudForecast !is Remote.Ready) cloudForecast = Remote.Loading
            cloudJob = viewModelScope.launch {
                cloudForecast = repository.forecastGlucose(request).toRemote()
                publish()
            }
        } else {
            cloudJob?.cancel()
            cloudForecast = null
        }

        bpJob?.cancel()
        if (bp !is Remote.Ready) bp = Remote.Loading
        bpJob = viewModelScope.launch {
            bp = repository.estimateBp(BpRequest(patientId, latest.value, latest.heartRate)).toRemote()
            publish()
        }
    }

    private fun publish() {
        val s = buildState()
        if (tracker.shouldLog(s.alert.kind, session.now)) {
            val logged = LoggedAlert(
                id = nextAlertId++,
                kind = s.alert.kind,
                title = s.alert.title,
                detail = s.alert.detail,
                valueText = "${formatGlucose(s.current?.value)} mg/dL ${s.trend.symbol}",
                clock = s.clock(s.nowMinute),
                patient = settings.persona.name,
            )
            alertLog.addFirst(logged)
            while (alertLog.size > MAX_ALERTS) alertLog.removeLast()
            unreadAlerts++
            if (settings.notifications) notifier.post(logged)
        }
        _state.value = s.copy(alertLog = alertLog.toList(), unreadAlerts = unreadAlerts)
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
            cloudForecast = cloudForecast,
            bp = bp,
            mealAnalysis = mealAnalysis,
            activityAnalysis = activityAnalysis,
            dataSource = repository.sourceLabel,
            notificationsAllowed = notifier.canPost(),
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
            safety?.level == SafetyLevel.SUSPENDED && settings.cohort == Cohort.T1D ->
                GlucoseAlert(AlertKind.LOW_SOON, "Falling toward a low", "Projected below $SAFETY_FLOOR mg/dL within 25 min")
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

    private fun <T> Result<T>.toRemote(): Remote<T> =
        fold({ Remote.Ready(it) }, { Remote.Failed(it.message ?: "Request failed") })

    companion object {
        const val URGENT_LOW = 55.0
        const val SAFETY_FLOOR = 75
        const val CLOUD_WINDOW = 12
        const val MAX_ALERTS = 50
    }
}
