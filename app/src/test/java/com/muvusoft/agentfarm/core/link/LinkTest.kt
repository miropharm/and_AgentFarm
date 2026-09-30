package com.muvusoft.agentfarm.core.link

import com.muvusoft.agentfarm.core.Time
import com.muvusoft.agentfarm.core.state.Link
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {
    private fun contractReasons(): List<String> {
        val file = listOf(File("../contract/remote-contract.v1.json"), File("contract/remote-contract.v1.json")).first { it.exists() }
        val refuse = Json.parseToJsonElement(file.readText()).jsonObject["frames"]!!.jsonObject["refuse"]!!.jsonObject
        return refuse["reasons"]!!.jsonArray.map { it.jsonPrimitive.content }
    }

    @Test
    fun everyContractRefuseReasonHasItsOwnSentence() {
        val reasons = contractReasons()
        assertTrue(reasons.isNotEmpty())
        for (r in reasons) assertNotEquals("no sentence for refuse reason $r", r, LinkText.refused(r))
    }

    @Test
    fun onlyRefusalsNoRetryCanFixStopTheLink() {
        val retryable = setOf("not-allowed", "busy")
        for (r in contractReasons()) assertEquals(r, r !in retryable, Reconnect.isFinal(r))
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        assertEquals(0, Reconnect.delayMs(0, 0.0))
        assertEquals(1_000, Reconnect.delayMs(1, 0.0))
        assertEquals(4_000, Reconnect.delayMs(3, 0.0))
        assertEquals(Reconnect.MAX_MS, Reconnect.delayMs(30, 0.0))
        assertEquals(1_250, Reconnect.delayMs(1, 1.0))
    }

    @Test
    fun theLastGoodAddressIsTriedFirst() {
        val a = listOf("192.168.1.2:8743", "100.64.0.2:8743")
        assertEquals(a, Reconnect.order(a, null))
        assertEquals(a.reversed(), Reconnect.order(a, "100.64.0.2:8743"))
        assertEquals(a, Reconnect.order(a, "gone:1"))
    }

    @Test
    fun everyWaitSaysKindReasonAndSince() {
        val t = LinkText.of(Link.Connecting("10.0.0.2:8743", 3, 0), 65_000)
        assertTrue(t.short.contains("deneme 3"))
        assertTrue(t.long.contains("1 dk"))
        val refused = LinkText.of(Link.Refused("revoked", null, 0), 0)
        assertTrue(refused.short.startsWith("Reddedildi"))
        assertTrue(refused.long.contains("yeniden eşleyin"))
        assertFalse(LinkText.of(Link.Online("a:1", "manage", emptyMap(), 0), 0).short.contains("Reddedildi"))
    }

    @Test
    fun elapsedTimeIsShort() {
        assertEquals("0 sn", Time.ago(-5))
        assertEquals("59 sn", Time.ago(59_999))
        assertEquals("2 sa", Time.ago(7_200_000))
        assertEquals("3 gün", Time.ago(3 * 86_400_000L))
    }

    @Test
    fun theSheetOpensWithTheLongFormAndListsTheAddresses() {
        val link = Link.Offline("unreachable", 0)
        val lines = LinkText.sheet(link, listOf("10.0.0.2:7443", "pc.local:7443"), 60_000)
        assertEquals(LinkText.of(link, 60_000).long, lines.first())
        assertEquals("Adresler: 10.0.0.2:7443, pc.local:7443", lines[1])
        assertEquals(1, LinkText.sheet(link, emptyList(), 60_000).size)
    }
}
