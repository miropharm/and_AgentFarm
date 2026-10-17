package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.lang.Words

/** The Now screen's sentences that are verdicts, not labels. */
object NowText {
    val NO_ANSWER: String get() = Words.t("The farm did not answer. Pull down to try again.")
    val NOT_READ: String get() = Words.t("The farm's answer did not read. Agent Farm on the PC may need an update.")

    /**
     * Why the fresh answer is not on screen: the farm's own sentence first, then silence, then a shape it does not
     * speak. Silence while the link is down is no line of its own: the copy's age already says it.
     */
    fun notRead(vararg results: CallResult?, online: Boolean): String? =
        results.firstNotNullOfOrNull { r -> r?.takeIf { !it.ok }?.error?.takeIf(String::isNotBlank) }
            ?: if (results.none { it == null }) NOT_READ else if (online) NO_ANSWER else null
}
