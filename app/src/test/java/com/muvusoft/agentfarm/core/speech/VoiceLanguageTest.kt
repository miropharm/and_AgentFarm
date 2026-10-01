package com.muvusoft.agentfarm.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class VoiceLanguageTest {
    private val brazil = Locale.forLanguageTag("pt-BR")

    @Test
    fun nothingChosenFollowsThePhone() {
        assertEquals("pt-BR", VoiceLanguage.tag(VoiceLanguage.PHONE, brazil))
        assertEquals("pt-BR", VoiceLanguage.tag(null, brazil))
        assertEquals("pt-BR", VoiceLanguage.tag("   ", brazil))
    }

    @Test
    fun aChoiceWinsOverThePhone() {
        assertEquals("hi-IN", VoiceLanguage.tag("hi-IN", brazil))
        assertEquals(Locale.forLanguageTag("hi-IN"), VoiceLanguage.locale("hi-IN", brazil))
    }

    @Test
    fun aStoredValueThatNamesNoLanguageFallsBackToThePhone() {
        assertEquals("pt-BR", VoiceLanguage.tag("@@", brazil))
    }

    @Test
    fun thePickerListsThePhoneFirstAndKeepsAnUnofferedChoice() {
        val plain = VoiceLanguage.choices(VoiceLanguage.PHONE)
        assertEquals(VoiceLanguage.PHONE, plain.first())
        assertEquals(plain.size, plain.toSet().size)
        assertEquals(VoiceLanguage.OFFERED.size + 1, plain.size)
        val kept = VoiceLanguage.choices("sw-KE")
        assertEquals("sw-KE", kept.last())
        assertEquals(plain.size + 1, kept.size)
    }

    @Test
    fun aLanguageIsNamedInItsOwnWords() {
        assertEquals("Deutsch (Deutschland)", VoiceLanguage.name("de-DE"))
        assertTrue(VoiceLanguage.name("en-US").startsWith("English"))
    }
}
