package dev.gouthaman.regimen.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.gouthaman.regimen.MainActivity
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.domain.di.ApplicationScope
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveActiveWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.PauseWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ResumeWorkoutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service backing an active workout (S13): keeps the process alive with a persistent,
 * pause-aware timer notification (Pause/Resume actions only - ending a workout is in-app only,
 * via Active Workout's Finish button). Started/stopped by [ActiveWorkoutServiceController].
 * Survives process death via START_STICKY (the workout itself lives in Room).
 */
@AndroidEntryPoint
class ActiveWorkoutService : Service() {

    @Inject
    lateinit var observeActiveWorkoutId: ObserveActiveWorkoutIdUseCase

    @Inject
    lateinit var observeWorkout: ObserveWorkoutUseCase

    @Inject
    lateinit var pauseWorkout: PauseWorkoutUseCase

    @Inject
    lateinit var resumeWorkout: ResumeWorkoutUseCase

    @Inject
    lateinit var getInProgressWorkoutId: GetInProgressWorkoutIdUseCase

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        observeSession()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSession() {
        observeActiveWorkoutId()
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else observeWorkout(id) }
            .onEach { workout ->
                if (workout == null || workout.workout.workoutStatus == WorkoutStatus.COMPLETE) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(this)
                        .notify(NOTIFICATION_ID, buildNotification(workout.workout))
                }
            }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Satisfy the 5s startForeground requirement immediately on (re)start.
        startForegroundCompat(buildNotification(null))

        // Resolve the workout id fresh from the DB (not a cached field) so notification actions
        // act on the real current session.
        val action = intent?.action
        if (action != null) {
            appScope.launch {
                val id = getInProgressWorkoutId() ?: return@launch
                when (action) {
                    ACTION_PAUSE -> pauseWorkout(id)
                    ACTION_RESUME -> resumeWorkout(id)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(workout: Workout?): android.app.Notification {
        val paused = workout?.workoutStatus == WorkoutStatus.PAUSED
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            // Deep-links straight into this session's Active Workout instead of wherever the app
            // last was (see MainActivity.EXTRA_WORKOUT_ID / RegimenApp's deepLinkWorkoutId).
            if (workout != null) putExtra(MainActivity.EXTRA_WORKOUT_ID, workout.id)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Workout in progress")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (workout != null) {
            if (paused) {
                builder.setContentText("Paused")
                builder.setUsesChronometer(false)
            } else {
                // Chronometer counts up from a base that excludes accumulated pause time.
                builder.setWhen(workout.startTime + workout.accumulatedPausedMs)
                builder.setUsesChronometer(true)
            }
            builder.addAction(
                0,
                if (paused) "Resume" else "Pause",
                servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE),
            )
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, ActiveWorkoutService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active workout",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Ongoing workout timer and controls" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "active_workout"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_PAUSE = "dev.gouthaman.regimen.action.PAUSE"
        const val ACTION_RESUME = "dev.gouthaman.regimen.action.RESUME"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ActiveWorkoutService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ActiveWorkoutService::class.java))
        }
    }
}
