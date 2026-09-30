package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.Time.ago
import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
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
            "Reddedildi · ${refused(link.reason, link.minContract)}",
            "Çiftlik bağlantıyı reddetti: ${refused(link.reason, link.minContract)}" + (link.detail?.let { " ($it)" } ?: "") +
                ". " + clears(link.reason, link.minContract),
        )
    }

    /** The link detail sheet: the long form first, then where the phone looks for the farm. */
    fun sheet(link: Link, addresses: List<String>, now: Long): List<String> =
        listOf(of(link, now).long) + (if (addresses.isEmpty()) emptyList() else listOf("Adresler: " + addresses.joinToString(", ")))

    /** Which side is behind: the farm needs a newer contract than this app speaks, or the other way round. */
    private fun appIsBehind(minContract: Long?): Boolean? = minContract?.let { it > CONTRACT_VERSION }

    fun refused(reason: String, minContract: Long? = null): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> "bu uygulama eski"
            false -> "Agent Farm eski"
            null -> "sürümler uyuşmuyor"
        }
        "revoked" -> "bu cihazın izni kaldırılmış"
        "unknown-device" -> "çiftlik bu cihazı tanımıyor"
        "bad-signature" -> "cihaz imzası doğrulanamadı"
        "not-allowed" -> "izin verilmedi"
        "busy" -> "çiftlik meşgul"
        else -> reason
    }

    private fun clears(reason: String, minContract: Long?): String = when (reason) {
        "contract" -> when (appIsBehind(minContract)) {
            true -> "Telefondaki uygulamayı güncelleyin: çiftlik sözleşme $minContract istiyor, bu uygulama $CONTRACT_VERSION konuşuyor."
            false -> "Agent Farm'ı güncelleyin: bu uygulama sözleşme $CONTRACT_VERSION konuşuyor, çiftlik daha eskisini."
            null -> "Uygulamayı ya da Agent Farm'ı güncelleyin."
        }
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
