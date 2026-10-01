package com.redforge.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.repository.SplitRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.formulas.StrengthFormulas
import com.redforge.app.domain.streak.StreakCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ShareScope { DAY, MONTH, YEAR }
enum class ShareTemplate { SUMMARY, STREAK, VOLUME }

data class ShareSummary(
    val scope: ShareScope,
    val workoutsCompleted: Int,
    val totalSets: Int,
    val totalVolume: Int,
    val currentStreak: Int,
    val splitName: String
)

class ShareViewModel(
    private val workoutRepository: WorkoutRepository,
    private val splitRepository: SplitRepository
) : ViewModel() {

    private val _summary = MutableStateFlow<ShareSummary?>(null)
    val summary: StateFlow<ShareSummary?> = _summary.asStateFlow()

    fun load(scope: ShareScope) {
        viewModelScope.launch {
            val (from, to) = rangeFor(scope)
            val sessions = workoutRepository
                .getSessionsBetween(from, to)
                .filter { it.status == WorkoutSessionStatus.COMPLETED }

            var totalSets = 0
            var totalVolume = 0.0

            sessions.forEach { session ->
                val sets = workoutRepository.getSetsOnce(session.id)
                totalSets += sets.count { !it.isWarmup }
                totalVolume += StrengthFormulas.totalVolume(sets.filter { !it.isWarmup })
            }

            val allSessions = workoutRepository.observeAllSessions().first()
            val activeSplit = splitRepository.observeActiveSplit().first()
            val streak = StreakCalculator.compute(allSessions)

            _summary.value = ShareSummary(
                scope = scope,
                workoutsCompleted = sessions.size,
                totalSets = totalSets,
                totalVolume = StrengthFormulas.displayRounded(totalVolume),
                currentStreak = streak.current,
                splitName = activeSplit?.name ?: "RedForge"
            )
        }
    }

    private fun rangeFor(scope: ShareScope): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis
        when (scope) {
            ShareScope.DAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            ShareScope.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            ShareScope.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
        }
        return cal.timeInMillis to to
    }
}
