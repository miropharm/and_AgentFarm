package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.PairRequest
import com.muvusoft.agentfarm.core.contract.PairResponse
import com.muvusoft.agentfarm.core.contract.PairingInfo
import com.muvusoft.agentfarm.core.pairing.PairingProblem
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Who the phone says it is when pairing. */
data class DeviceIdentity(val name: String, val platform: String, val app: String)

/**
 * Pairs with the farm a link names: a fresh Keystore key, POST /pair to each address in order over the
 * pinned TLS client, and the answer checked by the core verdict. A failed pairing leaves no key behind.
 */
object PairClient {
    private val JSON = "application/json".toMediaType()

    suspend fun pair(info: PairingInfo, who: DeviceIdentity): PairingVerdict.Result = withContext(Dispatchers.IO) {
        val alias = DeviceKeys.newAlias(info.farm, System.currentTimeMillis())
        val publicKey = DeviceKeys.create(alias)
        val body = Codec.json.encodeToString(
            PairRequest.serializer(),
            PairingVerdict.request(info, who.name, publicKey, who.platform, who.app),
        )
        val result = attempt(info, body, alias)
        if (result !is PairingVerdict.Result.Paired) DeviceKeys.delete(alias)
        result
    }

    private fun attempt(info: PairingInfo, body: String, alias: String): PairingVerdict.Result {
        val client = PinnedTls.client(info.fp)
        val tried = mutableListOf<String>()
        for (address in info.addresses) {
            tried += address
            val req = Request.Builder().url("https://$address/pair").post(body.toRequestBody(JSON)).build()
            try {
                client.newCall(req).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return PairingVerdict.Result.Failed(PairingVerdict.refused(res.code, errorOf(text)))
                    }
                    val parsed = try {
                        Codec.json.decodeFromString(PairResponse.serializer(), text)
                    } catch (_: SerializationException) {
                        return PairingVerdict.Result.Failed(PairingProblem.Refused(res.code, "the answer could not be read"))
                    }
                    return PairingVerdict.accept(info, parsed, alias)
                }
            } catch (e: IOException) {
                if (PinnedTls.isPinFailure(e)) return PairingVerdict.Result.Failed(PairingProblem.CertificateMismatch)
            }
        }
        return PairingVerdict.Result.Failed(PairingProblem.Unreachable(tried))
    }

    private fun errorOf(text: String): String? =
        Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)
}
