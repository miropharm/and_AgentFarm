package com.muvusoft.agentfarm.net

import android.content.res.Resources
import java.util.Locale

/** The phone's own language (the system's first), which an app-level language never shadows. */
fun phoneLocale(): Locale = Resources.getSystem().configuration.locales.get(0) ?: Locale.getDefault()
