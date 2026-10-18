package com.muvusoft.agentfarm.core.handsfree

import com.muvusoft.agentfarm.core.session.Answers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAnswerTest {
    private val branches = listOf("master", "Yeni dal")
    private val yesNo = listOf("Yes, proceed (Recommended)", "No, stop here")

    @Test
    fun wordsAreComparedWithoutCaseAccentsOrPunctuation() {
        assertEquals("yeni dal", VoiceAnswer.fold("  Yeni   dal. "))
        assertEquals("vazgec simdi", VoiceAnswer.fold("Vazgeç, ŞİMDİ!"))
        assertEquals("ilk", VoiceAnswer.fold("İLK"))
        assertEquals("ucuncu", VoiceAnswer.fold("üçüncü"))
    }

    @Test
    fun theWordsNameAnOptionAsWrittenCarriedBegunOrByItsPlace() {
        assertEquals("master", VoiceAnswer.option("Master", branches))
        assertEquals("Yeni dal", VoiceAnswer.option("yeni dal.", branches))
        assertEquals("master", VoiceAnswer.option("master olsun", branches))
        assertEquals("Yes, proceed (Recommended)", VoiceAnswer.option("yes", yesNo))
        assertEquals("No, stop here", VoiceAnswer.option("ikincisi", yesNo))
        assertEquals("master", VoiceAnswer.option("the first one", branches))
        assertNull(VoiceAnswer.option("başka bir dal aç", branches))
        assertNull("two options carried name none", VoiceAnswer.option("master ya da yeni dal", branches))
        assertNull(VoiceAnswer.option("", branches))
    }

    @Test
    fun anOptionGoesAsItsLabelOtherWordsAsTheAnswer() {
        assertEquals(Heard(Answers.question("c_dev", "Hangi dal?", "master"), "master"), VoiceAnswer.heard("c_dev", "Hangi dal?", branches, " Master "))
        assertEquals(Heard(Answers.free("c_dev", "release dalını kullan"), "release dalını kullan"), VoiceAnswer.heard("c_dev", "Hangi dal?", branches, "release dalını kullan"))
        assertEquals(Heard(Answers.free("c_dev", "evet"), "evet"), VoiceAnswer.heard("c_dev", "Devam?", emptyList(), "evet"))
        assertNull(VoiceAnswer.heard("c_dev", "Hangi dal?", branches, "   "))
    }

    @Test
    fun aShortUtteranceWithACancelWordCallsItOff() {
        assertTrue(VoiceAnswer.cancels("Vazgeç"))
        assertTrue(VoiceAnswer.cancels("iptal et"))
        assertTrue(VoiceAnswer.cancels("never mind"))
        assertTrue(VoiceAnswer.cancels("Cancel that"))
        assertFalse(VoiceAnswer.cancels("master"))
        assertFalse("a long answer that mentions stopping is an answer", VoiceAnswer.cancels("stop the build and run the tests again"))
        assertFalse(VoiceAnswer.cancels(""))
    }

    @Test
    fun theSentencesAreInTheVoicesLanguage() {
        assertEquals("master. Sending.", VoiceAnswer.readBack("en", "master"))
        assertEquals("master. Gönderiyorum.", VoiceAnswer.readBack("tr", "master"))
        assertEquals("Vazgeçildi.", VoiceAnswer.cancelled("tr"))
        assertEquals("No link: it waits on this phone.", VoiceAnswer.waits("de"))
    }
}
