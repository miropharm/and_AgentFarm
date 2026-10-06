package com.muvusoft.agentfarm.core.speech

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageReadingTest {
    private val token = JsonPrimitive("r7")
    private val ask = ReadingAsk(token, listOf("One.", "Two.", "Three."), "tr", 3)
    private val at = ReadingAt(token, 3, 1, paused = false, rate = 3)

    @Test
    fun aReadingStartsAtItsFirstParagraphAtItsOwnSpeed() {
        assertEquals(ReadingAt(token, 3, 0, paused = false, rate = 3), PageReading.start(ask))
        assertEquals(10, PageReading.start(ask.copy(rate = 40))?.rate)
        assertNull("nothing to say is no reading", PageReading.start(ask.copy(paragraphs = listOf(" ", ""))))
    }

    @Test
    fun theSpeedIsThePcsScaleOnThePhone() {
        assertEquals(1f, PageReading.speechRate(0), 0.0001f)
        assertEquals(2f, PageReading.speechRate(10), 0.0001f)
        assertEquals(0.5f, PageReading.speechRate(-10), 0.0001f)
        assertEquals("beyond the scale is its end", 2f, PageReading.speechRate(25), 0.0001f)
    }

    @Test
    fun stopEndsPauseAndResumeWaitAndGoOn() {
        assertEquals(ReadingMove.End, PageReading.move(at, ReadingControl("stop")))
        assertEquals(ReadingMove.Halt(at.copy(paused = true)), PageReading.move(at, ReadingControl("pause")))
        assertEquals(ReadingMove.Stay, PageReading.move(at.copy(paused = true), ReadingControl("pause")))
        assertEquals(ReadingMove.Play(at), PageReading.move(at.copy(paused = true), ReadingControl("resume")))
        assertEquals(ReadingMove.Stay, PageReading.move(at, ReadingControl("resume")))
        assertEquals(ReadingMove.Halt(at.copy(paused = true)), PageReading.move(at, ReadingControl("toggle")))
        assertEquals(ReadingMove.Play(at), PageReading.move(at.copy(paused = true), ReadingControl("toggle")))
    }

    @Test
    fun nextAndPreviousMoveByParagraphWithinTheReading() {
        assertEquals(ReadingMove.Play(at.copy(paragraph = 2)), PageReading.move(at, ReadingControl("next")))
        assertEquals("the last has nowhere to go", ReadingMove.Stay, PageReading.move(at.copy(paragraph = 2), ReadingControl("next")))
        assertEquals(ReadingMove.Play(at.copy(paragraph = 0)), PageReading.move(at, ReadingControl("prev")))
        assertEquals("the first starts again", ReadingMove.Play(at.copy(paragraph = 0)), PageReading.move(at.copy(paragraph = 0), ReadingControl("prev")))
        assertEquals("a skip while paused plays", ReadingMove.Play(at.copy(paragraph = 2)), PageReading.move(at.copy(paused = true), ReadingControl("next")))
    }

    @Test
    fun aNewSpeedRestartsTheParagraphOrWaitsWithIt() {
        assertEquals(ReadingMove.Play(at.copy(rate = -2)), PageReading.move(at, ReadingControl("rate", -2)))
        assertEquals(ReadingMove.Halt(at.copy(paused = true, rate = 10)), PageReading.move(at.copy(paused = true), ReadingControl("rate", 12)))
        assertEquals(ReadingMove.Stay, PageReading.move(at, ReadingControl("rate")))
    }

    @Test
    fun aLongParagraphIsCutWhereAVoiceCanTakeIt() {
        assertEquals(listOf("Short one."), PageReading.chunks("  Short one. ", 4000))
        assertEquals(listOf("First part.", "Second part goes on."), PageReading.chunks("First part. Second part goes on.", 25))
        assertEquals("a closing quote stays with its sentence", listOf("He said \"stop.\"", "Then left."), PageReading.chunks("He said \"stop.\" Then left.", 18))
        assertEquals(listOf("no sentence", "end here"), PageReading.chunks("no sentence end here", 12))
        val hard = PageReading.chunks("x".repeat(25), 10)
        assertEquals(listOf(10, 10, 5), hard.map { it.length })
        PageReading.chunks("Word ".repeat(3000), 4000).forEach { assertTrue(it.length <= 4000 && it.isNotBlank()) }
    }

    @Test
    fun thePageHearsWhereTheReadingIsUnderItsToken() {
        val s = PageReading.state(token, at)
        assertEquals("afSpeakState", s["type"]?.jsonPrimitive?.content)
        assertEquals(token, s["token"])
        assertEquals("reading", s["state"]?.jsonPrimitive?.content)
        assertEquals(1, s["paragraph"]?.jsonPrimitive?.int)
        assertEquals(3, s["count"]?.jsonPrimitive?.int)
        assertEquals(3, s["rate"]?.jsonPrimitive?.int)
        assertEquals("paused", PageReading.state(token, at.copy(paused = true))["state"]?.jsonPrimitive?.content)
        val over = PageReading.state(token, null, "why")
        assertEquals("idle", over["state"]?.jsonPrimitive?.content)
        assertFalse(over.containsKey("paragraph"))
        assertEquals("why", over["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun aPieceIdNamesItsRoundParagraphAndPieceAndNothingElseParses() {
        val p = ReadingPiece(4, 2, 1)
        assertEquals(p, ReadingPiece.of(p.id))
        listOf(null, "", "alert-17", "afread:", "afread:1:2", "afread:a:2:3", "afread:1:2:3:4").forEach {
            assertNull(it, ReadingPiece.of(it))
        }
    }

    @Test
    fun noVoiceNamesTheLanguageInEnglish() {
        assertTrue(PageReading.noVoice("tr").contains("Turkish"))
        assertTrue(PageReading.noVoice("").contains("this language"))
    }
}
