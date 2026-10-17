package com.muvusoft.agentfarm.core.lang

/**
 * The core's own sentences in the app's language: the English sentence is the key and the fallback, `{0}`, `{1}`
 * its blanks, and a language's catalog maps the key to its sentence (WordsTr.kt). The app sets [lang] to the
 * language its screens are drawn in (AppLanguage.effective), so a verdict reads like the strings.xml words beside it.
 * JVM tests read English.
 */
object Words {
    @Volatile
    var lang: String = "en"

    private val CATALOGS: Map<String, Map<String, String>> = mapOf("tr" to TURKISH)

    /** Every language the app has words for: English and each catalog (each has its res/values-<lang> too). */
    val languages: Set<String> = setOf("en") + CATALOGS.keys

    fun t(english: String, vararg args: Any?): String =
        fill(CATALOGS[lang]?.get(english) ?: english, args)

    private val BLANK = Regex("\\{(\\d)}")

    private fun fill(sentence: String, args: Array<out Any?>): String =
        BLANK.replace(sentence) { m -> args.getOrNull(m.groupValues[1].toInt())?.toString() ?: m.value }
}
