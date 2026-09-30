package com.muvusoft.agentfarm.core.speech

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.notify.Alerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {
    private val ask = Alerts.of("Ev", AskOpened("s1", "developer", "q1", "Testleri şimdi çalıştırayım mı?", listOf(AskOption("Evet"))))!!
    private val turn = Alerts.of("Ev", TurnFinished("s1", "developer", "Paket yeşil, commit atıldı", ""))!!

    @Test
    fun aQuestionIsReadInEveryModeButOffAndCutsInFront() {
        assertNull(Speech.of(ask, SpeakMode.OFF))
        val u = Speech.of(ask, SpeakMode.ASKS)!!
        assertEquals("developer soruyor, Ev. Testleri şimdi çalıştırayım mı?", u.text)
        assertEquals("ask:q1", u.id)
        assertTrue(u.interrupt)
        assertEquals(u, Speech.of(ask, SpeakMode.ASKS_AND_TURNS))
    }

    @Test
    fun aTurnSummaryIsReadOnlyWhenAskedForAndWaitsItsTurn() {
        assertNull(Speech.of(turn, SpeakMode.ASKS))
        val u = Speech.of(turn, SpeakMode.ASKS_AND_TURNS)!!
        assertEquals("developer: tur bitti, Ev. Paket yeşil, commit atıldı", u.text)
        assertFalse(u.interrupt)
        assertEquals("developer: tur bitti, Ev. Tüm testler geçti, bağlantı açıldı",
            Speech.of(turn, SpeakMode.ASKS_AND_TURNS, summary = "Tüm testler **geçti**, https://ci/run açıldı")!!.text)
    }

    @Test
    fun noticesAreNeverReadAloud() {
        val notice = Alerts.of("Ev", NoticePosted("n1", "Rapor hazır", 2, "developer"))!!
        assertNull(Speech.of(notice, SpeakMode.ASKS_AND_TURNS))
    }

    @Test
    fun markupAndLinksAreNotSpelledOut() {
        assertEquals("Bak: bağlantı ve node test/run-all.js kod bloğu tamam",
            Speech.spoken("Bak: https://x.y/z?a=1 ve `node test/run-all.js` ```\nrm -rf\n``` **tamam**"))
    }

    @Test
    fun aLongTextIsCutAtAWord() {
        val long = List(100) { "kelime" }.joinToString(" ")
        val c = Speech.cut(long)
        assertTrue(c.length <= Speech.MAX_CHARS + 1)
        assertTrue(c.endsWith("kelime…"))
        assertEquals("kısa", Speech.cut("kısa"))
    }

    @Test
    fun anUnknownStoredModeIsSilent() {
        assertEquals(SpeakMode.OFF, SpeakMode.of(null))
        assertEquals(SpeakMode.OFF, SpeakMode.of("loud"))
        assertEquals(SpeakMode.ASKS_AND_TURNS, SpeakMode.of("asksAndTurns"))
    }

    @Test
    fun aReplayedAlertIsReadOnce() {
        val log = SpokenLog(cap = 2)
        assertTrue(log.first("ask:q1"))
        assertFalse(log.first("ask:q1"))
        assertTrue(log.first("ask:q2"))
        assertTrue(log.first("ask:q3"))
        // The oldest fell out of the window.
        assertTrue(log.first("ask:q1"))
    }
}
