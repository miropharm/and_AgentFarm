package com.muvusoft.agentfarm.core.pairing

import com.muvusoft.agentfarm.core.contract.FarmRef
import com.muvusoft.agentfarm.core.contract.PairResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingVerdictTest {
    private val link = "agentfarm://pair?v=1&farm=farm_7f3a&name=Masa%C3%BCst%C3%BC&code=7Q2M&fp=AB12&a=192.168.1.5:8743,100.64.0.2:8743"

    private fun ready() = (PairingVerdict.read(link) as PairingStep.Ready).info

    @Test
    fun aValidLinkIsReady() {
        val info = ready()
        assertEquals("farm_7f3a", info.farm)
        assertEquals("ab12", info.fp)
        assertEquals(listOf("192.168.1.5:8743", "100.64.0.2:8743"), info.addresses)
    }

    @Test
    fun anythingElseIsNotALink() {
        assertEquals(PairingStep.Rejected(PairingProblem.NotALink), PairingVerdict.read("https://example.com"))
        assertEquals(PairingStep.Rejected(PairingProblem.NotALink), PairingVerdict.read(link.replace("&code=7Q2M", "")))
    }

    @Test
    fun aNewerContractAsksForAnUpdate() {
        val step = PairingVerdict.read(link.replace("v=1", "v=2"))
        assertEquals(PairingStep.Rejected(PairingProblem.NewerContract(2)), step)
    }

    @Test
    fun theRequestCarriesTheLinksCode() {
        val r = PairingVerdict.request(ready(), "Pixel 8", "PK", "android 14", "0.1.0-b1")
        assertEquals("7Q2M", r.code)
        assertEquals("PK", r.publicKey)
    }

    @Test
    fun theAnswerOfTheNamedFarmBecomesAPairedFarm() {
        val r = PairingVerdict.accept(ready(), PairResponse("dev_1", FarmRef("farm_7f3a", "Masaüstü"), "manage"), "alias1")
        val farm = (r as PairingVerdict.Result.Paired).farm
        assertEquals("dev_1", farm.device)
        assertEquals("ab12", farm.fp)
        assertEquals("alias1", farm.key)
        assertEquals(2, farm.addresses.size)
    }

    @Test
    fun anAnswerFromAnotherFarmIsRefused() {
        val r = PairingVerdict.accept(ready(), PairResponse("dev_1", FarmRef("farm_other", "X"), "manage"), "a")
        assertEquals(PairingVerdict.Result.Failed(PairingProblem.WrongFarm), r)
    }

    @Test
    fun a403MeansTheCodeIsSpent() {
        assertEquals(PairingProblem.CodeUsed, PairingVerdict.refused(403, "code"))
        assertTrue(PairingVerdict.refused(400, "missing").message.contains("missing"))
    }
}
