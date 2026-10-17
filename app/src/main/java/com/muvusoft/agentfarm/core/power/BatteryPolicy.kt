package com.muvusoft.agentfarm.core.power

import com.muvusoft.agentfarm.core.lang.Words

/**
 * What the battery-optimization row says. `exempt` = the system lets this app skip battery
 * optimization; without it Doze may delay the link and its alerts while the screen is off.
 */
object BatteryPolicy {
    data class Line(val state: String, val detail: String, val action: String)

    fun line(exempt: Boolean): Line = if (exempt) {
        Line(
            state = Words.t("Not restricted"),
            detail = Words.t("The link and its alerts arrive without delay, even with the screen off."),
            action = Words.t("Open setting"),
        )
    } else {
        Line(
            state = Words.t("Optimized"),
            detail = Words.t("Alerts may be late while the phone sits idle for a long time. Find Agent Farm in the list and choose \"Don't optimize\"."),
            action = Words.t("Open setting"),
        )
    }
}
