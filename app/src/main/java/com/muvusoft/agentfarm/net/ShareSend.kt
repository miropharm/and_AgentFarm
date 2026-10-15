package com.muvusoft.agentfarm.net

import android.content.Context
import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.share.Share
import com.muvusoft.agentfarm.core.share.Shared
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A share sent to a farm that answers: its picture first goes to the PC (console.stage), then the words and
 * the picture's path go into the chosen session (console.send) or open a new one with an agent (console.new,
 * the phone's own door: the agent's last launch setup). Null = the farm did not answer in time.
 */
object ShareSend {
    private const val UPLOAD_TIMEOUT_MS = 90_000L

    suspend fun send(context: Context, manager: ConnectionManager, farm: String, to: String, shared: Shared, gate: String?): CallResult? {
        val attach = shared.image?.let { image ->
            val data = SharedImages.base64(context, image) ?: return CallResult("", false, error = "the picture could not be read on this phone")
            val staged = manager.request(farm, "console.stage", buildJsonObject {
                put("name", JsonPrimitive(image.name))
                put("data", JsonPrimitive(data))
                put("mediaType", JsonPrimitive(image.mime))
            }, UPLOAD_TIMEOUT_MS) ?: return null
            if (!staged.ok) return staged
            Share.stagedPath(staged.result) ?: return CallResult(staged.id, false, error = "the farm did not say where it put the picture")
        }
        val (op, args) = Share.call(to, shared.text, attach, gate)
        return manager.request(farm, op, args)
    }
}
