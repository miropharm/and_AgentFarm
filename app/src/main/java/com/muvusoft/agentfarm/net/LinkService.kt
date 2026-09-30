package com.muvusoft.agentfarm.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.MainActivity
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.notify.StatusLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the farm sockets alive while the app is not on screen, and shows what is connected and what
 * waits in one quiet, ongoing notification. Runs while at least one farm is paired.
 */
class LinkService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = AgentFarmApp.of(this)
        channel(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(StatusLine.of(app.manager.state.value, System.currentTimeMillis())), type())
        app.manager.setFarms(app.store.load())
        if (job == null) {
            job = app.scope.launch {
                app.manager.state.collectLatest { s ->
                    // Re-drawn on every change and once a minute so "son tur … önce" stays true.
                    while (true) {
                        notify(StatusLine.of(s, System.currentTimeMillis()))
                        delay(60_000)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        super.onDestroy()
    }

    private fun notify(text: StatusLine.Text) =
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(text))

    private fun build(text: StatusLine.Text): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_farm)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun type(): Int =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0

    companion object {
        const val CHANNEL = "link"
        const val NOTIFICATION_ID = 1

        fun channel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val ch = NotificationChannel(CHANNEL, "Bağlantı", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Çiftliklere bağlantının sürekli durum satırı"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** Runs the service while any farm is paired, stops it when none is. */
        fun sync(context: Context, anyFarm: Boolean) {
            val intent = Intent(context, LinkService::class.java)
            if (anyFarm) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
