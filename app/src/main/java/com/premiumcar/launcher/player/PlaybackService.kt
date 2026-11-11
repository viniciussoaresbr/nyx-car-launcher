package com.premiumcar.launcher.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.premiumcar.launcher.R

/**
 * Mantém o processo em primeiro plano **somente enquanto a música toca** (o Android exige
 * uma notificação nesse período). Ao pausar, a notificação some e o serviço termina.
 * Primeira exceção à regra "sem Service" (ARCHITECTURE.md, D5).
 */
class PlaybackService : Service() {

    private val listener: () -> Unit = { refresh() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MusicPlayer.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8+: depois de startForegroundService, startForeground é obrigatório, mesmo
        // que a música já tenha parado nesse meio-tempo.
        startForeground(NOTIFICATION_ID, buildNotification(this))
        when (intent?.action) {
            ACTION_PREVIOUS -> MusicPlayer.previous()
            ACTION_TOGGLE -> MusicPlayer.togglePlayPause()
            ACTION_NEXT -> MusicPlayer.next()
        }
        if (!MusicPlayer.state.playing) stopNow()
        return START_NOT_STICKY
    }

    private fun refresh() {
        if (!MusicPlayer.state.playing) {
            stopNow()
            return
        }
        runCatching { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(this)) }
    }

    private fun stopNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        MusicPlayer.removeListener(listener)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback"
        private const val ACTION_PREVIOUS = "com.premiumcar.launcher.player.PREVIOUS"
        private const val ACTION_TOGGLE = "com.premiumcar.launcher.player.TOGGLE"
        private const val ACTION_NEXT = "com.premiumcar.launcher.player.NEXT"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            }.onFailure { PlayerLog.add("Serviço de reprodução não iniciou: ${it.javaClass.simpleName}") }
        }

        internal fun buildNotification(context: Context): Notification {
            val state = MusicPlayer.state
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ensureChannel(context)
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            val style = Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2)
            MusicPlayer.sessionToken?.let { style.setMediaSession(it) }
            return builder
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(state.title ?: context.getString(R.string.card_music_title))
                .setContentText(state.artist ?: state.folderLabel)
                .setLargeIcon(state.cover)
                .setContentIntent(MusicPlayer.openPlayerIntent(context))
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(context, R.drawable.ic_skip_previous, R.string.music_previous, ACTION_PREVIOUS))
                .addAction(
                    if (state.playing) {
                        action(context, R.drawable.ic_pause, R.string.music_pause, ACTION_TOGGLE)
                    } else {
                        action(context, R.drawable.ic_play, R.string.music_play, ACTION_TOGGLE)
                    },
                )
                .addAction(action(context, R.drawable.ic_skip_next, R.string.music_next, ACTION_NEXT))
                .setStyle(style)
                .build()
        }

        private fun action(context: Context, icon: Int, label: Int, action: String): Notification.Action {
            val intent = PendingIntent.getService(
                context,
                action.hashCode(),
                Intent(context, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), intent).build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.player_notification_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
            )
        }
    }
}
