package com.muvusoft.agentfarm.net

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.BuildConfig
import com.muvusoft.agentfarm.core.link.PushWake

/**
 * A farm's push wake (core/link/PushWake): Firebase Cloud Messaging gives this phone a token, every
 * farm that keeps one is given it, and a wake brings the link service up and that farm's link back at
 * once. A build without the Firebase key, or a phone without Google Play services, has no push wake:
 * the link service keeps its sockets as it always did.
 */
class PushWakeService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = keepToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        wake(this, message.data)
    }

    companion object {
        /** A wake message: the link service comes up and the named farm's link opens, a resting one too; false when it names no paired farm. */
        fun wake(context: Context, data: Map<String, String>): Boolean {
            val app = AgentFarmApp.of(context)
            val farm = PushWake.farmOf(data, app.store.load().map { it.id }) ?: return false
            Log.i(TAG, "wake for $farm")
            // A high-priority message may start the foreground service; a deprioritised one may not, and the next wake will.
            runCatching { LinkService.sync(context, anyFarm = true) }.onFailure { Log.w(TAG, "link service not started: $it") }
            app.manager.wake(farm)
            return true
        }

        private const val TAG = "AFPush"
        private const val PROJECT = "agent-farm-f0701"
        private const val APP_ID = "1:426334010939:android:ead4c441732d1dbf17e61e"
        private const val SENDER = "426334010939"

        /** Starts Firebase with this build's key and asks for the token; a build without the key starts nothing. */
        fun setUp(context: Context) {
            val key = BuildConfig.FIREBASE_API_KEY
            if (key.isBlank()) return
            runCatching {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId(APP_ID).setApiKey(key).setProjectId(PROJECT).setGcmSenderId(SENDER).build()
                    FirebaseApp.initializeApp(context, options)
                }
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { keepToken(context, it) }
                    .addOnFailureListener { Log.w(TAG, "no push token: $it") }
            }.onFailure { Log.w(TAG, "push wake off: $it") }
        }

        private fun keepToken(context: Context, token: String) {
            val app = AgentFarmApp.of(context)
            if (app.prefs.pushToken == token) return
            app.prefs.pushToken = token
            app.manager.registerPush(token)
        }
    }
}
