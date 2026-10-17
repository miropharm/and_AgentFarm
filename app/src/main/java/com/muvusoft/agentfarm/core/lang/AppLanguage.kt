package com.muvusoft.agentfarm.core.lang

import java.util.Locale

/**
 * The language the app's own screens draw in: the one a farm draws its pages in (welcome.lang), so a
 * native screen and the farm page beside it read alike; with no farm telling one, the phone's own.
 * The newest telling wins: a farm naming a language for the first time, or another one than it named
 * before, moves the app; a farm repeating itself moves nothing, so going from farm to farm never flips it.
 */
object AppLanguage {
    /** What one farm last told: the tag, and when it began telling it. */
    data class Told(val tag: String, val since: Long)

    /** A tag the app can draw in, or null for one that names no language. */
    fun clean(tag: String?): String? {
        val t = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val l = Locale.forLanguageTag(t)
        return if (l.language.isEmpty() || l.language == "und") null else l.toLanguageTag()
    }

    /** A farm's record after its welcome told [tag] at [now]: kept as it was when the farm repeats itself or tells nothing. */
    fun tell(before: Told?, tag: String?, now: Long): Told? {
        val t = clean(tag) ?: return before
        return if (before?.tag == t) before else Told(t, now)
    }

    /** The paired farms' records -> the app's tag, null when none told one (the phone's own language then). */
    fun of(told: Collection<Told>): String? = told.maxByOrNull { it.since }?.tag

    /**
     * The language the app's words are drawn in: the told one when the app has words for it, else the first of the
     * phone's languages it has words for, else English. Resolved here, never by Android's resource matching: English
     * is the default resources, not values-en, so a phone listing English before Turkish would match Turkish.
     * A language the phone also lists keeps the phone's region ("en" on an en-US phone is en-US: nothing to redraw).
     */
    fun effective(tag: String?, phone: List<Locale>, supported: Set<String>): Locale {
        val pick = (listOfNotNull(clean(tag)?.let(Locale::forLanguageTag)) + phone).firstOrNull { it.language in supported } ?: Locale.ENGLISH
        return phone.firstOrNull { it.language == pick.language } ?: pick
    }

    /** A record as kept on the phone ("tr|1792251673568"), and back; a damaged one reads as never told. */
    fun write(told: Told): String = "${told.tag}|${told.since}"

    fun read(text: String?): Told? {
        val parts = text?.split('|') ?: return null
        if (parts.size != 2) return null
        val tag = clean(parts[0]) ?: return null
        return parts[1].toLongOrNull()?.let { Told(tag, it) }
    }
}
