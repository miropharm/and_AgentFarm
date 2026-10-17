package com.muvusoft.agentfarm.core

import com.muvusoft.agentfarm.core.lang.Words

/** Every elapsed time the shell prints goes through here: shortest unambiguous form. */
object Time {
    fun ago(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        return when {
            s < 60 -> Words.t("{0} s", s)
            s < 3600 -> Words.t("{0} min", s / 60)
            s < 86_400 -> Words.t("{0} h", s / 3600)
            else -> Words.t("{0} d", s / 86_400)
        }
    }
}
