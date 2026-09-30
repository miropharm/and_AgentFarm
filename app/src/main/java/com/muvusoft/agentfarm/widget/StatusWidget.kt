package com.muvusoft.agentfarm.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** The home-screen widget: connected farms, what waits, what runs. The link service redraws it on every change. */
class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, StatusSurfaces.views(context, StatusSurfaces.current(context)))
    }
}
