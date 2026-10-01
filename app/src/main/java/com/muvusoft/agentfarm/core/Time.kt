package com.muvusoft.agentfarm.core

/** Every elapsed time the shell prints goes through here: shortest unambiguous form. */
object Time {
    fun ago(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return when {
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min"
            s < 86_400 -> "${s / 3600} h"
            else -> "${s / 86_400} d"
        }
    }
}
