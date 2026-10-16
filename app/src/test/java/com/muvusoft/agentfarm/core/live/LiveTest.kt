package com.muvusoft.agentfarm.core.live

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.contract.LiveFrame
import com.muvusoft.agentfarm.core.conversation.ConversationState
import com.muvusoft.agentfarm.core.conversation.Message
import com.muvusoft.agentfarm.core.conversation.Role
import com.muvusoft.agentfarm.core.now.NowState
import com.muvusoft.agentfarm.core.now.Recent
import com.muvusoft.agentfarm.core.now.Running
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.Asks
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTest {
    private val example: String by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "contract/remote-contract.v1.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("frames").jsonObject.getValue("live").jsonObject.getValue("example").toString()
    }

    private fun data(json: String): LiveCard = Live.read(Json.parseToJsonElement(json).jsonObject)!!

    private fun beat(status: String, rows: String? = null, asking: String = "null", step: String = "null", cost: String = "0.5") = data(
        """{"agent":"developer","status":"$status","turnSince":${if (status == "working") 1000 else "null"},"cost":$cost,"steerable":true,
           "step":$step,"asking":$asking${rows?.let { ",\"messages\":[$it]" } ?: ""}}""",
    )

    private fun row(key: String, role: String, ts: Long, text: String) = """{"key":"$key","turn":3,"role":"$role","ts":$ts,"text":"$text"}"""

    private val question = """{"kind":"question","tool":"AskUserQuestion","questions":[{"question":"Hangi dalda?","header":"Dal","multiSelect":false,
        "options":[{"label":"master","description":"Ana dal"},{"label":"yeni dal"}]}],"detail":"Hangi dalda?","waitingSince":900,"autoAnswer":null}"""

    private fun read(i: Int, key: String, ts: Long, role: Role = Role.ASSISTANT) = Message(i, 3, role, ts, "r$i", key = key)

    private val conv = ConversationState("c_dev", "developer", "Telefon", "Claude", Link.Offline("closed", 0), 0)

    @Test
    fun theContractsExampleReadsAsALiveBeat() {
        val frame = Codec.decode(example) as LiveFrame
        val d = Live.read(frame.data)!!
        assertEquals("developer", d.agent)
        assertTrue(d.working)
        assertEquals(Step("Bash", "node test/run-all.js", 1790760012000), d.step)
        assertNull(d.asking)
        val m = d.messages!!.single()
        assertEquals(Role.TOOL, m.role)
        assertEquals("9b1f0c2e-1d2a-4c3b-8e4f-5a6b7c8d9e0f", m.key)
        assertTrue(m.provisional)
    }

    @Test
    fun aSummaryBeatCarriesNoRowsAndABrokenOneDoesNotRead() {
        assertNull(beat("idle").messages)
        assertNull(Live.read(JsonObject(emptyMap())))
    }

    @Test
    fun askingReadsAsConsoleSessionsSaysIt() {
        val q = Asks.read(Json.parseToJsonElement(question))!!
        assertEquals(AskKind.QUESTION, q.kind)
        assertEquals(listOf("master", "yeni dal"), q.questions.single().options.map { it.label })
        assertEquals("Ana dal", q.questions.single().options.first().description)
        assertEquals(900L, q.since)
        val p = Asks.read(Json.parseToJsonElement("""{"kind":"permission","tool":"Bash","questions":[],"detail":"git push","waitingSince":5}"""))!!
        assertEquals(AskKind.PERMISSION, p.kind)
        assertEquals("git push", p.detail)
        assertNull(Asks.read(JsonNull))
        assertNull(Asks.read(null))
    }

    @Test
    fun nowPutsAWorkingCardUnderRunningWithTheTitleItAlreadyKnew() {
        val s = NowState("Ev PC", Link.Offline("closed", 0), 0, recent = listOf(Recent("c_dev", "developer", "Testler", "ok", 1)))
        val step = """{"tool":"Bash","detail":"npm test","since":1200}"""
        val r = LiveMerge.now(s, "c_dev", beat("working", step = step), now = 5000).running.single()
        assertEquals(Running("c_dev", "developer", "Testler", Step("Bash", "npm test", 1200), 1000, 0.5), r)
    }

    @Test
    fun nowMovesAnAskingCardToWaitingAndAnIdleOneOutKeepingTheOthersInPlace() {
        val other = Running("c_a", "yazar", "A", null, 10, null)
        val dev = Running("c_dev", "developer", "Testler", null, 10, null)
        val s = NowState("Ev PC", Link.Offline("closed", 0), 0, running = listOf(dev, other))
        val asking = LiveMerge.now(s, "c_dev", beat("working", asking = question), now = 5000)
        assertEquals(listOf("c_a"), asking.running.map { it.session })
        assertEquals("Testler", asking.waiting.single().title)
        assertEquals(AskKind.QUESTION, asking.waiting.single().ask.kind)
        val back = LiveMerge.now(asking, "c_dev", beat("working"), now = 6000)
        assertTrue(back.waiting.isEmpty())
        assertEquals("Testler", back.running.single { it.session == "c_dev" }.title)
        val moved = LiveMerge.now(s, "c_dev", beat("working", cost = "0.9"), now = 6000)
        assertEquals(listOf("c_dev", "c_a"), moved.running.map { it.session })
        assertEquals(0.9, moved.running.first().cost!!, 0.0)
        val idle = LiveMerge.now(s, "c_dev", beat("idle"), now = 7000)
        assertEquals(listOf("c_a"), idle.running.map { it.session })
        assertEquals(7000L, idle.updatedAt)
    }

    @Test
    fun conversationTakesTheCardsStateFromTheBeat() {
        val step = """{"tool":"Read","detail":"a.kt","since":1500}"""
        val c = LiveMerge.conversation(conv, beat("working", rows = "", step = step, asking = question), now = 2000)
        assertTrue(c.running)
        assertEquals(1000L, c.turnSince)
        assertEquals(Step("Read", "a.kt", 1500), c.live)
        assertEquals(AskKind.QUESTION, c.ask!!.kind)
        assertTrue(c.steerable)
        val done = LiveMerge.conversation(c, beat("idle", rows = ""), now = 3000)
        assertFalse(done.running)
        assertNull(done.turnSince)
        assertNull(done.live)
        assertNull(done.ask)
        assertTrue(LiveMerge.turnEnded(c, beat("idle")))
        assertFalse(LiveMerge.turnEnded(c, beat("working")))
        assertFalse(LiveMerge.turnEnded(done, beat("idle")))
    }

    @Test
    fun liveRowsFollowTheReadOnesAndAStreamedAnswerGrowsInPlace() {
        val s = conv.copy(messages = listOf(read(41, "k41", 100), read(42, "k42", 200)))
        val one = LiveMerge.conversation(s, beat("working", rows = row("c:1", "user", 300, "Testleri çalıştır") + "," + row("c:2", "assistant", 310, "Baş")), now = 400)
        assertEquals(listOf(41, 42, 43, 44), one.messages.map { it.i })
        assertEquals(listOf(false, false, true, true), one.messages.map { it.provisional })
        val two = LiveMerge.conversation(one, beat("working", rows = row("c:2", "assistant", 310, "Başladım, testler geçti.")), now = 500)
        assertEquals(4, two.messages.size)
        assertEquals("Başladım, testler geçti.", two.messages.last().text)
        assertEquals(LiveMerge.readFrom(two), 43)
    }

    @Test
    fun aRowTheReadAlreadyCarriesIsUpdatedNotRepeated() {
        val s = conv.copy(messages = listOf(read(41, "u-41", 100), read(42, "u-42", 200)))
        val c = LiveMerge.conversation(s, beat("working", rows = row("u-42", "assistant", 200, "uzadı")), now = 300)
        assertEquals(listOf(41, 42), c.messages.map { it.i })
        assertEquals("uzadı", c.messages.last().text)
        assertFalse(c.messages.last().provisional)
    }

    @Test
    fun rowsOfARoleTheScreenDoesNotReadAreLeftOut() {
        val c = LiveMerge.conversation(conv, beat("working", rows = row("c:1", "tool", 300, "x") + "," + row("c:2", "assistant", 310, "y")), now = 400,
            roles = setOf(Role.USER, Role.ASSISTANT))
        assertEquals(listOf(Role.ASSISTANT), c.messages.map { it.role })
    }

    @Test
    fun theReadPutsTheRecordInPlaceOfWhatItCoversAndKeepsWhatIsNewer() {
        val s = conv.copy(messages = listOf(read(41, "k41", 100)))
        val told = LiveMerge.conversation(s, beat("working", rows = listOf(
            row("u-a", "user", 300, "soru"), row("c:b", "assistant", 310, "cevap"), row("c:c", "user", 900, "sonraki"),
        ).joinToString(",")), now = 950)
        assertEquals(42, LiveMerge.readFrom(told))
        val page = listOf(read(42, "u-a", 299, Role.USER), read(43, "#43", 315))
        val c = LiveMerge.confirm(told, page)
        assertEquals(listOf(41, 42, 43, 44), c.messages.map { it.i })
        assertEquals(listOf("k41", "u-a", "#43", "c:c"), c.messages.map { it.key })
        assertEquals(listOf(false, false, false, true), c.messages.map { it.provisional })
        assertEquals(44, LiveMerge.readFrom(c))
        val again = LiveMerge.confirm(c, listOf(read(43, "#43", 315)))
        assertEquals(listOf(41, 42, 43, 44), again.messages.map { it.i })
    }

    @Test
    fun anEmptyReadDropsNothing() {
        val told = LiveMerge.conversation(conv, beat("working", rows = row("c:1", "user", 300, "x")), now = 400)
        assertEquals(told.messages, LiveMerge.confirm(told, emptyList()).messages)
        assertEquals(1, LiveMerge.readFrom(told))
    }
}
