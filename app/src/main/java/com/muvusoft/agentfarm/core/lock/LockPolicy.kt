package com.muvusoft.agentfarm.core.lock

/** When the shell asks for its owner, and how a destructive action is confirmed. The platform reports; this decides. */
object LockPolicy {
    /** How long the app may sit in the background before coming back asks for the owner again. */
    const val GRACE_MS = 60_000L

    /** What the phone can offer to prove its owner (biometric or the screen lock). */
    enum class Availability { READY, NOT_ENROLLED, NO_HARDWARE, UNAVAILABLE }

    enum class Confirm { OWNER, DIALOG }

    /** Whether the app is locked now. `backgroundSince` = when it last left the screen; null while it is on screen. */
    fun locked(enabled: Boolean, unlocked: Boolean, backgroundSince: Long?, now: Long): Boolean = when {
        !enabled -> false
        !unlocked -> true
        backgroundSince != null && now - backgroundSince >= GRACE_MS -> true
        else -> false
    }

    /** A destructive action always asks the owner; a phone that cannot prove one falls back to a dialog. */
    fun confirm(availability: Availability): Confirm =
        if (availability == Availability.READY) Confirm.OWNER else Confirm.DIALOG

    /** Why the owner check is not offered, in the user's words; null when it is. */
    fun reason(availability: Availability): String? = when (availability) {
        Availability.READY -> null
        Availability.NOT_ENROLLED -> "Telefonda ekran kilidi ya da parmak izi tanımlı değil."
        Availability.NO_HARDWARE -> "Bu telefon kimlik doğrulaması sunmuyor."
        Availability.UNAVAILABLE -> "Kimlik doğrulaması şu an kullanılamıyor."
    }
}
