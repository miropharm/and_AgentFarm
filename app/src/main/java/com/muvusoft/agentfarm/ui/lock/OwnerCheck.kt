package com.muvusoft.agentfarm.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.muvusoft.agentfarm.core.lock.LockPolicy.Availability

/** The phone's own proof of its owner: a biometric, or the screen lock when there is none. */
object OwnerCheck {
    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun availability(context: Context): Availability = when (BiometricManager.from(context).canAuthenticate(AUTH)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Availability.NO_HARDWARE
        else -> Availability.UNAVAILABLE
    }

    /** Shows the system prompt; `onResult(true)` only after the owner proved themselves. A single bad finger is not an answer. */
    fun ask(context: Context, title: String, subtitle: String?, onResult: (Boolean) -> Unit) {
        val activity = context.activity() ?: return onResult(false)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(AUTH)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }

    private tailrec fun Context.activity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
}
