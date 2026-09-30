package com.muvusoft.agentfarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.muvusoft.agentfarm.core.contract.CONTRACT_VERSION
import com.muvusoft.agentfarm.core.contract.Challenge
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.Frame
import com.muvusoft.agentfarm.core.contract.Hello
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.contract.Welcome
import com.muvusoft.agentfarm.core.pairing.PairingProblem
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.DeviceKeys
import com.muvusoft.agentfarm.net.PairClient
import com.muvusoft.agentfarm.net.PinnedTls
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pairing against the fake host over pinned TLS, then a hello signed by the Keystore key. */
@RunWith(AndroidJUnit4::class)
class PairingE2ETest {
    private val who = DeviceIdentity("Emulator", "android test", "test")

    private fun pair(link: String) = runBlocking { PairClient.pair(Pairing.parse(link)!!, who) }

    private fun paired(): PairedFarm = (pair(FakeHost.freshLink()) as PairingVerdict.Result.Paired).farm

    @Test
    fun pairingStoresAKeystoreKeyAndTheFarmsAnswer() {
        val farm = paired()
        assertTrue(farm.device.startsWith("dev_"))
        assertTrue(DeviceKeys.exists(farm.key))
    }

    @Test
    fun theKeystoreSignatureIsWelcomed() {
        val farm = paired()
        val frames = LinkedBlockingQueue<Frame>()
        val ws = PinnedTls.client(farm.fp).newWebSocket(
            Request.Builder().url("wss://${farm.addresses.first()}/ws").build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) { Codec.decode(text)?.let(frames::put) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { t.printStackTrace() }
            },
        )
        try {
            val challenge = frames.poll(10, TimeUnit.SECONDS) as Challenge
            val signature = DeviceKeys.sign(farm.key, Codec.signedText(farm.id, farm.device, challenge.nonce))
            ws.send(Codec.encode(Hello(farm.device, CONTRACT_VERSION, signature)))
            val answer = frames.poll(10, TimeUnit.SECONDS)
            assertTrue("expected welcome, got $answer", answer is Welcome)
            assertEquals(farm.device, (answer as Welcome).device)
        } finally {
            ws.close(1000, null)
        }
    }

    @Test
    fun anotherCertificateIsRefusedBeforeAnythingIsSent() {
        val link = FakeHost.requireLink().replace(Regex("fp=[0-9a-f]+"), "fp=" + "0".repeat(64))
        assertEquals(PairingVerdict.Result.Failed(PairingProblem.CertificateMismatch), pair(link))
    }

    @Test
    fun aSpentCodeSaysSo() {
        val link = FakeHost.freshLink()
        assertTrue(pair(link) is PairingVerdict.Result.Paired)
        assertEquals(PairingVerdict.Result.Failed(PairingProblem.CodeUsed), pair(link))
    }
}
