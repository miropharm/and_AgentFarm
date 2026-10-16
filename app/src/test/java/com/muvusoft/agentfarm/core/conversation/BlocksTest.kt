package com.muvusoft.agentfarm.core.conversation

import com.muvusoft.agentfarm.core.state.Link
import org.junit.Assert.assertEquals
import org.junit.Test

class BlocksTest {
    private var n = 0
    private fun m(role: Role, text: String? = null, tool: String? = null, error: Boolean = false) =
        Message(i = ++n, turn = 1, role = role, ts = 0L, text = text, tool = tool, isError = error)

    @Test
    fun theFiveWireRolesAndTheConsoleSnapshotsOwnWords() {
        assertEquals(
            listOf(Role.USER, Role.ASSISTANT, Role.ASSISTANT, Role.TOOL, Role.TOOL_RESULT, Role.TOOL_RESULT, Role.SYSTEM, Role.SYSTEM),
            listOf("user", "assistant", "agent", "tool", "toolResult", "result", "system", "bilinmeyen").map(Role::of),
        )
    }

    @Test
    fun toolStepsInARowFoldIntoOneBlock() {
        val b = Blocks.of(listOf(
            m(Role.USER, "testleri çalıştır"), m(Role.ASSISTANT, "Bakıyorum."),
            m(Role.TOOL, tool = "Read"), m(Role.TOOL_RESULT), m(Role.TOOL, tool = "Bash npm test"), m(Role.TOOL_RESULT, error = true),
            m(Role.ASSISTANT, "Bitti."), m(Role.SYSTEM, "compacted"),
        ))
        assertEquals(listOf("User", "Answer", "Steps", "Answer", "Note"), b.map { it::class.simpleName })
        val steps = b[2] as Block.Steps
        assertEquals(4, steps.steps.size)
        assertEquals(3, steps.first)
        assertEquals("2 steps · Read, Bash · 1 failed", Blocks.summary(steps))
    }

    @Test
    fun theSummaryNamesThreeToolsThenCountsTheRest() {
        val steps = Block.Steps(listOf("Read", "Bash", "Edit", "Grep", "Write", "Read").map { m(Role.TOOL, tool = it) })
        assertEquals("6 steps · Read, Bash, Edit +2", Blocks.summary(steps))
        assertEquals("1 step", Blocks.summary(Block.Steps(listOf(m(Role.TOOL_RESULT)))))
    }

    @Test
    fun itOpensAtTheStartOfTheLastAnswer() {
        val done = Blocks.of(listOf(m(Role.USER, "a"), m(Role.ASSISTANT, "b"), m(Role.USER, "c"), m(Role.TOOL, tool = "Read"), m(Role.ASSISTANT, "uzun cevap")))
        assertEquals(4, Blocks.openAt(done))
        // the turn has not answered yet: what was asked, with the steps below it
        val running = Blocks.of(listOf(m(Role.USER, "a"), m(Role.ASSISTANT, "b"), m(Role.USER, "c"), m(Role.TOOL, tool = "Bash")))
        assertEquals(2, Blocks.openAt(running))
        assertEquals(0, Blocks.openAt(emptyList()))
        assertEquals(0, Blocks.openAt(Blocks.of(listOf(m(Role.TOOL, tool = "Read")))))
    }

    @Test
    fun theComposerSaysWhenAMessageWaitsForTheTurnAndWhenItCannotWrite() {
        val s = ConversationState("c1", "developer", "t", "Claude", Link.Offline("closed", 0), 0)
        assertEquals(ComposerMode.WRITE, s.composer)
        assertEquals(ComposerMode.WRITE, s.copy(running = true).composer)
        assertEquals(ComposerMode.AFTER_TURN, s.copy(running = true, steerable = false).composer)
        assertEquals(ComposerMode.WRITE, s.copy(running = false, steerable = false).composer)
        assertEquals(ComposerMode.READ_ONLY, s.copy(running = true, steerable = false, readOnly = "terminal").composer)
    }
}
