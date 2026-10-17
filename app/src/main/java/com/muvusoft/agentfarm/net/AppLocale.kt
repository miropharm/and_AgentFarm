package com.muvusoft.agentfarm.net

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.muvusoft.agentfarm.AgentFarmApp
import java.util.Locale

/**
 * The configuration change that draws the app in [tag] (core/lang/AppLanguage), the phone's own language
 * second so a language the app has no words for falls back to the phone's; null when none is needed.
 */
fun languageOverride(tag: String?): Configuration? {
    val want = tag?.let { Locale.forLanguageTag(it) }?.takeIf { it.language.isNotEmpty() } ?: return null
    val phone = phoneLocale()
    if (want.language == phone.language && (want.country.isEmpty() || want.country == phone.country)) return null
    return Configuration().apply { setLocales(LocaleList(want, phone)) }
}

/** Whether two tags draw the same words (on an English phone "en" and none do: no screen is redrawn for it). */
fun drawAlike(a: String?, b: String?): Boolean = languageOverride(a)?.locales == languageOverride(b)?.locales

/** This context drawing in the app's language: a notification, a channel's name, the widget, built off-screen. */
fun Context.inAppLanguage(): Context =
    languageOverride(AgentFarmApp.of(this).language.value)?.let { createConfigurationContext(it) } ?: this
