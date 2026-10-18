package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNewTest {
    private val agents = listOf("developer", "psikolog", "yazar")

    @Test
    fun theAgentIsNamedByTheFarmsOwnListElseByTheWords() {
        assertEquals("developer", VoiceNew.agent("Developer.", agents))
        assertEquals("psikolog", VoiceNew.agent("psikolog olsun", agents))
        assertEquals("muhasebeci", VoiceNew.agent(" muhasebeci ", agents))
        assertNull(VoiceNew.agent("  ", agents))
    }

    @Test
    fun theSessionOpensThroughConsoleNewWithTheAgentAndTheWords() {
        val call = VoiceNew.call("developer", "sürüm notunu hazırla")
        assertEquals("console.new", call.op)
        assertEquals("""{"agent":"developer","prompt":"sürüm notunu hazırla"}""", call.args.toString())
    }

    @Test
    fun theQuestionsAndTheReadBackAreInTheVoicesLanguage() {
        assertEquals("Hangi ajan?", VoiceNew.whichAgent("tr"))
        assertEquals("developer ne yapsın?", VoiceNew.whatToDo("tr", "developer"))
        assertEquals("developer: testleri çalıştır. Açıyorum.", VoiceNew.readBack("tr", "developer", "testleri çalıştır"))
        assertEquals("What should developer do?", VoiceNew.whatToDo("de", "developer"))
    }

    @Test
    fun theFarmsAnswerIsSaid() {
        val ok = CallResult("1", ok = true, result = Codec.json.parseToJsonElement("""{"id":"c_new1","agent":"developer","note":"opened"}"""))
        assertEquals("developer açıldı.", VoiceNew.said("tr", "Developer", ok))
        assertEquals("No agent named muhasebeci.", VoiceNew.said("en", "muhasebeci", CallResult("2", ok = false, error = "No agent named muhasebeci.")))
        assertEquals("Agent Farm bunu açmadı.", VoiceNew.said("tr", "x", CallResult("3", ok = false)))
        assertEquals(VoiceAnswer.waits("tr"), VoiceNew.said("tr", "developer", null))
    }
}
