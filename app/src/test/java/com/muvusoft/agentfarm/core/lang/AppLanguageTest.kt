package com.muvusoft.agentfarm.core.lang

import com.muvusoft.agentfarm.core.lang.AppLanguage.Told
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppLanguageTest {
    @Test
    fun noFarmTellingOneLeavesThePhonesOwn() {
        assertNull(AppLanguage.of(emptyList()))
    }

    @Test
    fun aFarmsFirstTellingIsTaken() {
        val a = AppLanguage.tell(null, "tr", 10)
        assertEquals(Told("tr", 10), a)
        assertEquals("tr", AppLanguage.of(listOfNotNull(a)))
    }

    @Test
    fun aFarmRepeatingItselfMovesNothing() {
        val a = Told("en", 10)
        val b = Told("tr", 20)
        val again = AppLanguage.tell(a, "en", 30)
        assertSame(a, again)
        assertEquals("tr", AppLanguage.of(listOf(again!!, b)))
    }

    @Test
    fun aFarmTellingAnotherLanguageMovesTheApp() {
        val a = AppLanguage.tell(Told("en", 10), "tr", 40)
        assertEquals("tr", AppLanguage.of(listOf(a!!, Told("en", 20))))
    }

    @Test
    fun aWelcomeWithoutALanguageKeepsWhatTheFarmToldBefore() {
        val a = Told("tr", 10)
        assertSame(a, AppLanguage.tell(a, null, 50))
        assertSame(a, AppLanguage.tell(a, "  ", 50))
        assertSame(a, AppLanguage.tell(a, "@@", 50))
        assertNull(AppLanguage.tell(null, null, 50))
    }

    @Test
    fun aForgottenFarmsLanguageLeavesWithIt() {
        val left = listOf(Told("en", 10))
        assertEquals("en", AppLanguage.of(left))
    }

    @Test
    fun tagsAreCleanedToTheirStandardForm() {
        assertEquals("pt-BR", AppLanguage.clean(" pt-br "))
        assertNull(AppLanguage.clean("und"))
        assertNull(AppLanguage.clean(""))
    }

    private val supported = setOf("en", "tr")
    private fun phone(vararg tags: String) = tags.map { java.util.Locale.forLanguageTag(it) }

    @Test
    fun theToldLanguageWinsWhenTheAppHasWordsForIt() {
        assertEquals("tr", AppLanguage.effective("tr", phone("en-US"), supported).language)
        assertEquals("en", AppLanguage.effective("en", phone("tr-TR"), supported).language)
    }

    @Test
    fun aLanguageThePhoneAlsoListsKeepsItsRegionSoNothingIsRedrawn() {
        assertEquals(AppLanguage.effective(null, phone("en-US"), supported), AppLanguage.effective("en", phone("en-US"), supported))
        assertEquals("en-GB", AppLanguage.effective("en", phone("tr-TR", "en-GB"), supported).toLanguageTag())
    }

    @Test
    fun aPhoneListingEnglishBeforeTurkishReadsEnglishWhenNoFarmTells() {
        assertEquals("en-US", AppLanguage.effective(null, phone("en-US", "tr-TR"), supported).toLanguageTag())
        assertEquals("tr-TR", AppLanguage.effective(null, phone("de-DE", "tr-TR"), supported).toLanguageTag())
    }

    @Test
    fun aLanguageWithoutWordsFallsToThePhonesThenToEnglish() {
        assertEquals("tr-TR", AppLanguage.effective("de", phone("tr-TR"), supported).toLanguageTag())
        assertEquals("en", AppLanguage.effective("de", phone("fr-FR"), supported).language)
    }

    @Test
    fun aRecordSurvivesBeingKeptAndADamagedOneReadsAsNeverTold() {
        val t = Told("tr", 1792251673568)
        assertEquals(t, AppLanguage.read(AppLanguage.write(t)))
        assertNull(AppLanguage.read("tr"))
        assertNull(AppLanguage.read("tr|soon"))
        assertNull(AppLanguage.read("@@|5"))
        assertNull(AppLanguage.read(null))
    }
}
