package com.redforge.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redforge.app.data.datastore.SettingsDataStore
import com.redforge.app.data.local.entities.Split
import com.redforge.app.data.local.entities.SplitDay
import com.redforge.app.data.repository.SplitRepository
import com.redforge.app.data.repository.WorkoutRepository
import com.redforge.app.domain.schedule.SplitTemplate
import com.redforge.app.util.WidgetRefreshUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class SplitActivationPrompt(val split: Split)

class SplitListViewModel(
    private val repository: SplitRepository,
    private val workoutRepository: WorkoutRepository,
    private val appContext: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _activationPrompt = MutableStateFlow<SplitActivationPrompt?>(null)
    val activationPrompt = _activationPrompt.asStateFlow()

    val splits: StateFlow<List<Split>> = repository.observeAllSplits()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setActive(split: Split) {
        if (split.isActive) {
            viewModelScope.launch {
                repository.clearActiveSplit()
                settingsDataStore.clearScheduleAnchor()
                WidgetRefreshUtil.request(appContext)
            }
        } else {
            _activationPrompt.value = SplitActivationPrompt(split)
        }
    }

    fun confirmActivation(split: Split, startDateMillis: Long) {
        viewModelScope.launch {
            activate(split, startOfDay(startDateMillis))
            _activationPrompt.value = null
        }
    }

    fun dismissActivationPrompt() {
        _activationPrompt.value = null
    }

    private suspend fun activate(split: Split, anchorStartMillis: Long) {
        repository.setActiveSplit(split.id)
        settingsDataStore.setScheduleAnchor(split.id, anchorStartMillis)
        settingsDataStore.clearSkippedWorkoutDay()
        WidgetRefreshUtil.request(appContext)
    }

    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun createSplit(name: String, template: SplitTemplate, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.saveSplit(Split(name = name, daysPerCycle = template.days.size))
            template.days.forEachIndexed { index, day ->
                repository.saveDay(
                    SplitDay(
                        splitId = id,
                        name = day.name,
                        dayOrder = index + 1,
                        isRestDay = day.isRestDay
                    )
                )
            }
            WidgetRefreshUtil.request(appContext)
            onCreated(id)
        }
    }

    fun deleteSplit(split: Split) {
        viewModelScope.launch {
            if (split.isActive) settingsDataStore.clearScheduleAnchor()
            repository.deleteSplit(split)
            WidgetRefreshUtil.request(appContext)
        }
    }

    fun renameSplit(split: Split, newName: String) {
        viewModelScope.launch {
            repository.saveSplit(split.copy(name = newName))
            WidgetRefreshUtil.request(appContext)
        }
    }
}
