package com.muvusoft.agentfarm.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.muvusoft.agentfarm.MainActivity
import com.muvusoft.agentfarm.R
import com.muvusoft.agentfarm.core.notify.Alert
import com.muvusoft.agentfarm.core.notify.Channel

/** Draws an Alert as a notification; its buttons go to AlertActionReceiver, which calls the op. */
object AlertPoster {
    private val SPEC = mapOf(
        Channel.ASK to Triple(R.string.channel_ask, NotificationManager.IMPORTANCE_HIGH, R.string.channel_ask_detail),
        Channel.NOTICE to Triple(R.string.channel_notice, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_notice_detail),
        Channel.TURN to Triple(R.string.channel_turn, NotificationManager.IMPORTANCE_LOW, R.string.channel_turn_detail),
        Channel.LINK to Triple(R.string.channel_link, NotificationManager.IMPORTANCE_MIN, R.string.channel_link_detail),
    )

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        for ((ch, spec) in SPEC) {
            nm.createNotificationChannel(NotificationChannel(ch.id, context.getString(spec.first), spec.second).apply {
                description = context.getString(spec.third)
                setShowBadge(ch == Channel.ASK || ch == Channel.NOTICE)
            })
        }
    }

    fun post(context: Context, farmId: String, alert: Alert) {
        val open = PendingIntent.getActivity(
            context, alert.tag.hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, alert.channel.id)
            .setSmallIcon(R.drawable.ic_stat_farm)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (alert.channel == Channel.ASK) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        alert.actions.forEachIndexed { i, a ->
            val intent = AlertActionReceiver.intent(context, farmId, alert.tag, a)
            // Mutable only when the system must write the typed reply into it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (a.replyArg != null) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(context, (alert.tag + i).hashCode(), intent, flags)
            val action = NotificationCompat.Action.Builder(0, a.label, pi).setAuthenticationRequired(a.unlock)
            if (a.replyArg != null) action.addRemoteInput(RemoteInput.Builder(AlertActionReceiver.REPLY).setLabel(a.label).build())
            b.addAction(action.build())
        }
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(context).notify(alert.tag, 0, b.build())
            } catch (_: SecurityException) {
                // Permission withdrawn between the check and the post: nothing to show.
            }
        }
    }

    fun cancel(context: Context, tag: String) = NotificationManagerCompat.from(context).cancel(tag, 0)
}
