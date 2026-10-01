package com.muvusoft.agentfarm.core.power

/**
 * What the battery-optimization row says. `exempt` = the system lets this app skip battery
 * optimization; without it Doze may delay the link and its alerts while the screen is off.
 */
object BatteryPolicy {
    data class Line(val state: String, val detail: String, val action: String)

    fun line(exempt: Boolean): Line = if (exempt) {
        Line(
            state = "Not restricted",
            detail = "The link and its alerts arrive without delay, even with the screen off.",
            action = "Open setting",
        )
    } else {
        Line(
            state = "Optimized",
            detail = "Alerts may be late while the phone sits idle for a long time. Find Agent Farm in the list and choose \"Don't optimize\".",
            action = "Open setting",
        )
    }
}
