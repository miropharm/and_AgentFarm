package com.muvusoft.agentfarm.net

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.core.lang.AppLanguage
import com.muvusoft.agentfarm.core.lang.Words
import java.util.Locale

/** The phone's own languages, in the user's order (the system's, which an app-level language never shadows). */
fun phoneLocales(): List<Locale> = Resources.getSystem().configuration.locales.let { l -> List(l.size()) { l[it] } }

/** The language the app's words are drawn in for a farm's [tag] (core/lang/AppLanguage.effective). */
fun appLocale(tag: String?): Locale = AppLanguage.effective(tag, phoneLocales(), Words.languages)

/**
 * The configuration change that draws the app in [tag]'s language. Always that one locale alone: Android's own
 * matching of the phone's list would take Turkish over English, whose words are the default resources.
 */
fun languageOverride(tag: String?): Configuration = Configuration().apply { setLocales(LocaleList(appLocale(tag))) }

/** Whether two tags draw the same words (on an English phone "en" and none do: no screen is redrawn for it). */
fun drawAlike(a: String?, b: String?): Boolean = appLocale(a) == appLocale(b)

/** This context drawing in the app's language: a notification, a channel's name, the widget, built off-screen. */
fun Context.inAppLanguage(): Context = createConfigurationContext(languageOverride(AgentFarmApp.of(this).language.value))
