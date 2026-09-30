package com.muvusoft.agentfarm.core.share

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test
    fun theSubjectLeadsUnlessTheTextAlreadyStartsWithIt() {
        assertEquals("Başlık\nhttps://x.y", Share.text("Başlık", "https://x.y"))
        assertEquals("Başlık ve devamı", Share.text("Başlık", "Başlık ve devamı"))
        assertEquals("yalnız metin", Share.text(null, "  yalnız metin "))
        assertEquals("yalnız konu", Share.text("yalnız konu", ""))
        assertNull(Share.text(" ", null))
    }

    @Test
    fun endedSessionsAreDroppedAndOneWaitingOnYouComesFirst() {
        val rows = json(
            """[
              {"id":"a","agent":"yazar","status":"idle","asking":null},
              {"id":"b","agent":"developer","status":"running"},
              {"id":"c","agent":"eski","status":"ended"},
              {"id":"d","agent":"psikolog","status":"running","asking":{"kind":"question"}},
              {"agent":"kimliksiz","status":"idle"},
              "bozuk"
            ]""",
        )
        val t = Share.targets(rows)
        assertEquals(listOf("d", "b", "a"), t.map { it.id })
        assertEquals("seni bekliyor", Share.statusWord(t[0]))
        assertEquals("çalışıyor", Share.statusWord(t[1]))
        assertEquals("boşta", Share.statusWord(t[2]))
        assertEquals(emptyList<ShareTarget>(), Share.targets(json("""{"error":"x"}""")))
        assertEquals(emptyList<ShareTarget>(), Share.targets(null))
    }

    @Test
    fun anUnknownStatusIsShownAsItself() {
        assertEquals("parked", Share.statusWord(ShareTarget("x", "a", "parked", false)))
    }

    @Test
    fun theOutcomeIsTheHostsOwnSentenceWhenItGaveOne() {
        assertEquals("Said into the running turn.", Share.outcome(true, json("""{"sentence":"Said into the running turn."}"""), null))
        assertEquals("Sıraya alındı; tur bitince söylenecek.", Share.outcome(true, json("""{"held":true}"""), null))
        assertEquals("Gönderildi.", Share.outcome(true, json("""{"sent":true}"""), null))
        assertEquals("Gönderilemedi: scope: console.send needs manage", Share.outcome(false, null, "scope: console.send needs manage"))
        assertEquals("Gönderilemedi: çiftlik yanıt vermedi", Share.outcome(false, null, null))
    }

    @Test
    fun aLongPreviewIsCut() {
        val long = "a".repeat(Share.PREVIEW_CHARS + 50)
        assertEquals(Share.PREVIEW_CHARS + 1, Share.preview(long).length)
        assertEquals("kısa", Share.preview("kısa"))
    }
}
