package com.muvusoft.agentfarm.net

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.muvusoft.agentfarm.core.handsfree.Presses

/**
 * The headset button while the headset mode is on (S-10, S-11): a media session that takes the headset's hook and
 * play/pause presses and counts them (core/handsfree/Presses). Android hands the button to the media session of the
 * app that played sound last, so a music app the user listens to keeps it - the mode's own loop needs no button (the
 * design's decision 4). Released when the mode goes off or the link service ends.
 */
class HeadsetButton(private val context: Context, private val pressed: (Presses.Kind) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val presses = Presses()

    @Volatile
    private var session: MediaSession? = null

    /** The session's token while it is open: a test presses the button through the session itself. */
    val token: MediaSession.Token? get() = session?.sessionToken

    fun sync(on: Boolean) {
        main.post { if (on) open() else close() }
    }

    private fun open() {
        if (session != null) return
        session = MediaSession(context, TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    val key = keyOf(intent) ?: return false
                    if (key.keyCode !in KEYS) return false
                    if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) press()
                    return true
                }
            }, main)
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                    .setState(PlaybackState.STATE_PAUSED, 0, 1f)
                    .build(),
            )
            isActive = true
        }
    }

    private fun close() {
        main.removeCallbacksAndMessages(null)
        session?.release()
        session = null
    }

    private fun press() {
        if (presses.press(SystemClock.uptimeMillis()) == Presses.Kind.TWO) {
            pressed(Presses.Kind.TWO)
            return
        }
        main.postDelayed({ presses.due(SystemClock.uptimeMillis())?.let(pressed) }, Presses.GAP_MS)
    }

    private fun keyOf(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    private companion object {
        const val TAG = "AFHeadsetButton"
        val KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }
}
