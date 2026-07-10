package dev.gouthaman.regimen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires the rest-complete alert (S14): vibration + default notification sound + a system
 * notification. Vibration/sound need no runtime permission; the notification shows on Android
 * <13 always, and 13+ once POST_NOTIFICATIONS is granted (wired in Phase 3).
 */
@Singleton
class RestAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    init {
        // Two channels, not one: on Android 8+ notification sound is a channel property, not
        // per-notification, so gating playChime() alone left the channel's default sound playing
        // regardless of the preference. notifyDone() picks the channel matching chimeEnabled.
        val manager = context.getSystemService(NotificationManager::class.java)
        val soundChannel = NotificationChannel(
            CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alerts when a rest period ends" }
        val silentChannel = NotificationChannel(
            CHANNEL_ID_SILENT,
            "Rest timer (silent)",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when a rest period ends (no sound)"
            setSound(null, null)
        }
        manager?.createNotificationChannel(soundChannel)
        manager?.createNotificationChannel(silentChannel)
    }

    fun fire(chimeEnabled: Boolean = true) {
        vibrate()
        if (chimeEnabled) playChime()
        notifyDone(chimeEnabled)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun playChime() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        }
    }

    private fun notifyDone(chimeEnabled: Boolean) {
        val channelId = if (chimeEnabled) CHANNEL_ID else CHANNEL_ID_SILENT
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest complete")
            .setContentText("Time for your next set.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "rest_timer"
        private const val CHANNEL_ID_SILENT = "rest_timer_silent"
        private const val NOTIFICATION_ID = 2001
    }
}
