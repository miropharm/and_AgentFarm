package com.muvusoft.agentfarm.core

import com.muvusoft.agentfarm.core.lang.Words

val GREETING_TITLE: String get() = Words.t("Hello, Agent Farm")

fun buildLabel(versionName: String): String = Words.t("Version {0}", versionName)
