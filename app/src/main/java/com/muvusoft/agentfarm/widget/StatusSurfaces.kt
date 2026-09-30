package com.muvusoft.agentfarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.MainActivity
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.notify.StatusLine

/**
 * The home-screen widget and the quick settings tile say what the ongoing notification says — the same
 * StatusLine, never a second derivation — and a tap on either opens the focused farm's Now page.
 */
object StatusSurfaces {
    fun current(context: Context): StatusLine.Text =
        StatusLine.of(AgentFarmApp.of(context).manager.state.value, System.currentTimeMillis())

    /** Redraws every placed widget and asks the tile to refresh while the shade is open. */
    fun show(context: Context, text: StatusLine.Text) {
        val wm = AppWidgetManager.getInstance(context)
        val ids = wm.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
        if (ids.isNotEmpty()) wm.updateAppWidget(ids, views(context, text))
        TileService.requestListeningState(context, ComponentName(context, StatusTile::class.java))
    }

    fun views(context: Context, text: StatusLine.Text): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_status).apply {
            setTextViewText(R.id.widget_title, text.title)
            setTextViewText(R.id.widget_body, text.body)
            setOnClickPendingIntent(R.id.widget_root, openNow(context))
        }

    fun openNow(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7, openNowIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun openNowIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.OPEN_NOW, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
