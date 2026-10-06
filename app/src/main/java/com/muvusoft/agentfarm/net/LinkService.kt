package com.muvusoft.agentfarm.net

import android.app.Notification
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
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.widget.StatusSurfaces
import com.muvusoft.agentfarm.core.speech.Speech
import com.muvusoft.agentfarm.core.notify.AlertBook
import com.muvusoft.agentfarm.core.notify.Alerts
import com.muvusoft.agentfarm.core.notify.Channel
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
    private var alerts: Job? = null
    private var poll: Job? = null
    private val book = AlertBook()
    private val prefs by lazy { ShellPrefs(this) }
    private val speaker: Speaker get() = AgentFarmApp.of(this).speaker

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = AgentFarmApp.of(this)
        AlertPoster.channels(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(StatusLine.of(app.manager.state.value, System.currentTimeMillis())), type())
        app.manager.setFarms(app.store.load())
        if (alerts == null) {
            alerts = app.scope.launch {
                app.manager.frames.collect { f ->
                    if (f.frame is Welcome) refreshRunning(f.farm)
                    val ev = f.frame as? EventFrame ?: return@collect
                    val data = Codec.eventData(ev) ?: return@collect
                    val name = app.manager.state.value.farm(f.farm)?.farm?.name ?: f.farm
                    book.settle(f.farm, data).forEach { AlertPoster.cancel(this@LinkService, it) }
                    if (data is TurnFinished || data is SessionEnded || data is AskOpened) refreshRunning(f.farm)
                    Alerts.of(name, data)?.let {
                        AlertPoster.post(this@LinkService, f.farm, it)
                        book.posted(f.farm, data, it)
                        Speech.of(it, prefs.speakMode, (data as? TurnFinished)?.summary)?.let(speaker::say)
                    }
                }
            }
        }
        if (job == null) {
            job = app.scope.launch {
                app.manager.state.collectLatest { s ->
                    // Re-drawn on every change and once a minute so "last turn … ago" stays true.
                    while (true) {
                        val text = StatusLine.of(s, System.currentTimeMillis())
                        notify(text)
                        StatusSurfaces.show(this@LinkService, text)
                        delay(60_000)
                    }
                }
            }
        }
        // A turn that starts sends no event: the running count is also asked once a minute.
        if (poll == null) {
            poll = app.scope.launch {
                while (true) {
                    app.manager.state.value.farms.filter { it.link is Link.Online }.forEach { refreshRunning(it.farm.id) }
                    delay(60_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        alerts?.cancel()
        alerts = null
        poll?.cancel()
        poll = null
        speaker.shutdown()
        super.onDestroy()
    }

    private fun refreshRunning(farmId: String) {
        val app = AgentFarmApp.of(this)
        app.scope.launch {
            val r = app.manager.request(farmId, "console.sessions") ?: return@launch
            if (r.ok) app.manager.running(farmId, Share.targets(r.result).count { it.status == "running" })
        }
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
        val CHANNEL = Channel.LINK.id
        const val NOTIFICATION_ID = 1

        /** Runs the service while any farm is paired, stops it when none is. */
        fun sync(context: Context, anyFarm: Boolean) {
            val intent = Intent(context, LinkService::class.java)
            if (anyFarm) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
