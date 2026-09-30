package com.muvusoft.agentfarm.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.muvusoft.agentfarm.AgentFarmApp
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.notify.AlertAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A notification button was pressed: its op goes into the farm's outbox (sent now or on reconnect). */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val farm = intent.getStringExtra(FARM) ?: return
        val op = intent.getStringExtra(OP) ?: return
        val tag = intent.getStringExtra(TAG) ?: return
        var args = intent.getStringExtra(ARGS)?.let { Codec.json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
        val replyArg = intent.getStringExtra(REPLY_ARG)
        if (replyArg != null) {
            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString()?.trim()
            if (text.isNullOrEmpty()) return
            args = JsonObject(args + (replyArg to JsonPrimitive(text)))
        }
        AgentFarmApp.of(context).manager.call(farm, op, args)
        AlertPoster.cancel(context, tag)
    }

    companion object {
        const val REPLY = "reply"
        private const val FARM = "farm"
        private const val OP = "op"
        private const val ARGS = "args"
        private const val TAG = "tag"
        private const val REPLY_ARG = "replyArg"

        fun intent(context: Context, farmId: String, tag: String, a: AlertAction): Intent =
            Intent(context, AlertActionReceiver::class.java)
                .putExtra(FARM, farmId)
                .putExtra(OP, a.op)
                .putExtra(ARGS, a.args.toString())
                .putExtra(TAG, tag)
                .apply { if (a.replyArg != null) putExtra(REPLY_ARG, a.replyArg) }
    }
}
