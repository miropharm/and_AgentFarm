package com.muvusoft.agentfarm.core.speech

import java.util.Locale

/**
 * The language the phone reads aloud and dictates in: the user's choice, else the phone's own language.
 * Stored as a BCP 47 tag; [PHONE] ("") means "follow the phone". Reading aloud and dictation ask this one
 * function, so the two never disagree.
 */
object VoiceLanguage {
    const val PHONE = ""

    /** Offered besides the phone's own: widely spoken languages a phone's voice and recogniser commonly carry. */
    val OFFERED: List<String> = listOf(
        "en-US", "en-GB", "es-ES", "es-MX", "pt-BR", "pt-PT", "fr-FR", "de-DE", "it-IT", "nl-NL",
        "pl-PL", "ru-RU", "uk-UA", "tr-TR", "ar-SA", "hi-IN", "bn-IN", "id-ID", "vi-VN", "th-TH",
        "ja-JP", "ko-KR", "zh-CN", "zh-TW",
    )

    /** The tag in effect: the stored choice when it names a language, else the phone's. */
    fun tag(chosen: String?, phone: Locale): String {
        val c = chosen?.trim().orEmpty()
        val l = if (c.isEmpty()) null else Locale.forLanguageTag(c)
        return if (l != null && l.language.isNotEmpty()) l.toLanguageTag() else phone.toLanguageTag()
    }

    fun locale(chosen: String?, phone: Locale): Locale = Locale.forLanguageTag(tag(chosen, phone))

    /** A language the way its own speakers write it ("Deutsch (Deutschland)"), as a picker lists it. */
    fun name(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        val n = l.getDisplayName(l).ifEmpty { tag }
        return n.replaceFirstChar { if (it.isLowerCase()) it.titlecase(l) else it.toString() }
    }

    /** The picker's rows: the phone's own first, then the offered ones; a stored tag not offered stays, last. */
    fun choices(stored: String?): List<String> {
        val c = stored?.trim().orEmpty()
        val known = c.isEmpty() || c in OFFERED
        return listOf(PHONE) + OFFERED + (if (known) emptyList() else listOf(c))
    }
}
