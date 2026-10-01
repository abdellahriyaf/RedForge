package com.redforge.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.local.entities.Exercise
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.data.repository.ExerciseRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.formulas.StrengthFormulas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

data class ExerciseProgressSummary(
    val exercise: Exercise,
    val bestEstimated1RM: Int,
    val totalVolumeAllTime: Int,
    val volumeChangePercent: Double,
    val sessionCount: Int
)

data class ExerciseTrendPoint(
    val sessionId: Long,
    val date: Long,
    val estimated1RM: Int,
    val volume: Int
)

class ProgressViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val summaries: StateFlow<List<ExerciseProgressSummary>> = exerciseRepository.observeAll()
        .flatMapLatest { exercises ->
            if (exercises.isEmpty()) flowOf(emptyList())
            else combine(exercises.map { ex -> workoutRepository.observeAllSetsForExercise(ex.id).map { ex to it } }) { pairs ->
                pairs.mapNotNull { (ex, sets) -> buildSummary(ex, sets) }
                    .sortedByDescending { it.totalVolumeAllTime }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun buildSummary(exercise: Exercise, sets: List<SetEntry>): ExerciseProgressSummary? {
        if (sets.isEmpty()) return null
        val sorted = sets.sortedBy { it.loggedAt }
        val midpoint = sorted.size / 2
        val earlierHalf = sorted.take(maxOf(midpoint, 1))
        val recentHalf = sorted.drop(midpoint)
        val workingSets = sets.filter { !it.isWarmup }
        val volumeChange = StrengthFormulas.percentChange(
            StrengthFormulas.totalVolume(earlierHalf),
            StrengthFormulas.totalVolume(recentHalf.ifEmpty { earlierHalf })
        )
        val sessionCount = sorted.asSequence().map { it.workoutSessionId }.distinct().count()
        return ExerciseProgressSummary(
            exercise = exercise,
            bestEstimated1RM = StrengthFormulas.displayRounded(StrengthFormulas.bestEstimated1RM(workingSets)),
            totalVolumeAllTime = StrengthFormulas.displayRounded(StrengthFormulas.totalVolume(workingSets)),
            volumeChangePercent = volumeChange,
            sessionCount = sessionCount
        )
    }
}

class ExerciseProgressDetailViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseId: Long
) : ViewModel() {

    data class UiState(
        val exercise: Exercise? = null,
        val points: List<ExerciseTrendPoint> = emptyList(),
        val bestEstimated1RM: Int = 0,
        val allTimeVolume: Int = 0,
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId)
            if (exercise == null) {
                _uiState.value = UiState(
                    loading = false,
                    error = "Exercise not found."
                )
                return@launch
            }

            combine(
                workoutRepository.observeAllSessions(),
                workoutRepository.observeAllSetsForExercise(exerciseId)
            ) { allSessions, allSets ->
                exercise to Pair(allSessions, allSets)
            }.flowOn(Dispatchers.Default).collect { (currentExercise, data) ->
                val (allSessions, allSets) = data
                val sessions = allSessions
                    .filter { it.status == WorkoutSessionStatus.COMPLETED }
                    .associateBy { it.id }

                val sets = allSets
                    .filter { sessions.containsKey(it.workoutSessionId) }
                    .filter { !it.isWarmup }
                    .groupBy { it.workoutSessionId }

                val points = sets.mapNotNull { (sessionId, sessionSets) ->
                    val session = sessions[sessionId] ?: return@mapNotNull null
                    ExerciseTrendPoint(
                        sessionId = sessionId,
                        date = session.startedAt,
                        estimated1RM = StrengthFormulas.displayRounded(
                            StrengthFormulas.bestEstimated1RM(sessionSets)
                        ),
                        volume = StrengthFormulas.displayRounded(
                            StrengthFormulas.totalVolume(sessionSets)
                        )
                    )
                }.sortedBy { it.date }

                _uiState.value = UiState(
                    exercise = currentExercise,
                    points = points,
                    bestEstimated1RM = points.maxOfOrNull { it.estimated1RM } ?: 0,
                    allTimeVolume = points.sumOf { it.volume },
                    loading = false
                )
            }
        }
    }
}
