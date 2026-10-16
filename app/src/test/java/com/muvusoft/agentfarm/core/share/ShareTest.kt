package com.muvusoft.agentfarm.core.share

import com.muvusoft.agentfarm.core.contract.CallResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
              {"id":"b","agent":"developer","status":"working"},
              {"id":"c","agent":"eski","status":"ended"},
              {"id":"d","agent":"psikolog","status":"working","asking":{"kind":"question"}},
              {"id":"e","agent":"ops","status":"running"},
              {"agent":"kimliksiz","status":"idle"},
              "bozuk"
            ]""",
        )
        val t = Share.targets(rows)
        assertEquals(listOf("d", "b", "a", "e"), t.map { it.id })
        assertEquals("waiting for you", Share.statusWord(t[0]))
        assertEquals("running", Share.statusWord(t[1]))
        assertEquals("idle", Share.statusWord(t[2]))
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
        assertEquals("Queued; it is said when the turn ends.", Share.outcome(true, json("""{"held":true}"""), null))
        assertEquals("Sent.", Share.outcome(true, json("""{"sent":true}"""), null))
        assertEquals("Not sent: scope: console.send needs manage", Share.outcome(false, null, "scope: console.send needs manage"))
        assertEquals("Not sent: the farm did not answer", Share.outcome(false, null, null))
    }

    @Test
    fun aSendTheFarmStoppedAtAGateNamesTheGateToConfirm() {
        val stopped = json("""{"sent":false,"confirm":"risk","note":"⚠️ This asks for rm -rf on developer, which cannot be undone.\nRun it?"}""")
        assertEquals("risk", Share.asks(true, stopped))
        assertEquals("budget", Share.asks(true, json("""{"sent":false,"confirm":"budget","note":"x"}""")))
        // The farm's own sentence is what the screen shows while it waits.
        assertEquals("⚠️ This asks for rm -rf on developer, which cannot be undone.\nRun it?", Share.outcome(true, stopped, null))
        // A gate the app has never heard of is still echoed back: the farm judges it.
        assertEquals("cost", Share.asks(true, json("""{"sent":false,"confirm":"cost"}""")))
        assertNull(Share.asks(true, json("""{"note":"→ developer"}""")))
        assertNull(Share.asks(true, json("""{"sent":true}""")))
        assertNull(Share.asks(true, json("""{"confirm":"risk"}""")))
        assertNull(Share.asks(true, json("""{"sent":false,"confirm":" "}""")))
        assertNull(Share.asks(false, stopped))
        assertNull(Share.asks(true, null))
    }

    @Test
    fun aLongPreviewIsCut() {
        val long = "a".repeat(Share.PREVIEW_CHARS + 50)
        assertEquals(Share.PREVIEW_CHARS + 1, Share.preview(long).length)
        assertEquals("kısa", Share.preview("kısa"))
    }

    @Test
    fun aShareIsWordsAPictureOrBothAndNothingWithoutEither() {
        val pic = SharedImage("content://media/1", "ekran.png", "image/png", 2_000)
        assertNull(Shared.of(null, "  ", null))
        assertEquals(Shared("", pic), Shared.of(null, null, pic))
        assertEquals(Shared("Not\nbak", pic), Shared.of("Not", "bak", pic))
        assertFalse(pic.tooBig)
        assertTrue(pic.copy(bytes = Share.IMAGE_MAX_BYTES + 1).tooBig)
        assertFalse(pic.copy(bytes = null).tooBig)
        assertEquals("2 KB", Share.size(2_000))
        assertTrue(Share.size(3 * 1_048_576L).startsWith("3"))
    }

    @Test
    fun aShareGoesIntoASessionOrOpensANewOneWithItsPicture() {
        val (op, args) = Share.call("c_dev", "bak", "C:\\Temp\\a.png", null)
        assertEquals("console.send", op)
        assertEquals("""{"id":"c_dev","text":"bak","attach":["C:\\Temp\\a.png"]}""", args.toString())
        val (newOp, newArgs) = Share.call(Share.newTarget("psikolog"), "yarın", null, null)
        assertEquals("console.new", newOp)
        assertEquals("""{"agent":"psikolog","prompt":"yarın"}""", newArgs.toString())
        assertEquals("""{"agent":"psikolog"}""", Share.call(Share.newTarget("psikolog"), " ", null, null).second.toString())
        assertEquals("risk", Share.call("c_dev", "rm -rf x", null, "risk").second["confirm"].toString().trim('"'))
        assertEquals("psikolog", Share.newAgent(Share.newTarget("psikolog")))
        assertNull(Share.newAgent("c_dev"))
        assertNull(Share.newAgent(null))
        assertEquals(listOf("developer", "psikolog"), Share.agents(json("""{"agents":["developer","psikolog",""]}""")))
        assertEquals(emptyList<String>(), Share.agents(json("""{"error":"unknown op"}""")))
        assertEquals("C:\\Temp\\a.png", Share.stagedPath(json("""{"path":"C:\\Temp\\a.png"}""")))
        assertNull(Share.stagedPath(json("{}")))
    }

    @Test
    fun aFarmThatDoesNotAnswerIsChosenOnlyWhenPickedOrWhenNoneAnswers() {
        val online = listOf("b")
        val offered = listOf("a", "b", "c")
        assertEquals("c", Share.farmFor("c", "a", online, offered))
        assertEquals("b", Share.farmFor(null, "a", online, offered))
        assertEquals("b", Share.farmFor("gone", "b", online, offered))
        assertEquals("a", Share.farmFor(null, "a", emptyList(), offered))
        assertEquals("a", Share.farmFor(null, "z", emptyList(), offered))
        assertNull(Share.farmFor("a", "a", emptyList(), emptyList()))
    }

    @Test
    fun aQueuedShareSaysWhereItWaitsAndItsReceiptHowItWent() {
        assertEquals("Masaüstü", Share.farmLabel("Masaüstü", true))
        assertEquals("Masaüstü · not connected", Share.farmLabel("Masaüstü", false))
        val queued = Share.queued("developer", "Masaüstü", 6)
        assertTrue(queued, queued.contains("developer") && queued.contains("Masaüstü") && queued.contains("6 h"))

        val expired = Share.receipt("developer", "Masaüstü", null, 6)
        assertFalse(expired.delivered)
        assertEquals("Not sent to developer · Masaüstü", expired.title)
        assertTrue(expired.text, expired.text.contains("6 h") && expired.text.contains("dropped"))

        val refused = Share.receipt("developer", "Masaüstü", CallResult("c_1", false, error = "no such session"), 6)
        assertFalse(refused.delivered)
        assertEquals("Not sent: no such session", refused.text)

        val gated = CallResult("c_1", true, json("""{"sent":false,"confirm":"risk","message":"This command cannot be undone."}"""))
        val stopped = Share.receipt("developer", "Masaüstü", gated, 6)
        assertFalse(stopped.delivered)
        assertEquals("This command cannot be undone. Share it again to answer.", stopped.text)

        val sent = Share.receipt("developer", "Masaüstü", CallResult("c_1", true, json("""{"sent":true,"note":"→ developer"}""")), 6)
        assertTrue(sent.delivered)
        assertEquals("Delivered to developer · Masaüstü", sent.title)
        assertEquals("→ developer", sent.text)
    }
}
