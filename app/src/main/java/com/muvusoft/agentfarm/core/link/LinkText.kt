package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.Time.ago
import com.muvusoft.agentfarm.core.state.Link

/**
 * The status strip's words for one farm's link. A wait has one shape: kind · reason · since;
 * `short` is the strip, `long` the detail sheet (what clears it included).
 */
object LinkText {
    data class Text(val short: String, val long: String)

    fun of(link: Link, now: Long): Text = when (link) {
        is Link.Online -> Text("Bağlı · ${link.address}", "Bağlı: ${link.address}, yetki ${link.scope}, ${ago(now - link.since)} önce.")
        is Link.Connecting -> Text(
            if (link.attempt <= 1) "Bağlanıyor · ${link.address}" else "Yeniden bağlanıyor · deneme ${link.attempt}",
            "Bağlanıyor: ${link.address}, deneme ${link.attempt}, ${ago(now - link.since)} önce başladı. Çiftliğe ulaşınca kendiliğinden bağlanır.",
        )
        is Link.Offline -> Text(
            "Bağlı değil · ${offline(link.reason)}",
            "Bağlı değil: ${offline(link.reason)}, ${ago(now - link.since)}. Çiftliğe ulaşılınca kendiliğinden bağlanır.",
        )
        is Link.Refused -> Text(
            "Reddedildi · ${refused(link.reason)}",
            "Çiftlik bağlantıyı reddetti: ${refused(link.reason)}" + (link.detail?.let { " ($it)" } ?: "") +
                ". " + clears(link.reason),
        )
    }

    fun refused(reason: String): String = when (reason) {
        "revoked" -> "bu cihazın izni kaldırılmış"
        "unknown-device" -> "çiftlik bu cihazı tanımıyor"
        "bad-signature" -> "cihaz imzası doğrulanamadı"
        "contract" -> "sürümler uyuşmuyor"
        "not-allowed" -> "izin verilmedi"
        "busy" -> "çiftlik meşgul"
        else -> reason
    }

    private fun clears(reason: String): String = when (reason) {
        "contract" -> "Uygulamayı ya da Agent Farm'ı güncelleyin."
        "busy", "not-allowed" -> "Biraz sonra yeniden denenecek."
        else -> "Agent Farm'da yeni QR ile yeniden eşleyin."
    }

    private fun offline(reason: String): String = when (reason) {
        "paired" -> "henüz bağlanılmadı"
        "unreachable" -> "adreslere ulaşılamadı"
        "closed" -> "bağlantı kapandı"
        "stopped" -> "durduruldu"
        else -> reason
    }
}
