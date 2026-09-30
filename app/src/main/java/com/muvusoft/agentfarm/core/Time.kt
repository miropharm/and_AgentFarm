package com.muvusoft.agentfarm.core

/** Every elapsed time the shell prints goes through here: shortest unambiguous form. */
object Time {
    fun ago(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return when {
            s < 60 -> "$s sn"
            s < 3600 -> "${s / 60} dk"
            s < 86_400 -> "${s / 3600} sa"
            else -> "${s / 86_400} gün"
        }
    }
}
