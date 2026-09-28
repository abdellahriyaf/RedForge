package com.redforge.app.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "redforge_settings")

enum class WeightUnit { KG, LB }

data class ForgeSettings(
    val privacyPolicyAccepted: Boolean = false,
    val onboardingTutorialSeen: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val defaultRestSeconds: Int = 90,
    val darkThemeForced: Boolean = true,
    val timerSoundEnabled: Boolean = true,
    val timerVibrationEnabled: Boolean = true,
    val lastCelebratedMilestone: Int = 0,
    val scheduleAnchorSplitId: Long? = null,
    val scheduleAnchorStartMillis: Long? = null,
    val skippedSplitId: Long? = null,
    val skippedWorkoutDayStartMillis: Long? = null
)

/**
 * Small local-only preferences. Workout history remains in Room; derived
 * streak values are calculated from that source of truth instead of being
 * cached back into DataStore.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        val DARK_THEME_FORCED = booleanPreferencesKey("dark_theme_forced")
        val TIMER_SOUND = booleanPreferencesKey("timer_sound")
        val TIMER_VIBRATION = booleanPreferencesKey("timer_vibration")
        val LAST_CELEBRATED_MILESTONE = intPreferencesKey("last_celebrated_milestone")
        val SCHEDULE_ANCHOR_SPLIT_ID = longPreferencesKey("schedule_anchor_split_id")
        val SCHEDULE_ANCHOR_START_MILLIS = longPreferencesKey("schedule_anchor_start_millis")
        val SKIPPED_SPLIT_ID = longPreferencesKey("skipped_split_id")
        val SKIPPED_WORKOUT_DAY_START_MILLIS = longPreferencesKey("skipped_workout_day_start_millis")
    }

    val settingsFlow: Flow<ForgeSettings> = context.dataStore.data.map { prefs ->
        ForgeSettings(
            privacyPolicyAccepted = prefs[Keys.PRIVACY_ACCEPTED] ?: false,
            onboardingTutorialSeen = prefs[Keys.ONBOARDING_SEEN] ?: false,
            weightUnit = prefs[Keys.WEIGHT_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
            defaultRestSeconds = (prefs[Keys.DEFAULT_REST_SECONDS] ?: 90).coerceIn(15, 300),
            darkThemeForced = prefs[Keys.DARK_THEME_FORCED] ?: true,
            timerSoundEnabled = prefs[Keys.TIMER_SOUND] ?: true,
            timerVibrationEnabled = prefs[Keys.TIMER_VIBRATION] ?: true,
            lastCelebratedMilestone = prefs[Keys.LAST_CELEBRATED_MILESTONE] ?: 0,
            scheduleAnchorSplitId = prefs[Keys.SCHEDULE_ANCHOR_SPLIT_ID],
            scheduleAnchorStartMillis = prefs[Keys.SCHEDULE_ANCHOR_START_MILLIS],
            skippedSplitId = prefs[Keys.SKIPPED_SPLIT_ID],
            skippedWorkoutDayStartMillis = prefs[Keys.SKIPPED_WORKOUT_DAY_START_MILLIS]
        )
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_ACCEPTED] = accepted }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { it[Keys.WEIGHT_UNIT] = unit.name }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_REST_SECONDS] = seconds.coerceIn(15, 300) }
    }

    suspend fun setDarkThemeForced(forced: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME_FORCED] = forced }
    }

    suspend fun setTimerSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIMER_SOUND] = enabled }
    }

    suspend fun setTimerVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIMER_VIBRATION] = enabled }
    }

    suspend fun setLastCelebratedMilestone(milestone: Int) {
        context.dataStore.edit { it[Keys.LAST_CELEBRATED_MILESTONE] = milestone }
    }

    suspend fun setScheduleAnchor(splitId: Long, startOfDayMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_ANCHOR_SPLIT_ID] = splitId
            prefs[Keys.SCHEDULE_ANCHOR_START_MILLIS] = startOfDayMillis
        }
    }

    suspend fun skipWorkoutDay(splitId: Long, dayStartMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SKIPPED_SPLIT_ID] = splitId
            prefs[Keys.SKIPPED_WORKOUT_DAY_START_MILLIS] = dayStartMillis
        }
    }

    suspend fun clearSkippedWorkoutDay() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SKIPPED_SPLIT_ID)
            prefs.remove(Keys.SKIPPED_WORKOUT_DAY_START_MILLIS)
        }
    }

    suspend fun clearScheduleAnchor() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SCHEDULE_ANCHOR_SPLIT_ID)
            prefs.remove(Keys.SCHEDULE_ANCHOR_START_MILLIS)
        }
    }
}
