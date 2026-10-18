package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.contract.Codec
import com.muvusoft.agentfarm.core.conversation.ConversationKey
import com.muvusoft.agentfarm.core.session.Ask
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.state.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewSessionTest {
    private val now = NowState(
        farm = "Ev PC",
        link = Link.Online("192.168.1.20:7300", "manage", emptyMap(), 0),
        updatedAt = 5,
        waiting = listOf(Waiting("c_w", "psikolog", "Soru", Ask(kind = AskKind.QUESTION, detail = "Hangisi?", since = 1))),
        running = listOf(Running("c_r", "developer", "Testler", null, null, null)),
        recent = listOf(Recent("s1", "asistan", "A", "", 50), Recent("s2", "developer", "B", "", 40), Recent("s3", "eski", "C", "", 30)),
    )

    @Test
    fun theAgentsUsedLastComeFirstThenTheRestInTheFarmsOrder() {
        val farm = listOf("asistan", "basit", "developer", "manager", "psikolog")
        assertEquals(listOf("psikolog", "developer", "asistan", "basit", "manager"), NewSession.agents(farm, now))
    }

    @Test
    fun anAgentTheFarmNoLongerListsIsNeverOffered() {
        assertEquals(listOf("developer", "basit"), NewSession.agents(listOf("basit", "developer"), now))
        assertEquals(emptyList<String>(), NewSession.agents(emptyList(), now))
    }

    @Test
    fun withNothingUsedTheFarmsOrderStands() {
        val farm = listOf("b", "a")
        assertEquals(farm, NewSession.agents(farm, NowState("Ev PC", Link.Offline("unpaired", 0), 0)))
    }

    @Test
    fun theOpenedSessionIsItsCardsConversationUnderTheAgentTheFarmNamed() {
        val answer = Codec.json.parseToJsonElement("""{"id":"cs7-mvd8","agent":"developer","note":"▶ developer — a Console session is open"}""")
        assertEquals(ConversationKey("cs7-mvd8", null, "developer", ""), NewSession.opened("Developer", answer))
    }

    @Test
    fun aFarmThatNamesNoAgentLeavesThePickedOne() {
        assertEquals(ConversationKey("c_new3", null, "developer", ""), NewSession.opened("developer", Codec.json.parseToJsonElement("""{"id":"c_new3"}""")))
    }

    @Test
    fun anAnswerWithoutACardOpensNothing() {
        assertNull(NewSession.opened("developer", null))
        assertNull(NewSession.opened("developer", Codec.json.parseToJsonElement("""{"agents":["developer"]}""")))
        assertNull(NewSession.opened("developer", Codec.json.parseToJsonElement("""{"id":"","agent":"developer"}""")))
        assertNull(NewSession.opened("developer", Codec.json.parseToJsonElement("""["c_new3"]""")))
    }
}
