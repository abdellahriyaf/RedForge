package com.redforge.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.local.entities.SetEntry
import com.redforge.app.data.local.entities.WorkoutSession
import com.redforge.app.data.local.entities.WorkoutSessionStatus
import com.redforge.app.data.repository.ExerciseRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.formulas.StrengthFormulas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

/** Summary row used by the History list. */
data class HistorySessionUi(
    val session: WorkoutSession,
    val setCount: Int,
    val workingSetCount: Int,
    val volume: Int
)

/** One exercise group inside a completed history session. */
data class HistoryExerciseUi(
    val exerciseId: Long,
    val name: String,
    val sets: List<SetEntry>
)

class HistoryViewModel(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val sessions: StateFlow<List<HistorySessionUi>> =
        combine(
            workoutRepository.observeAllSessions(),
            workoutRepository.observeAllSets()
        ) { sessions, allSets ->
            val setsBySession = allSets.groupBy { it.workoutSessionId }
            sessions
                .filter { it.status != WorkoutSessionStatus.ACTIVE }
                .map { session ->
                    val sets = setsBySession[session.id].orEmpty()
                    HistorySessionUi(
                        session = session,
                        setCount = sets.size,
                        workingSetCount = sets.count { !it.isWarmup },
                        volume = StrengthFormulas.displayRounded(
                            StrengthFormulas.totalVolume(sets)
                        )
                    )
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

class HistoryDetailViewModel(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val sessionId: Long
) : ViewModel() {

    data class UiState(
        val session: WorkoutSession? = null,
        val exercises: List<HistoryExerciseUi> = emptyList(),
        val totalVolume: Int = 0,
        val loading: Boolean = true,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            load()
        }
    }

    private suspend fun load() {
        val session = workoutRepository.getSession(sessionId)
        if (session == null || session.status == WorkoutSessionStatus.ACTIVE) {
            _uiState.value = UiState(
                loading = false,
                error = "That workout could not be found."
            )
            return
        }

        val sets = workoutRepository.getSetsOnce(session.id)
        val groups = sets
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, entries) ->
                val exercise = exerciseRepository.getById(exerciseId)
                    ?: return@mapNotNull null
                HistoryExerciseUi(
                    exerciseId = exerciseId,
                    name = exercise.name,
                    sets = entries.sortedBy { it.setIndex }
                )
            }
            .sortedBy { it.sets.firstOrNull()?.loggedAt ?: Long.MAX_VALUE }

        _uiState.value = UiState(
            session = session,
            exercises = groups,
            totalVolume = StrengthFormulas.displayRounded(
                StrengthFormulas.totalVolume(sets)
            ),
            loading = false
        )
    }

    fun updateSet(
        set: SetEntry,
        weight: Double,
        reps: Int,
        rpe: Float?
    ) {
        if (weight < 0.0 || reps <= 0) return

        viewModelScope.launch {
            workoutRepository.updateSet(
                set.copy(
                    weight = weight,
                    reps = reps,
                    rpe = rpe,
                    // A manual correction invalidates the original PR flag;
                    // current/future PRs are recalculated when a new set is logged.
                    isPersonalRecord = false
                )
            )
            load()
        }
    }

    fun deleteSet(set: SetEntry) {
        viewModelScope.launch {
            workoutRepository.deleteSetAndReindex(set)
            load()
        }
    }
}
