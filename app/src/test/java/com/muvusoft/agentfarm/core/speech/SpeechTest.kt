package com.muvusoft.agentfarm.core.speech

import com.muvusoft.agentfarm.core.contract.AskOpened
import com.muvusoft.agentfarm.core.contract.AskOption
import com.muvusoft.agentfarm.core.contract.NoticePosted
import com.muvusoft.agentfarm.core.contract.SessionEnded
import com.muvusoft.agentfarm.core.contract.Spoken
import com.muvusoft.agentfarm.core.contract.TurnFinished
import com.muvusoft.agentfarm.core.notify.Alerts
import java.util.Locale
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
        assertEquals("developer is asking, Ev. Testleri şimdi çalıştırayım mı?", u.text)
        assertEquals("ask:q1", u.id)
        assertTrue(u.interrupt)
        assertEquals(u, Speech.of(ask, SpeakMode.ASKS_AND_TURNS))
    }

    @Test
    fun aTurnSummaryIsReadOnlyWhenAskedForAndWaitsItsTurn() {
        assertNull(Speech.of(turn, SpeakMode.ASKS))
        val u = Speech.of(turn, SpeakMode.ASKS_AND_TURNS)!!
        assertEquals("developer: turn finished, Ev. Paket yeşil, commit atıldı", u.text)
        assertFalse(u.interrupt)
        assertEquals("developer: turn finished, Ev. Tüm testler geçti, link açıldı",
            Speech.of(turn, SpeakMode.ASKS_AND_TURNS, summary = "Tüm testler **geçti**, https://ci/run açıldı")!!.text)
    }

    @Test
    fun noticesAreNeverReadAloud() {
        val notice = Alerts.of("Ev", NoticePosted("n1", "Rapor hazır", 2, "developer"))!!
        assertNull(Speech.of(notice, SpeakMode.ASKS_AND_TURNS))
        for (level in 1L..4L) assertNull(Speech.of(Alerts.of("Ev", NoticePosted("n$level", "t", level, "a"))!!, SpeakMode.ASKS_AND_TURNS))
    }

    @Test
    fun theFarmsSpokenTextIsReadAsWrittenInItsLanguage() {
        val spoken = Spoken("developer soruyor: Testleri şimdi çalıştırayım mı? Seçenekler: Evet.", "tr")
        val u = Speech.of(ask, SpeakMode.ASKS, spoken = spoken)!!
        assertEquals(spoken.text, u.text)
        assertEquals("tr", u.lang)
        assertTrue(u.interrupt)
        val long = "x ".repeat(Speech.MAX_CHARS)
        assertEquals(long.trim(), Speech.of(turn, SpeakMode.ASKS_AND_TURNS, summary = "ignored", spoken = Spoken(long, "en"))!!.text)
        assertNull(Speech.of(turn, SpeakMode.ASKS, spoken = Spoken("Paket yeşil.", "tr")))
    }

    @Test
    fun withoutTheFarmsTextThePhoneReadsItsOwnInTheUsersLanguage() {
        assertEquals("", Speech.of(ask, SpeakMode.ASKS)!!.lang)
        assertEquals("", Speech.of(ask, SpeakMode.ASKS, spoken = Spoken("  ", "tr"))!!.lang)
        val e = AskOpened("s1", "developer", "q1", "Hangisi?", emptyList(), spoken = Spoken("Which one?", "en"))
        assertEquals(Spoken("Which one?", "en"), Speech.spokenOf(e))
        assertNull(Speech.spokenOf(SessionEnded("s1", "developer")))
    }

    @Test
    fun aTextIsReadInItsOwnLanguageElseInTheUsers() {
        val user = Locale.forLanguageTag("tr-TR")
        assertEquals(listOf(Locale.forLanguageTag("de"), user), Speech.voices("de", user))
        assertEquals(listOf(user), Speech.voices("", user))
        assertEquals(listOf(user), Speech.voices("tr-TR", user))
        // a phone with no German voice falls to the user's; with neither, nothing is read
        val has = setOf(user)
        assertEquals(user, Speech.voices("de", user).firstOrNull { it in has })
        assertNull(Speech.voices("de", Locale.forLanguageTag("fr")).firstOrNull { it in has })
    }

    @Test
    fun anAlertTheUserSilencedOnTheFarmIsNotReadAloud() {
        assertNull(Speech.of(ask.copy(silent = true), SpeakMode.ASKS_AND_TURNS))
        assertNull(Speech.of(turn.copy(silent = true), SpeakMode.ASKS_AND_TURNS))
    }

    @Test
    fun markupAndLinksAreNotSpelledOut() {
        assertEquals("Bak: link ve node test/run-all.js code block tamam",
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
