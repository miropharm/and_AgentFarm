package com.muvusoft.agentfarm

import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.contract.PairingInfo
import com.muvusoft.agentfarm.net.PinnedTls
import org.junit.Assume.assumeTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The fake Agent Farm host scripts/ci/emulator-run.sh starts; its pairing link arrives as the `pairUri` argument. */
object FakeHost {
    val link: String? get() = InstrumentationRegistry.getArguments().getString("pairUri")

    fun requireLink(): String {
        val l = link
        assumeTrue("no fake host (pairUri argument) on this run", !l.isNullOrBlank())
        return l!!
    }

    /** The link with a pairing code the host has not handed out yet (codes work once). */
    fun freshLink(): String {
        val base = requireLink()
        val info = Pairing.parse(base)!!
        val code = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(get(info, "/_test/state"))!!.groupValues[1]
        return base.replace(Regex("code=[^&]+"), "code=$code")
    }

    fun get(info: PairingInfo, path: String): String =
        PinnedTls.client(info.fp).newCall(Request.Builder().url("https://${info.addresses.first()}$path").build())
            .execute().use { it.body!!.string() }

    /** POSTs a test control request under /_test (emit an event, drop sockets, revoke a device). */
    fun post(path: String, json: String): String {
        val info = Pairing.parse(requireLink())!!
        val req = Request.Builder().url("https://${info.addresses.first()}$path")
            .post(json.toRequestBody("application/json".toMediaType())).build()
        return PinnedTls.client(info.fp).newCall(req).execute().use { it.body!!.string() }
    }
}
