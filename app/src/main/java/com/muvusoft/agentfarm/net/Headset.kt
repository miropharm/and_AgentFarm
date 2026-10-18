package com.muvusoft.agentfarm.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** What the headset mode needs from the phone: a headset on (wired, USB or Bluetooth) and the microphone permission. */
object Headset {
    private val KINDS = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    fun on(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in KINDS }

    /** The headset coming and going, from the audio outputs as they change; the first value is the present one. */
    fun watch(context: Context): Flow<Boolean> = callbackFlow {
        val audio = context.getSystemService(AudioManager::class.java)
        val changed = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { trySend(on(context)) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { trySend(on(context)) }
        }
        trySend(on(context))
        audio.registerAudioDeviceCallback(changed, Handler(Looper.getMainLooper()))
        awaitClose { audio.unregisterAudioDeviceCallback(changed) }
    }.distinctUntilChanged()

    fun microphone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
