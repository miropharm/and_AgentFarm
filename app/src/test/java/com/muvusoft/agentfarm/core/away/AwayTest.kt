package com.muvusoft.agentfarm.core.away

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.link.LinkText
import com.muvusoft.agentfarm.core.notify.StatusLine
import com.muvusoft.agentfarm.core.state.FarmStatus
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.core.state.PairedFarm
import com.muvusoft.agentfarm.core.state.ShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwayTest {
    private val now = 1_800_000_000_000L
    private fun json(s: String) = Codec.json.parseToJsonElement(s)

    private val answer = json(
        """{"away":true,"farm":"Masaüstü","sentence":"Agent Farm is not running: VS Code is closed on Masaüstü.",
        "since":${now - 30 * 60_000},"snapshot":{"v":2,"ts":${now - 12 * 60_000},"writer":"service",
        "totals":{"needs":2,"unreadNotices":4,"runningTasks":1},
        "needs":[{"kind":"ask","agent":"developer","title":"Testleri çalıştırayım mı?"},{"kind":"x","agent":"","title":"Yedek"},
        {"agent":"a","title":""}]},"age":{"seconds":720,"stale":true,"note":"VS Code is closed."}}""",
    )

    @Test
    fun theServicesAnswerIsReadIntoAnAway() {
        val a = AwayVerdict.of(answer)!!
        assertEquals("Agent Farm is not running: VS Code is closed on Masaüstü.", a.sentence)
        assertEquals(now - 12 * 60_000, a.snapshotAt)
        assertEquals(2L, a.needs)
        assertEquals(listOf("developer · Testleri çalıştırayım mı?", "Yedek"), a.top.map(AwayVerdict::row))
        assertEquals(
            listOf(
                "VS Code was last seen 30 min ago.",
                "The numbers below are from 12 min ago; nothing behind them moves until VS Code opens.",
                "2 waiting for you · 4 unread notices · 1 running task",
            ),
            AwayVerdict.lines(a, now),
        )
        assertEquals("VS Code is closed · last state 12 min ago", AwayVerdict.short(a, now))
    }

    @Test
    fun aRunningFarmOrAnUnreadableAnswerIsNotAway() {
        assertNull(AwayVerdict.of(json("""{"away":false,"farm":"x"}""")))
        assertNull(AwayVerdict.of(json("""{"away":"true"}""")))
        assertNull(AwayVerdict.of(json("[1]")))
        assertNull(AwayVerdict.of(null))
    }

    @Test
    fun withoutASnapshotTheWaitSaysSoAndTheSentenceIsNeverBlank() {
        val a = AwayVerdict.of(json("""{"away":true,"farm":"Ev","snapshot":null}"""))!!
        assertEquals("Agent Farm is not running: VS Code is closed on Ev.", a.sentence)
        assertEquals(listOf("There is no saved state to show."), AwayVerdict.lines(a, now))
        assertEquals("VS Code is closed", AwayVerdict.short(a, now))
    }

    @Test
    fun farmOpensAnswerIsOneSentence() {
        assertTrue(AwayVerdict.opened(true, json("""{"opening":true}"""), null, "Ev", now).startsWith("Starting VS Code on Ev"))
        assertTrue(AwayVerdict.opened(true, json("""{"opening":true,"again":true,"since":${now - 60_000}}"""), null, "Ev", now).contains("asked 1 min ago"))
        assertEquals("Agent Farm is already running on Ev.", AwayVerdict.opened(true, json("""{"already":true}"""), null, "Ev", now))
        assertEquals("VS Code cannot be started from here", AwayVerdict.opened(false, null, "VS Code cannot be started from here", "Ev", now))
    }

    @Test
    fun theStripAndTheStatusLineSayVsCodeIsClosed() {
        val a = AwayVerdict.of(answer)!!
        val online = Link.Online("10.0.0.2:7443", "manage", emptyMap(), now)
        assertEquals("VS Code is closed · last state 12 min ago", LinkText.of(online, now, a).short)
        assertTrue(LinkText.of(online, now, a).long.startsWith(a.sentence))
        assertEquals("Connected · 10.0.0.2:7443", LinkText.of(online, now).short)
        val farm = PairedFarm("f1", "Masaüstü", "d", "manage", "fp", listOf("10.0.0.2:7443"), "k")
        val one = ShellState(listOf(FarmStatus(farm, online, away = a)))
        assertEquals("VS Code closed · Masaüstü", StatusLine.of(one, now).title)
        val two = ShellState(listOf(FarmStatus(farm, online, away = a), FarmStatus(farm.copy(id = "f2"), online)))
        assertEquals("2 farms connected · 1 with VS Code closed", StatusLine.of(two, now).title)
    }
}
