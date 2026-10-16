package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.CallResult
import com.muvusoft.agentfarm.core.contract.ConsoleRow
import com.muvusoft.agentfarm.core.contract.NeedsList
import com.muvusoft.agentfarm.core.contract.Ops
import com.muvusoft.agentfarm.core.contract.RecentPage
import com.muvusoft.agentfarm.core.contract.RecentRow
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.conversation.ConversationText
import com.muvusoft.agentfarm.core.live.LiveCard
import com.muvusoft.agentfarm.core.live.LiveMerge
import com.muvusoft.agentfarm.core.session.Answers
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.Quick
import com.muvusoft.agentfarm.core.session.Step
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.view.PageRoute
import com.muvusoft.agentfarm.core.view.ShellRequest
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Now is built from the farm's own answers, starting from the contract's real examples.
class NowBuildTest {
    private val ops: JsonObject by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "contract/remote-contract.v1.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject.getValue("ops").jsonObject
    }

    private fun example(op: String) = ops.getValue(op).jsonObject.getValue("example")

    private val asking: ConsoleRow by lazy { Ops.consoleRows(example("console.sessions"))!!.first() }

    private val needs: NeedsList by lazy { Ops.read(NeedsList.serializer(), example("needs.list"))!! }

    private val page: RecentPage by lazy { Ops.read(RecentPage.serializer(), example("sessions.recent"))!! }

    private val online = Link.Online("192.168.1.20:7300", "manage", emptyMap(), 0)
    private fun working(id: String, session: String) = asking.copy(id = id, sessionId = session, asking = null, status = "working", cost = 1.5)
    private fun row(id: String, ts: Long, title: String = "T $id") = RecentRow(id, "developer", title, "claude", ts, "user", null, false, "said $id")
    private fun need(kind: String, session: String?, key: String) = needs.items.first().copy(
        kind = kind, key = key, ref = JsonObject(session?.let { mapOf("sessionId" to JsonPrimitive(it)) } ?: emptyMap()),
    )

    @Test
    fun aCardThatAsksWaitsAndItsQuestionIsAnsweredFromTheRow() {
        val s = NowBuild.of("Ev PC", online, 5, listOf(asking), null, null)
        val w = s.waiting.single()
        assertEquals(asking.id, w.session)
        assertEquals(AskKind.QUESTION, w.ask.kind)
        assertTrue(s.running.isEmpty())
        val q = Waits.quick(w)
        assertEquals(listOf("master", "yeni dal"), q.map { (it as Quick.Option).label })
        val call = Waits.answer(w, q.first())!!
        assertEquals(Answers.question(asking.id, w.ask.questions.single().question, "master"), call)
        assertEquals("console.answer", call.op)
    }

    @Test
    fun aWorkingCardRunsWithItsSessionAndNoTurnTimeUntilTheLiveChannelSaysIt() {
        val s = NowBuild.of("Ev PC", online, 5, listOf(working("c_a", "s-a")), RecentPage(listOf(row("s-a", 90, "Testler")), null), null)
        val r = s.running.single()
        assertEquals(Running("c_a", asking.agent, "Testler", null, null, 1.5, "s-a"), r)
        assertTrue("a running session is not drawn again in the history", s.finished.isEmpty())
        assertEquals(1, s.recent.size)
        assertEquals(mapOf("c_a" to "s-a"), s.cards)
    }

    @Test
    fun anIdleCardIsNeitherWaitingNorRunning() {
        val s = NowBuild.of("Ev PC", online, 5, listOf(working("c_a", "s-a").copy(status = "idle")), null, null)
        assertTrue(s.waiting.isEmpty() && s.running.isEmpty())
    }

    @Test
    fun aNeedElsewhereIsItsOwnRowAndAPermissionIsSettledByItsKey() {
        val s = NowBuild.of("Ev PC", online, 5, emptyList(), null, needs)
        val n = needs.items.single()
        val w = s.waiting.single()
        assertEquals(n.key, w.need)
        assertEquals(AskKind.PERMISSION, w.ask.kind)
        assertEquals(n.ts, w.ask.since)
        assertEquals(listOf(Quick.Deny, Quick.Allow), Waits.quick(w))
        assertEquals(Answers.need(n.key, Answers.ALLOW), Waits.answer(w, Quick.Allow))
        assertEquals(Answers.need(n.key, Answers.DENY), Waits.answer(w, Quick.Deny))
        assertNull("an option is no answer to a permission", Waits.answer(w, Quick.Option("Evet")))
    }

    @Test
    fun aCardsOwnPermissionNeedJoinsItsRowOnceWithTheKey() {
        val card = asking.copy(asking = JsonObject(mapOf("kind" to JsonPrimitive("permission"), "detail" to JsonPrimitive("git push"), "waitingSince" to JsonPrimitive(7))))
        val mine = need("permission", card.sessionId, "permission|${card.sessionId}")
        val s = NowBuild.of("Ev PC", online, 5, listOf(card), null, NeedsList(listOf(mine), 1, 0))
        val w = s.waiting.single()
        assertEquals(card.id, w.session)
        assertEquals(mine.key, w.need)
        assertEquals(Answers.need(mine.key, Answers.ALLOW), Waits.answer(w, Quick.Allow))
    }

    @Test
    fun aPermissionWithoutTheFarmsKeyOpensInsteadOfAnsweringFromTheRow() {
        val card = asking.copy(asking = JsonObject(mapOf("kind" to JsonPrimitive("permission"), "detail" to JsonPrimitive("git push"), "waitingSince" to JsonPrimitive(7))))
        val w = NowBuild.of("Ev PC", online, 5, listOf(card), null, null).waiting.single()
        assertTrue(Waits.quick(w).isEmpty())
        assertNull(Waits.answer(w, Quick.Allow))
    }

    @Test
    fun aNeedOfACardThatDoesNotAskStaysItsOwnRow() {
        val card = working("c_a", "s-a")
        val other = need("permission", "s-a", "permission|s-a")
        val s = NowBuild.of("Ev PC", online, 5, listOf(card), null, NeedsList(listOf(other), 1, 0))
        assertEquals(1, s.running.size)
        assertEquals("s-a", s.waiting.single().session)
    }

    @Test
    fun anApprovalOrAConsoleQuestionWithoutItsCardOpensOnly() {
        val s = NowBuild.of("Ev PC", online, 5, emptyList(), null, NeedsList(listOf(need("browser-approval", null, "ba|1"), need("console-question", null, "cq|2")), 2, 0))
        assertEquals(listOf(AskKind.APPROVAL, AskKind.QUESTION), s.waiting.map { it.ask.kind })
        assertEquals(listOf("ba|1", "cq|2"), s.waiting.map { it.session })
        assertTrue(s.waiting.all { Waits.quick(it).isEmpty() })
    }

    @Test
    fun theContractsPageIsTheHistoryAndItsCursor() {
        val s = NowBuild.of("Ev PC", online, 5, emptyList(), page, null)
        val r = page.rows.first()
        assertEquals(Recent(r.id, r.agent, r.title, r.lastLine.orEmpty(), r.lastTs), s.recent.first())
        assertEquals(page.next, s.cursor)
        assertEquals(page.next != null, s.moreRecent)
    }

    @Test
    fun theNextPageFollowsAndNoRowIsDrawnTwice() {
        val first = NowBuild.of("Ev PC", online, 5, emptyList(), RecentPage(listOf(row("a", 50), row("b", 40)), "40:b"), null)
        val next = NowBuild.more(first, RecentPage(listOf(row("b", 40), row("c", 30)), null))
        assertEquals(listOf("a", "b", "c"), next.recent.map { it.session })
        assertNull(next.cursor)
        assertFalse(next.moreRecent)
    }

    @Test
    fun theHeadReadAgainTakesTheTopAndKeepsWhatWasScrolledTo() {
        val read = NowBuild.more(
            NowBuild.of("Ev PC", online, 5, emptyList(), RecentPage(listOf(row("a", 50), row("b", 40)), "40:b"), null),
            RecentPage(listOf(row("c", 30), row("d", 20)), "20:d"),
        )
        val again = NowBuild.head(read, RecentPage(listOf(row("c", 60), row("a", 50)), "50:a"))
        assertEquals("c moved to the top, b and d stay below", listOf("c", "a", "b", "d"), again.recent.map { it.session })
        assertEquals("the cursor of what was scrolled to is kept", "20:d", again.cursor)
        val gap = NowBuild.head(read, RecentPage(listOf(row("n", 90), row("m", 80)), "80:m"))
        assertEquals("more new rows than a page: the list starts again", listOf("n", "m"), gap.recent.map { it.session })
        assertEquals("80:m", gap.cursor)
        val whole = NowBuild.head(read, RecentPage(listOf(row("c", 60), row("a", 50)), null))
        assertEquals("the whole history in one page: nothing older is kept", listOf("c", "a"), whole.recent.map { it.session })
        assertFalse(whole.moreRecent)
        val short = NowBuild.head(NowBuild.of("Ev PC", online, 5, emptyList(), RecentPage(listOf(row("a", 50)), null), null), RecentPage(listOf(row("n", 70), row("a", 50)), "50:a"))
        assertEquals(listOf("n", "a"), short.recent.map { it.session })
        assertEquals("nothing below: the page's own cursor", "50:a", short.cursor)
        assertTrue(NowBuild.head(read, RecentPage(emptyList(), null)).recent.isEmpty())
    }

    @Test
    fun aFreshAnswerKeepsWhatTheLiveChannelToldOfACardStillRunning() {
        val told = NowBuild.of("Ev PC", online, 5, listOf(working("c_a", "s-a")), null, null).let {
            it.copy(running = it.running.map { r -> r.copy(step = Step("Bash", "npm test", 900), turnSince = 800) })
        }
        val fresh = NowBuild.of("Ev PC", online, 9, listOf(working("c_a", "s-a").copy(cost = 2.0), working("c_b", "s-b")), null, null)
        val both = NowBuild.carry(told, fresh)
        assertEquals(Running("c_a", asking.agent, "", Step("Bash", "npm test", 900), 800, 2.0, "s-a"), both.running.first())
        assertNull("a card the beats never told stays unknown", both.running.last().turnSince)
        assertEquals(9L, both.updatedAt)
    }

    @Test
    fun aTurnEndsWhenARunningCardStopsWorking() {
        val s = NowBuild.of("Ev PC", online, 5, listOf(working("c_a", "s-a")), null, null)
        assertTrue(NowBuild.turnEnded(s, "c_a", working = false))
        assertFalse(NowBuild.turnEnded(s, "c_a", working = true))
        assertFalse(NowBuild.turnEnded(s, "c_b", working = false))
    }

    @Test
    fun aBeatOfACardTakesItsTitleFromItsSessionAndKeepsItsKey() {
        val s = NowBuild.of("Ev PC", online, 5, emptyList(), RecentPage(listOf(row("s-a", 50, "Testler")), null), null).copy(cards = mapOf("c_a" to "s-a"))
        val beat = LiveCard("developer", "working", 1000, 0.5, true, Step("Bash", "npm test", 1200), null, null)
        val run = LiveMerge.now(s, "c_a", beat, 2000).running.single()
        assertEquals(Running("c_a", "developer", "Testler", Step("Bash", "npm test", 1200), 1000, 0.5, "s-a"), run)
        val waits = NowBuild.of("Ev PC", online, 5, emptyList(), null, needs)
        val w = waits.waiting.single()
        val asked = LiveMerge.now(waits, w.session, beat.copy(asking = w.ask), 2000).waiting.single()
        assertEquals("a beat keeps the farm's key", w.need, asked.need)
    }

    @Test
    fun aRowOpensItsSessionAndANeedWithoutOneOpensTheNeedsPage() {
        val s = NowBuild.of(
            "Ev PC", online, 5, listOf(asking, working("c_a", "s-a")), RecentPage(listOf(row("s-old", 10)), null),
            NeedsList(listOf(needs.items.single(), need("browser-approval", null, "ba|1")), 2, 0),
        )
        fun opened(id: String) = (NowOpen.of(s, id) as NowTarget.Conversation).key.let { it.card to it.transcript }
        assertEquals("a card that asks: its card and its transcript", asking.id to asking.sessionId, opened(asking.id))
        assertEquals("c_a" to "s-a", opened("c_a"))
        assertEquals("a session no card hosts is read only", null to "s-old", opened("s-old"))
        assertEquals("a session a card hosts writes through the card", "c_a" to "s-a", opened("s-a"))
        val elsewhere = (needs.items.single().ref.getValue("sessionId") as JsonPrimitive).content
        assertEquals(null to elsewhere, opened(elsewhere))
        assertEquals(NowTarget.Page(ShellRequest.Open(NowOpen.NEEDS_PAGE)), NowOpen.of(s, "ba|1"))
        val running = s.copy(running = s.running + Running("c_new", "developer", "", null, null, null))
        assertEquals("a card the engine has not named yet", "c_new" to null, (NowOpen.of(running, "c_new") as NowTarget.Conversation).key.let { it.card to it.transcript })
    }

    @Test
    fun aPagesLinkToNowOrASessionOpensThePhonesOwnScreen() {
        val s = NowBuild.of("Ev PC", online, 5, listOf(working("c_a", "s-a")), RecentPage(listOf(row("s-old", 10, "Eski iş")), null), null)
        fun open(page: String, vararg args: Pair<String, Any>) = NowOpen.link(
            s,
            ShellRequest.Open(page, if (args.isEmpty()) null else JsonObject(args.associate { (k, v) -> k to if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString()) })),
        )
        assertEquals(PageLink.Now, open(PageRoute.HOME_PAGE))
        assertEquals("the plain Console's cards are Now's rows", PageLink.Now, open(NowOpen.CONSOLE_PAGE))
        assertEquals("a session a card hosts writes through the card", "c_a", (open(NowOpen.SESSION_PAGE, "sessionId" to "s-a") as PageLink.Conversation).key.card)
        assertEquals(ConversationKey(null, "s-old", "developer", "Eski iş"), (open(NowOpen.SESSION_PAGE, "sessionId" to "s-old") as PageLink.Conversation).key)
        val talk = open(NowOpen.CONSOLE_PAGE, "agentId" to "psikolog")
        assertEquals("the Console on one agent stays the page", PageLink.Page(ShellRequest.Open(NowOpen.CONSOLE_PAGE, JsonObject(mapOf("agentId" to JsonPrimitive("psikolog"))))), talk)
        assertTrue("a link into one step only the page places", open(NowOpen.SESSION_PAGE, "sessionId" to "s-old", "ts" to 1792000000000L) is PageLink.Page)
        assertTrue("a session named by its file only", open(NowOpen.SESSION_PAGE, "file" to "x.jsonl") is PageLink.Page)
        assertTrue(open("tasks") is PageLink.Page)
    }

    @Test
    fun aFailedReadSaysTheFarmsOwnSentenceFirst() {
        val ok = CallResult("a", true, JsonPrimitive(1))
        val refused = CallResult("b", false, error = "Unknown op: sessions.recent")
        assertEquals("Unknown op: sessions.recent", NowText.notRead(ok, refused, null, online = true))
        assertEquals(NowText.NO_ANSWER, NowText.notRead(ok, null, online = true))
        assertEquals(NowText.NOT_READ, NowText.notRead(ok, ok, online = true))
    }

    @Test
    fun aReadTheLinkCutSaysNothingOfItsOwn() {
        val ok = CallResult("a", true, JsonPrimitive(1))
        val refused = CallResult("b", false, error = "Unknown op: sessions.recent")
        assertEquals("the copy's age says the link is down", null, NowText.notRead(ok, null, online = false))
        assertEquals("the farm's own sentence still stands", "Unknown op: sessions.recent", NowText.notRead(refused, null, online = false))
        assertEquals(null, ConversationText.notRead(null, online = false))
        assertEquals(ConversationText.NO_ANSWER, ConversationText.notRead(null, online = true))
        assertEquals("Unknown op: sessions.read", ConversationText.notRead(CallResult("c", false, error = "Unknown op: sessions.read"), online = false))
    }

    @Test
    fun theCopyIsTheFarmsAnswersDrawnByTheSameBuild() {
        val copy = NowCopy(42, listOf(asking, working("c_a", "s-a")), page, needs)
        val back = NowCopy.decode(copy.encode())
        assertNotNull(back)
        assertEquals(copy, back)
        val offline = Link.Offline("closed", 0)
        assertEquals(NowBuild.of("Ev PC", offline, 42, copy.cards, page, needs), back!!.state("Ev PC", offline))
        assertNull(NowCopy.decode(""))
        assertNull(NowCopy.decode("{\"updatedAt\":\"no\"}"))
    }
}
