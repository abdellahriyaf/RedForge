package com.redforge.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.datastore.ForgeSettings
import com.redforge.app.data.datastore.SettingsDataStore
import com.redforge.app.data.local.entities.Split
import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.data.repository.SplitRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.formulas.StrengthFormulas
import com.redforge.app.domain.schedule.SplitScheduler
import com.redforge.app.domain.streak.StreakCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.redforge.app.domain.time.WorkoutClock
import java.util.Calendar

data class HomeUiState(
    val activeSplit: Split? = null,
    val nextDay: SplitDay? = null,
    val inProgressSession: WorkoutSession? = null,
    val todayCompleted: Boolean = false,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weekWorkouts: Int = 0,
    val weekSets: Int = 0,
    val weekVolume: Int = 0,
    val settings: ForgeSettings = ForgeSettings(),
    val scheduleNotStarted: Boolean = false,
    val todaySkipped: Boolean = false,
    val loading: Boolean = true
)

private val STREAK_MILESTONES = listOf(7, 30, 100, 365)

class HomeViewModel(
    private val splitRepository: SplitRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _milestoneEvent = MutableStateFlow<Int?>(null)
    val milestoneEvent: StateFlow<Int?> = _milestoneEvent.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        splitRepository.observeActiveSplit(),
        workoutRepository.observeAllSessions(),
        workoutRepository.observeAllSets(),
        settingsDataStore.settingsFlow
    ) { activeSplit, allSessions, allSets, settings ->
        val days = activeSplit?.let { splitRepository.observeDays(it.id).first() }.orEmpty()
        val today = WorkoutClock.nowMillis()
        val scheduleAnchor = settings.scheduleAnchorStartMillis.takeIf {
            it != null && settings.scheduleAnchorSplitId == activeSplit?.id
        }
        val planned = SplitScheduler.plannedDayForDate(
            days,
            allSessions,
            today,
            scheduleAnchorStartMillis = scheduleAnchor
        )
        val scheduleNotStarted = activeSplit != null &&
            scheduleAnchor?.let { SplitScheduler.isBeforeAnchor(it, today) } == true
        val todayStart = startOfDayMillis(today)
        val todaySkipped = activeSplit != null &&
            settings.skippedSplitId == activeSplit.id &&
            settings.skippedWorkoutDayStartMillis == todayStart
        val streak = StreakCalculator.compute(
            sessions = allSessions,
            nowMillis = today,
            scheduledTrainingDayOrders = days.filter { !it.isRestDay }.map { it.dayOrder }.toSet(),
            cycleLength = days.size,
            scheduleAnchorStartMillis = scheduleAnchor,
            skippedDayStartMillis = settings.skippedWorkoutDayStartMillis.takeIf {
                settings.skippedSplitId == activeSplit?.id
            }
        )
        // Active sessions remain resumable across midnight; do not filter by calendar day.
        val inProgress = allSessions.firstOrNull { it.status == WorkoutSessionStatus.ACTIVE }
        val todayCompleted = allSessions.any { session ->
            session.status == WorkoutSessionStatus.COMPLETED &&
                session.splitDayId != null &&
                days.any { it.id == session.splitDayId } &&
                isSameCalendarDay(session.startedAt, today)
        }
        val weekStart = startOfWeekMillis(today)
        val weekSessions = allSessions.filter { it.status == WorkoutSessionStatus.COMPLETED && it.startedAt >= weekStart && it.startedAt <= today }
        val weekSessionIds = weekSessions.map { it.id }.toSet()
        val weekSetsList = allSets.filter { it.workoutSessionId in weekSessionIds }
        val weekSets = weekSetsList.size
        val weekVolume = StrengthFormulas.totalVolume(weekSetsList)

        HomeUiState(
            activeSplit = activeSplit,
            nextDay = planned,
            inProgressSession = inProgress,
            todayCompleted = todayCompleted,
            currentStreak = if (todaySkipped) 0 else streak.current,
            longestStreak = streak.longest,
            weekWorkouts = weekSessions.size,
            weekSets = weekSets,
            weekVolume = StrengthFormulas.displayRounded(weekVolume),
            settings = settings,
            scheduleNotStarted = scheduleNotStarted,
            todaySkipped = todaySkipped,
            loading = false
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                workoutRepository.archiveExpiredSessions()
                delay(60_000L)
            }
        }
        viewModelScope.launch {
            uiState.filter { !it.loading }.collect { state ->
                val milestone = STREAK_MILESTONES.lastOrNull {
                    it <= state.currentStreak && it > state.settings.lastCelebratedMilestone
                }
                if (milestone != null) {
                    settingsDataStore.setLastCelebratedMilestone(milestone)
                    _milestoneEvent.value = milestone
                }
            }
        }
    }

    fun consumeMilestoneEvent() {
        _milestoneEvent.value = null
    }

    fun skipTodayWorkout() {
        viewModelScope.launch {
            val split = uiState.value.activeSplit ?: return@launch
            if (uiState.value.inProgressSession != null || uiState.value.todayCompleted) return@launch
            settingsDataStore.skipWorkoutDay(split.id, startOfDayMillis(System.currentTimeMillis()))
        }
    }

    fun resetInProgressWorkout() {
        viewModelScope.launch {
            uiState.value.inProgressSession?.let { workoutRepository.abandonSession(it) }
        }
    }

    private fun isSameCalendarDay(firstMillis: Long, secondMillis: Long): Boolean {
        return WorkoutClock.isSameCalendarDay(firstMillis, secondMillis)
    }

    private fun startOfDayMillis(nowMillis: Long): Long = WorkoutClock.startOfDayMillis(nowMillis)

    private fun startOfWeekMillis(nowMillis: Long): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
