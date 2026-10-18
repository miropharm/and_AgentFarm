package com.muvusoft.agentfarm

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muvusoft.agentfarm.core.contract.Pairing
import com.muvusoft.agentfarm.core.handsfree.VoiceAnswer
import com.muvusoft.agentfarm.core.pairing.PairingVerdict
import com.muvusoft.agentfarm.core.speech.Utterance
import com.muvusoft.agentfarm.core.speech.VoiceLanguage
import com.muvusoft.agentfarm.core.state.Link
import com.muvusoft.agentfarm.net.DeviceIdentity
import com.muvusoft.agentfarm.net.Ears
import com.muvusoft.agentfarm.net.LinkService
import com.muvusoft.agentfarm.net.PairClient
import com.muvusoft.agentfarm.net.phoneLocale
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * J11, "answer a question by voice", with the headset mode on and the phone in a pocket (no screen of the app on):
 * the question is read, a tone, the words heard and read back, and unless they are called off within 3 s the answer
 * goes to the question's card. The emulator has neither a headset nor speech: scripted ears and a mouth that only
 * writes down stand in for the recognizer and the voice (net/HandsFreeLoop's seams); the farm's log shows what went.
 */
@RunWith(AndroidJUnit4::class)
class JourneyHandsFreeTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = AgentFarmApp.of(ctx)
    private val loop = app.handsFree
    private val kept = Triple(loop.ready, loop.ears, loop.mouth)
    private val said = CopyOnWriteArrayList<String>()
    private val words = ConcurrentLinkedQueue<String>()
    private val tones = AtomicInteger()
    private var ready = true

    @Before
    fun seams() {
        loop.ready = { ready }
        loop.mouth = { u: Utterance -> said += u.text; true }
        loop.ears = object : Ears {
            override suspend fun tone() {
                tones.incrementAndGet()
            }

            // A scripted line is said a little after the tone; with none left the phone hears silence for the while.
            override suspend fun listen(lang: Locale, ms: Long): String? =
                words.poll()?.also { delay(SPEAKING_MS) } ?: run { delay(ms); null }
        }
    }

    @After
    fun restore() {
        loop.ready = kept.first
        loop.ears = kept.second
        loop.mouth = kept.third
    }

    private fun online() {
        val r = runBlocking { PairClient.pair(Pairing.parse(FakeHost.freshLink())!!, DeviceIdentity("Emulator", "android test", "test")) }
        val farm = (r as PairingVerdict.Result.Paired).farm
        app.store.save(farm)
        LinkService.sync(ctx, anyFarm = true)
        runBlocking { withTimeout(20_000) { app.manager.state.first { it.farm(farm.id)?.link is Link.Online } } }
    }

    private fun ask(id: String, question: String) {
        val data = "{\"session\":\"c_dev\",\"agent\":\"developer\",\"askId\":\"$id\",\"question\":\"$question\",\"options\":[{\"label\":\"master\"},{\"label\":\"New branch\"}]}"
        FakeHost.post("/_test/event", "{\"type\":\"ask.opened\",\"data\":$data}")
    }

    private fun answered(question: String) = FakeHost.get("/_test/state").let { it.contains("console.answer") && it.contains(question) }

    private fun until(ms: Long, what: String, cond: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + ms
        while (!cond()) {
            if (SystemClock.elapsedRealtime() > end) throw AssertionError("not within $ms ms: $what; said ${said.toList()}")
            Thread.sleep(50)
        }
    }

    private val lang get() = VoiceLanguage.locale(app.prefs.voiceLanguage, phoneLocale()).language

    @Test
    fun theWordsHeardAfterTheQuestionAnswerItUnlessCalledOff() {
        FakeHost.requireLink()
        online()
        val j = Journey("J11", "lan").begin()
        words += "Master"
        val question = "Which branch? (j11-answer)"
        j.step("the question read, a tone, the words read back", taps = 0, targetMs = 3_000) {
            ask("a_j11_answer", question)
            until(10_000, "the read-back") { said.any { it == VoiceAnswer.readBack(lang, "master") } }
        }
        j.step("not called off: the answer goes to its card", taps = 0, targetMs = VoiceAnswer.CANCEL_MS + 1_000) {
            until(10_000, "console.answer on the farm") { answered(question) }
        }
        j.check("the question was read first, then one tone", said.firstOrNull()?.contains("Which branch?") == true && tones.get() == 1, "said ${said.toList()} · tones ${tones.get()}")
        j.check("the option's own label went", FakeHost.get("/_test/state").contains("\"$question\":\"master\""), "answers {question: master}")
        j.end()
    }

    @Test
    fun calledOffWithinTheWhileNothingGoes() {
        FakeHost.requireLink()
        online()
        val j = Journey("J11", "lan").begin()
        words += listOf("master", "Vazgeç")
        val question = "Which branch? (j11-cancel)"
        j.step("called off: nothing goes and the phone says so", taps = 0, targetMs = 3_000) {
            ask("a_j11_cancel", question)
            until(10_000, "the call-off") { said.lastOrNull() == VoiceAnswer.cancelled(lang) }
        }
        Thread.sleep(VoiceAnswer.CANCEL_MS + 1_000)
        j.check("the farm got no answer", !answered(question), "no console.answer for it")
        j.end()
    }

    @Test
    fun silenceSendsNothingAndAModeNotReadyDoesNotListen() {
        FakeHost.requireLink()
        online()
        val j = Journey("J11", "lan").begin()
        val quiet = "Which branch? (j11-silence)"
        ask("a_j11_silence", quiet)
        until(10_000, "the question read") { said.any { it.contains(quiet) } }
        Thread.sleep(VoiceAnswer.LISTEN_MS + 1_000)
        j.check("silence: nothing goes, nothing more is said", !answered(quiet) && said.size == 1, "said ${said.toList()}")
        ready = false
        said.clear()
        val plain = "Which branch? (j11-not-ready)"
        ask("a_j11_not_ready", plain)
        Thread.sleep(3_000)
        j.check("the mode not ready: the loop does not take the question", said.isEmpty() && tones.get() == 1, "said ${said.toList()} · tones ${tones.get()}")
        j.end()
    }

    private companion object {
        const val SPEAKING_MS = 300L
    }
}
