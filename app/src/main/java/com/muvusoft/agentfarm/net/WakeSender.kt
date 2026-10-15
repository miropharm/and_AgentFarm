package com.muvusoft.agentfarm.net

import com.muvusoft.agentfarm.core.contract.WakeNic
import com.muvusoft.agentfarm.core.link.Wake
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sends the Wake-on-LAN packets (core/link/Wake) as UDP broadcasts; the answer is how many left the phone. */
object WakeSender {
    suspend fun send(nics: List<WakeNic>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { s ->
                s.broadcast = true
                var sent = 0
                var last: Exception? = null
                for ((packet, to) in Wake.sends(nics)) {
                    try {
                        s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(to), Wake.PORT))
                        sent++
                    } catch (e: Exception) {
                        last = e
                    }
                }
                if (sent == 0) throw last ?: IllegalStateException("no network card to wake")
                sent
            }
        }
    }
}
