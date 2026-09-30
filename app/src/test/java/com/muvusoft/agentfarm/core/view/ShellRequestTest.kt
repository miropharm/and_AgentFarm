package com.muvusoft.agentfarm.core.view

import com.muvusoft.agentfarm.core.contract.Codec
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellRequestTest {
    private fun of(json: String) = ShellRequest.of(Codec.json.parseToJsonElement(json))

    @Test
    fun aPageLinkOpensThatPageInTheShell() {
        assertEquals(ShellRequest.Open("needs"), of("""{"type":"afnav","to":"needs"}"""))
    }

    @Test
    fun aLinkToSomethingThatIsNotAPageIsRefusedNotSentToTheFarm() {
        listOf(
            """{"type":"afnav","to":"../x"}""",
            """{"type":"afnav","to":"a/b"}""",
            """{"type":"afnav","to":""}""",
            """{"type":"afnav","to":7}""",
            """{"type":"afnav"}""",
        ).forEach { assertEquals(it, ShellRequest.Refused, of(it)) }
    }

    @Test
    fun aCopyStaysOnThePhoneWithItsToken() {
        assertEquals(ShellRequest.Copy(JsonPrimitive("t1"), "merhaba"), of("""{"type":"afClipboard","token":"t1","text":"merhaba"}"""))
        assertEquals(ShellRequest.Copy(JsonNull, ""), of("""{"type":"afClipboard"}"""))
    }

    @Test
    fun everythingElseIsTheFarms() {
        listOf("""{"type":"refresh"}""", """{"type":"send","text":"x"}""", """{"to":"needs"}""", "[1]", "\"afnav\"", "null")
            .forEach { assertNull(it, of(it)) }
    }

    @Test
    fun theCopyAnswerHasTheDesktopHostsShape() {
        assertEquals(
            """{"type":"afClipboardDone","token":"t1","ok":true}""",
            ShellRequest.copyDone(JsonPrimitive("t1"), true).toString(),
        )
        assertEquals(
            """{"type":"afClipboardDone","token":null,"ok":false,"error":"x"}""",
            ShellRequest.copyDone(JsonNull, false, "x").toString(),
        )
    }

    @Test
    fun aDictationStaysOnThePhoneWithItsToken() {
        assertEquals(ShellRequest.Voice(JsonPrimitive(7)), of("""{"type":"afVoice","token":7}"""))
        assertEquals(ShellRequest.Voice(JsonNull), of("""{"type":"afVoice"}"""))
    }

    @Test
    fun whatWasHeardIsTheFirstRealResult() {
        assertEquals("devam et", ShellRequest.heard(listOf("", " devam et ", "devam")))
        assertEquals(null, ShellRequest.heard(listOf(" ")))
        assertEquals(null, ShellRequest.heard(null))
    }

    @Test
    fun theDictationAnswerCarriesTheFirstRealResultOrNothing() {
        assertEquals(
            """{"type":"afVoiceDone","token":7,"text":"testleri çalıştır"}""",
            ShellRequest.voiceDone(JsonPrimitive(7), listOf("  ", " testleri çalıştır ", "testler")).toString(),
        )
        assertEquals("""{"type":"afVoiceDone","token":7}""", ShellRequest.voiceDone(JsonPrimitive(7), null).toString())
        assertEquals(
            """{"type":"afVoiceDone","token":7,"error":"yok"}""",
            ShellRequest.voiceDone(JsonPrimitive(7), emptyList(), "yok").toString(),
        )
    }
}
