package com.muvusoft.agentfarm.core.power

/**
 * What the battery-optimization row says. `exempt` = the system lets this app skip battery
 * optimization; without it Doze may delay the link and its alerts while the screen is off.
 */
object BatteryPolicy {
    data class Line(val state: String, val detail: String, val action: String)

    fun line(exempt: Boolean): Line = if (exempt) {
        Line(
            state = "Kısıtlanmıyor",
            detail = "Ekran kapalıyken de bağlantı ve uyarılar gecikmeden gelir.",
            action = "Ayarı aç",
        )
    } else {
        Line(
            state = "Optimize ediliyor",
            detail = "Telefon uzun süre boştayken uyarılar gecikebilir. Listede Agent Farm'ı bulup \"Optimize etme\"yi seçin.",
            action = "Ayarı aç",
        )
    }
}
