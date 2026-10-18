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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.MainActivity
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.away.AwayVerdict
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.EventFrame
import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.handsfree.HandsFree
import com.muvusoft.agentfarm.core.link.Discovery
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.widget.StatusSurfaces
import com.muvusoft.agentfarm.core.speech.SpeakMode
import com.muvusoft.agentfarm.core.speech.Speech
import com.muvusoft.agentfarm.core.notify.AlertBook
import com.muvusoft.agentfarm.core.notify.Channel
import com.muvusoft.agentfarm.core.notify.StatusLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Keeps the farm sockets alive while the app is not on screen — an idle one rests while its farm can wake the
 * phone (LinkRestKeeper) — and shows what is connected and what waits in one quiet, ongoing notification. Runs
 * while at least one farm is paired.
 */
class LinkService : Service() {
    private var job: Job? = null
    private var alerts: Job? = null
    private var poll: Job? = null
    private var rest: Job? = null
    private var network: NetworkWatch? = null
    private var finder: FarmFinder? = null
    private var seek: Job? = null
    private val running = HashMap<String, Job>()
    private val book = AlertBook()
    private val prefs by lazy { ShellPrefs(this) }
    private val speaker: Speaker get() = AgentFarmApp.of(this).speaker

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = AgentFarmApp.of(this)
        AlertPoster.channels(this)
        foreground(build(StatusLine.of(app.manager.state.value, System.currentTimeMillis())), app.visible.on.value)
        app.manager.setFarms(app.store.load())
        if (network == null) network = NetworkWatch(this) { app.manager.onNetwork(it) }.also { it.start() }
        if (seek == null) {
            val f = FarmFinder(this) { farm, address -> app.manager.onFound(farm, address) }
            finder = f
            // Multicast costs battery: the network is asked only while a farm is out of reach.
            seek = app.scope.launch {
                app.manager.state.map { Discovery.wanted(it) }.distinctUntilChanged().collect { if (it) f.start() else f.stop() }
            }
        }
        if (alerts == null) {
            alerts = app.scope.launch {
                app.manager.frames.collect { f ->
                    if (f.frame is Welcome) {
                        refreshAway(f.farm)
                        refreshRunning(f.farm)
                    }
                    val ev = f.frame as? EventFrame ?: return@collect
                    val data = Codec.eventData(ev) ?: return@collect
                    val name = app.manager.state.value.farm(f.farm)?.farm?.name ?: f.farm
                    book.settle(f.farm, data).forEach { AlertPoster.cancel(this@LinkService, it) }
                    if (data is TurnFinished || data is SessionEnded || data is AskOpened) refreshRunning(f.farm)
                    book.alertFor(f.farm, name, data)?.let {
                        AlertPoster.post(this@LinkService, f.farm, it)
                        book.posted(f.farm, data, it)
                        val spoken = Speech.spokenOf(data)
                        // The headset mode reads every question it may answer, whatever Read aloud says.
                        val loop = data is AskOpened && Speech.of(it, SpeakMode.ASKS, null, spoken)?.let { u -> app.handsFree.ask(f.farm, u, data) } == true
                        if (!loop) Speech.of(it, prefs.speakMode, (data as? TurnFinished)?.summary, spoken)?.let(speaker::say)
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
                    // A share queued for a farm that never answers expires here, and its receipt says so.
                    app.manager.expire()
                    delay(60_000)
                }
            }
        }
        // An idle socket rests and opens again on a push wake, the app, an outbox item or a sparse check (core/power/LinkRest).
        if (rest == null) rest = app.scope.launch { LinkRestKeeper(app.manager, app.visible.on, pushToken = { app.prefs.pushToken }).run() }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        alerts?.cancel()
        alerts = null
        poll?.cancel()
        poll = null
        rest?.cancel()
        rest = null
        synchronized(running) {
            running.values.forEach { it.cancel() }
            running.clear()
        }
        network?.stop()
        network = null
        seek?.cancel()
        seek = null
        finder?.stop()
        finder = null
        speaker.shutdown()
        super.onDestroy()
    }

    /** Agent Farm itself, or its headless service while VS Code is closed? An older farm without the op is running. */
    private fun refreshAway(farmId: String) {
        val app = AgentFarmApp.of(this)
        app.scope.launch {
            val r = app.manager.request(farmId, "farm.away") ?: return@launch
            app.manager.away(farmId, if (r.ok) AwayVerdict.of(r.result) else null)
        }
    }

    /**
     * The running count and the share targets. Asked once for a burst (a welcome, a turn's end and the question it
     * opens arrive together), as a read (outside the outbox: an answer nobody waits for is never sent again later),
     * and as the summary rows, the fields Share.targets reads.
     */
    private fun refreshRunning(farmId: String) {
        val app = AgentFarmApp.of(this)
        synchronized(running) {
            running[farmId]?.cancel()
            running[farmId] = app.scope.launch {
                delay(BURST_MS)
                val r = app.manager.read(farmId, "console.sessions", buildJsonObject { put("view", JsonPrimitive("summary")) }) ?: return@launch
                if (r.ok) app.manager.sessions(farmId, Share.targets(r.result))
            }
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

    /** In the foreground, with the microphone while the headset mode may have it (core/handsfree/HandsFree). */
    private fun foreground(n: Notification, visible: Boolean) {
        val base = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        val mic = Build.VERSION.SDK_INT >= 30 && HandsFree.asksMicrophone(prefs.handsFree, Headset.microphone(this), visible)
        if (!mic) return ServiceCompat.startForeground(this, NOTIFICATION_ID, n, base)
        // Refused all the same (the app left the screen as the service started): the link goes on without it.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, base or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: RuntimeException) {
            Log.w(TAG, "the microphone was refused: $e")
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, base)
        }
    }

    companion object {
        val CHANNEL = Channel.LINK.id
        const val NOTIFICATION_ID = 1
        private const val TAG = "AFLink"

        /** How long a burst of the farm's frames is waited out before the running sessions are asked once. */
        private const val BURST_MS = 400L

        /** Runs the service while any farm is paired, stops it when none is. */
        fun sync(context: Context, anyFarm: Boolean) {
            val intent = Intent(context, LinkService::class.java)
            if (anyFarm) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
