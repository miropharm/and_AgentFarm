package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCallTest {
    private val answer = """{"spoken":{"text":"Seni bekleyen bir şey var: psikolog sana soruyor.","lang":"tr"},"waiting":1,"running":0,
        "asks":[{"session":"c_psy","agent":"psikolog","askId":"q7","question":"Hangi dal?","options":[{"label":"master"},{"label":"yeni dal"}],
        "spoken":{"text":"psikolog soruyor: Hangi dal?","lang":"tr"}}],"later":true}"""

    @Test
    fun theFarmsAnswerIsReadAsItSaidItWithItsQuestions() {
        val said = StatusCall.of(CallResult("1", ok = true, result = Codec.json.parseToJsonElement(answer)), "en")
        said as StatusCall.Farm
        assertEquals("Seni bekleyen bir şey var: psikolog sana soruyor.", said.summary.spoken.text)
        assertEquals("tr", said.summary.spoken.lang)
        assertEquals(listOf("master", "yeni dal"), said.summary.asks.single().options.map { it.label })
        assertEquals("c_psy", said.summary.asks.single().session)
    }

    @Test
    fun noAnswerOrARefusalIsSaidInThePhonesOwnWords() {
        assertEquals(StatusCall.Own("Agent Farm şu an cevap vermiyor."), StatusCall.of(null, "tr"))
        assertEquals(StatusCall.Own("Agent Farm does not answer right now."), StatusCall.of(null, "de"))
        val old = StatusCall.of(CallResult("2", ok = false, error = "unknown op now.summary"), "en")
        assertTrue((old as StatusCall.Own).text.contains("update on the computer"))
        val odd = StatusCall.of(CallResult("3", ok = true, result = JsonPrimitive("x")), "en")
        assertTrue(odd is StatusCall.Own)
        assertEquals(StatusCall.Own("Agent Farm durumunu söyleyemedi: bilgisayarda güncellenmesi gerekebilir."), StatusCall.of(CallResult("4", ok = false), "tr"))
    }

    @Test
    fun theFarmAskedIsTheFocusedOneElseTheFirstConnectedElseTheFirstPaired() {
        val a = status("a", Link.Resting(since = 0))
        val b = status("b", Link.Online("10.0.0.2:7443", "manage", emptyMap(), since = 0))
        assertEquals("a", StatusCall.farmOf(ShellState(listOf(a, b), focused = "a")))
        assertEquals("b", StatusCall.farmOf(ShellState(listOf(a, b), focused = "gone")))
        assertEquals("a", StatusCall.farmOf(ShellState(listOf(a))))
        assertNull(StatusCall.farmOf(ShellState()))
    }

    @Test
    fun theVoicesLanguageTravelsAsTheArgument() {
        assertEquals("""{"lang":"tr-TR"}""", StatusCall.args("tr-TR").toString())
    }

    private fun status(id: String, link: Link) = FarmStatus(PairedFarm(id, id, "d", "manage", "fp", listOf("10.0.0.2:7443"), "k"), link)
}
