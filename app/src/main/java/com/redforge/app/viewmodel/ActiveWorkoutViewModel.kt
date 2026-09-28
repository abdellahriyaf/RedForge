package com.redforge.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.datastore.SettingsDataStore
import com.redforge.app.data.datastore.WeightUnit
import com.redforge.app.data.local.entities.*
import com.redforge.app.data.repository.ExerciseRepository
import com.redforge.app.data.repository.SplitRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.formulas.StrengthFormulas
import com.redforge.app.domain.schedule.SplitScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

private const val DELOAD_REMAINING_VOLUME_FACTOR = 0.60

data class WorkoutExerciseBlock(
    val dayExercise: SplitDayExercise,
    val exercise: Exercise,
    val loggedSets: List<SetEntry>,
    val lastPreviousSet: SetEntry?,
    val targetSetsForToday: Int,
    val restSecondsForToday: Int
)

data class PrCelebration(val exerciseName: String, val newEstimated1RM: Int, val previousBest: Int)

data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null,
    val blocks: List<WorkoutExerciseBlock> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    val isDeloadCycle: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val isFinishing: Boolean = false,
    val finished: Boolean = false
)

class ActiveWorkoutViewModel(
    private val splitRepository: SplitRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val _prEvent = MutableStateFlow<PrCelebration?>(null)
    val prEvent: StateFlow<PrCelebration?> = _prEvent.asStateFlow()

    private val _lastLoggedSetTriggersRest = MutableStateFlow<Int?>(null)
    val restTriggerSeconds: StateFlow<Int?> = _lastLoggedSetTriggersRest.asStateFlow()

    // Serializes fast double-taps so two Room writes cannot receive the same
    // set index from stale UI state.
    private val sessionWriteMutex = Mutex()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            _uiState.value = _uiState.value.copy(
                weightUnit = settings.weightUnit,
                defaultRestSeconds = settings.defaultRestSeconds
            )
            resumeOrStart()
        }
    }

    private suspend fun resumeOrStart() {
        val activeSplit = splitRepository.observeActiveSplit().first()
        val isDeload = activeSplit?.isDeloadCycle ?: false
        _uiState.value = _uiState.value.copy(isDeloadCycle = isDeload)

        val existing = workoutRepository.getInProgressSession()
        val session = existing ?: run {
            if (activeSplit == null) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "No active split. Build and activate a split first."
                )
                return
            }

            val days = splitRepository.observeDays(activeSplit.id).first()
            if (days.none { !it.isRestDay }) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Your active split has no training days yet."
                )
                return
            }

            val allSessions = workoutRepository.observeAllSessions().first()
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val skippedToday = settings.skippedSplitId == activeSplit.id &&
                settings.skippedWorkoutDayStartMillis == todayStart
            if (skippedToday) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Today's workout was skipped. The next scheduled session will be available tomorrow."
                )
                return
            }
            val scheduleAnchor = settings.scheduleAnchorStartMillis.takeIf {
                it != null && settings.scheduleAnchorSplitId == activeSplit.id
            }
            val plannedDay = SplitScheduler.nextDay(
                days,
                allSessions,
                scheduleAnchorStartMillis = scheduleAnchor
            )
            if (plannedDay == null || plannedDay.isRestDay) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Today is a planned rest day. Enjoy the recovery."
                )
                return
            }
            val newId = workoutRepository.startSession(plannedDay.id, plannedDay.name)
            workoutRepository.getSession(newId) ?: run {
                _uiState.value = _uiState.value.copy(loading = false, error = "Couldn't start the workout.")
                return
            }
        }
        observeSession(session)
    }

    private fun observeSession(session: WorkoutSession) {
        viewModelScope.launch {
            val dayExercises = session.splitDayId?.let { splitRepository.getDayExercisesOnce(it) }.orEmpty()
            combine(
                workoutRepository.observeSets(session.id),
                settingsDataStore.settingsFlow
            ) { sets, settings -> sets to settings }
                .collect { (sets, settings) ->
                    val blocks = dayExercises.sortedBy { it.orderIndex }.mapNotNull { dayExercise ->
                        val exercise = exerciseRepository.getById(dayExercise.exerciseId) ?: return@mapNotNull null
                        val loggedForThis = sets.filter { it.exerciseId == dayExercise.exerciseId }.sortedBy { it.setIndex }
                        val recentPrevious = workoutRepository.getRecentSetsForExercise(dayExercise.exerciseId, 1)
                            .firstOrNull()
                        val targetSets = if (_uiState.value.isDeloadCycle) {
                            maxOf(1, ceil(dayExercise.targetSets * DELOAD_REMAINING_VOLUME_FACTOR).toInt())
                        } else {
                            dayExercise.targetSets
                        }
                        WorkoutExerciseBlock(
                            dayExercise = dayExercise,
                            exercise = exercise,
                            loggedSets = loggedForThis,
                            lastPreviousSet = recentPrevious,
                            targetSetsForToday = targetSets,
                            restSecondsForToday = if (dayExercise.targetRestSeconds > 0) {
                                dayExercise.targetRestSeconds
                            } else {
                                settings.defaultRestSeconds
                            }
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        session = session,
                        blocks = blocks,
                        weightUnit = settings.weightUnit,
                        defaultRestSeconds = settings.defaultRestSeconds,
                        loading = false
                    )
                }
        }
    }

    fun logSet(exerciseId: Long, weight: Double, reps: Int, isWarmup: Boolean, rpe: Float?) {
        if (weight < 0.0 || reps <= 0) return
        val session = _uiState.value.session ?: return
        val block = _uiState.value.blocks.firstOrNull { it.exercise.id == exerciseId } ?: return

        viewModelScope.launch {
            sessionWriteMutex.withLock {
                val nextIndex = workoutRepository.getMaxSetIndex(session.id, exerciseId) + 1

                var isPr = false
                if (!isWarmup) {
                    val completedHistory = workoutRepository.getRecentSetsForExercise(exerciseId, 1000)
                    val currentSessionSets = workoutRepository.getSetsOnce(session.id)
                        .filter { it.exerciseId == exerciseId && !it.isWarmup }
                    val previousBest = StrengthFormulas.bestEstimated1RM(completedHistory + currentSessionSets)
                    val newEstimate = StrengthFormulas.estimated1RM(weight, reps)
                    if (previousBest > 0.0 && newEstimate > previousBest) {
                        isPr = true
                        _prEvent.value = PrCelebration(
                            exerciseName = block.exercise.name,
                            newEstimated1RM = StrengthFormulas.displayRounded(newEstimate),
                            previousBest = StrengthFormulas.displayRounded(previousBest)
                        )
                    }
                }

                workoutRepository.logSet(
                    SetEntry(
                        workoutSessionId = session.id,
                        exerciseId = exerciseId,
                        setIndex = nextIndex,
                        weight = weight,
                        reps = reps,
                        isWarmup = isWarmup,
                        rpe = rpe,
                        isPersonalRecord = isPr
                    )
                )

                if (!isWarmup && isLastInSupersetGroup(block.dayExercise)) {
                    _lastLoggedSetTriggersRest.value = block.restSecondsForToday
                }
            }
        }
    }

    private fun isLastInSupersetGroup(dayExercise: SplitDayExercise): Boolean {
        val group = dayExercise.supersetGroup ?: return true
        val groupMembers = _uiState.value.blocks.map { it.dayExercise }.filter { it.supersetGroup == group }
        val maxOrder = groupMembers.maxOfOrNull { it.orderIndex } ?: dayExercise.orderIndex
        return dayExercise.orderIndex == maxOrder
    }

    fun consumePrEvent() {
        _prEvent.value = null
    }

    fun consumeRestTrigger() {
        _lastLoggedSetTriggersRest.value = null
    }

    fun deleteSet(set: SetEntry) {
        viewModelScope.launch {
            sessionWriteMutex.withLock { workoutRepository.deleteSetAndReindex(set) }
        }
    }

    fun estimatedOneRepMax(sets: List<SetEntry>): Int =
        StrengthFormulas.displayRounded(StrengthFormulas.bestEstimated1RM(sets))

    fun hasWorkingSets(): Boolean = _uiState.value.blocks.any { block -> block.loggedSets.any { !it.isWarmup } }

    fun finishWorkout() {
        val session = _uiState.value.session ?: return
        if (_uiState.value.isFinishing) return

        _uiState.value = _uiState.value.copy(
            isFinishing = true,
            error = null
        )

        viewModelScope.launch {
            try {
                sessionWriteMutex.withLock {
                    workoutRepository.completeSession(session.id)
                }

                _uiState.value = _uiState.value.copy(
                    isFinishing = false,
                    finished = true
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFinishing = false,
                    error = "Couldn't finish this workout. Your logged sets were kept safe."
                )
            }
        }
    }

    fun discardWorkout() {
        val session = _uiState.value.session ?: return
        if (_uiState.value.isFinishing) return

        _uiState.value = _uiState.value.copy(
            isFinishing = true,
            error = null
        )

        viewModelScope.launch {
            try {
                sessionWriteMutex.withLock {
                    workoutRepository.deleteSession(session)
                }

                _uiState.value = _uiState.value.copy(
                    isFinishing = false,
                    finished = true
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFinishing = false,
                    error = "Couldn't discard this workout."
                )
            }
        }
    }
}
