package com.redforge.app.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioManager
import android.media.ToneGenerator
import com.redforge.app.data.datastore.SettingsDataStore
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs the optional rest-timer as a real foreground service so it keeps
 * counting down (and keeps notifying the user) even if the app is
 * backgrounded or trimmed by the system — the timer itself is genuinely
 * "you can leave the app and it still works", independent of the workout
 * data safety guarantee (which comes from Room writes, not this service).
 */
class RestTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val ACTION_START = "com.redforge.app.timer.START"
        const val ACTION_PAUSE = "com.redforge.app.timer.PAUSE"
        const val ACTION_RESUME = "com.redforge.app.timer.RESUME"
        const val ACTION_ADD_15 = "com.redforge.app.timer.ADD_15"
        const val ACTION_SKIP = "com.redforge.app.timer.SKIP"
        const val ACTION_STOP = "com.redforge.app.timer.STOP"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"

        data class TimerUiState(
            val totalSeconds: Int = 0,
            val secondsRemaining: Int = 0,
            val isRunning: Boolean = false,
            val isPaused: Boolean = false,
            val finishedSignal: Long = 0L // bump to notify "just finished" once, for a UI ping/sound
        )

        private val _state = MutableStateFlow(TimerUiState())
        val state = _state.asStateFlow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getIntExtra(EXTRA_DURATION_SECONDS, 90)
                startForeground(TimerNotificationHelper.NOTIFICATION_ID, TimerNotificationHelper.build(this, duration, false))
                runTimer(duration)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_ADD_15 -> addSeconds(15)
            ACTION_SKIP -> finishTimer()
            ACTION_STOP -> stopSelfCleanly()
        }
        return START_NOT_STICKY
    }

    private fun runTimer(totalSeconds: Int, startingFrom: Int = totalSeconds) {
        countDownTimer?.cancel()
        _state.value = _state.value.copy(totalSeconds = totalSeconds, secondsRemaining = startingFrom, isRunning = true, isPaused = false)

        countDownTimer = object : CountDownTimer(startingFrom * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000L).toInt() + 1
                _state.value = _state.value.copy(secondsRemaining = remaining)
                updateNotification(remaining, false)
            }

            override fun onFinish() {
                finishTimer()
            }
        }.start()
    }

    private fun pause() {
        countDownTimer?.cancel()
        _state.value = _state.value.copy(isRunning = false, isPaused = true)
        updateNotification(_state.value.secondsRemaining, true)
    }

    private fun resume() {
        val remaining = _state.value.secondsRemaining
        if (remaining > 0) runTimer(_state.value.totalSeconds, remaining)
    }

    private fun addSeconds(extra: Int) {
        val newRemaining = _state.value.secondsRemaining + extra
        if (_state.value.isPaused) {
            _state.value = _state.value.copy(secondsRemaining = newRemaining)
            updateNotification(newRemaining, true)
        } else {
            runTimer(_state.value.totalSeconds + extra, newRemaining)
        }
    }

    private fun finishTimer() {
        countDownTimer?.cancel()
        _state.value = _state.value.copy(
            secondsRemaining = 0,
            isRunning = false,
            isPaused = false,
            finishedSignal = System.currentTimeMillis()
        )
        serviceScope.launch(Dispatchers.IO) {
            val settings = SettingsDataStore(applicationContext).settingsFlow.first()
            if (settings.timerVibrationEnabled) vibrateOnFinish()
            if (settings.timerSoundEnabled) playFinishSound()
            stopSelfCleanly()
        }
    }

    private fun vibrateOnFinish() {
        if (checkSelfPermission(Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 200, 100, 200)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun playFinishSound() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
        android.os.Handler(mainLooper).postDelayed({
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        }, 155L)
        android.os.Handler(mainLooper).postDelayed({
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 240)
        }, 355L)
        android.os.Handler(mainLooper).postDelayed({ tone.release() }, 650L)
    }

    private fun updateNotification(secondsRemaining: Int, isPaused: Boolean) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(
            TimerNotificationHelper.NOTIFICATION_ID,
            TimerNotificationHelper.build(this, secondsRemaining, isPaused)
        )
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
