package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellState
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    private fun contractDiscovery(): String {
        val file = listOf(File("../contract/remote-contract.v1.json"), File("contract/remote-contract.v1.json")).first { it.exists() }
        return Json.parseToJsonElement(file.readText()).jsonObject["transport"]!!.jsonObject["discovery"]!!.jsonPrimitive.content
    }

    @Test
    fun theServiceAndItsFarmKeyAreTheContracts() {
        val rule = contractDiscovery()
        assertTrue(rule, rule.contains("$SERVICE.local"))
        assertTrue(rule, rule.contains("farm=<farm id>"))
    }

    @Test
    fun anAnswerSaysWhichFarmAndWhere() {
        val txt = mapOf("farm" to "f_ab12".toByteArray(), "contract" to "1".toByteArray())
        assertEquals(Discovery.Found("f_ab12", "192.168.1.9:7443"), Discovery.found(txt, "192.168.1.9", 7443))
    }

    @Test
    fun anAnswerWithoutAFarmOrAUsableAddressIsNothing() {
        val txt = mapOf<String, ByteArray?>("farm" to "f_ab12".toByteArray())
        assertNull(Discovery.found(emptyMap(), "192.168.1.9", 7443))
        assertNull(Discovery.found(mapOf("farm" to null), "192.168.1.9", 7443))
        assertNull(Discovery.found(mapOf("farm" to "  ".toByteArray()), "192.168.1.9", 7443))
        assertNull(Discovery.found(txt, null, 7443))
        assertNull(Discovery.found(txt, "fe80::1", 7443))
        assertNull(Discovery.found(txt, "192.168.1.256", 7443))
        assertNull(Discovery.found(txt, "192.168.1.9", 0))
    }

    private fun state(vararg links: Link) = ShellState(
        farms = links.mapIndexed { i, l -> FarmStatus(PairedFarm("f$i", "Farm $i", "d", "manage", "fp", listOf("10.0.0.$i:7443"), "k$i"), l) },
    )

    @Test
    fun itLooksOnlyWhileAFarmIsOutOfReachForAReasonANewAddressCouldFix() {
        val online = Link.Online("10.0.0.1:7443", "manage", emptyMap(), 1)
        assertFalse(Discovery.wanted(state()))
        assertFalse(Discovery.wanted(state(online)))
        assertFalse("a revoked device is not helped by a new address", Discovery.wanted(state(online, Link.Refused("revoked", null, 1))))
        assertTrue(Discovery.wanted(state(online, Link.Offline("unreachable", 1))))
        assertTrue(Discovery.wanted(state(Link.Connecting("10.0.0.1:7443", 3, 1))))
    }

    private companion object { const val SERVICE = Discovery.SERVICE_TYPE }
}
