package com.muvusoft.agentfarm.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {
    // Built by Agent Farm's pairingUri (URLSearchParams: space is '+', comma and colon are escaped).
    private val uri = "agentfarm://pair?v=1&farm=farm_7f3a&name=Masa%C3%BCst%C3%BC+PC&code=7Q2M-9KXD" +
        "&fp=AB12CD&a=192.168.1.20%3A8378%2C100.64.0.7%3A8378"

    @Test
    fun readsEveryField() {
        val p = Pairing.parse(uri)!!
        assertEquals(1L, p.v)
        assertEquals("farm_7f3a", p.farm)
        assertEquals("Masaüstü PC", p.name)
        assertEquals("7Q2M-9KXD", p.code)
        assertEquals("ab12cd", p.fp)
        assertEquals(listOf("192.168.1.20:8378", "100.64.0.7:8378"), p.addresses)
    }

    @Test
    fun refusesWhatIsNotAPairingLink() {
        assertNull(Pairing.parse("https://example.com/?v=1"))
        assertNull(Pairing.parse(uri.replace("&code=7Q2M-9KXD", "")))
        assertNull(Pairing.parse(uri.replace("v=1", "v=one")))
        assertNull(Pairing.parse(uri.replace(Regex("&a=[^&]*"), "&a=%2C")))
    }
}
