package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.CallResult

/** The Now screen's sentences that are verdicts, not labels. */
object NowText {
    const val NO_ANSWER = "The farm did not answer. Pull down to try again."
    const val NOT_READ = "The farm's answer did not read. Agent Farm on the PC may need an update."

    /** Why the fresh answer is not on screen: the farm's own sentence first, then silence, then a shape it does not speak. */
    fun notRead(vararg results: CallResult?): String =
        results.firstNotNullOfOrNull { r -> r?.takeIf { !it.ok }?.error?.takeIf(String::isNotBlank) }
            ?: if (results.any { it == null }) NO_ANSWER else NOT_READ
}
