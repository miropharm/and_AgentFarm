package com.muvusoft.agentfarm.core.conversation

/** What the conversation draws, one row each: tool steps in a row fold into one. */
sealed interface Block {
    val first: Int

    data class User(val message: Message) : Block { override val first get() = message.i }
    data class Answer(val message: Message) : Block { override val first get() = message.i }
    data class Steps(val steps: List<Message>) : Block { override val first get() = steps.first().i }
    data class Note(val message: Message) : Block { override val first get() = message.i }
}

object Blocks {
    private const val NAMES_SHOWN = 3

    fun of(messages: List<Message>): List<Block> {
        val out = mutableListOf<Block>()
        var run = mutableListOf<Message>()
        fun flush() { if (run.isNotEmpty()) { out += Block.Steps(run); run = mutableListOf() } }
        for (m in messages) {
            when (m.role) {
                Role.TOOL, Role.TOOL_RESULT -> run += m
                Role.USER -> { flush(); out += Block.User(m) }
                Role.ASSISTANT -> { flush(); out += Block.Answer(m) }
                Role.SYSTEM -> { flush(); out += Block.Note(m) }
            }
        }
        flush()
        return out
    }

    /**
     * Where the conversation opens: the START of the last answer, never the end of a long one. While a
     * turn has not answered yet, the last thing the user asked, with the steps below it.
     */
    fun openAt(blocks: List<Block>): Int {
        val lastUser = blocks.indexOfLast { it is Block.User }
        val lastAnswer = blocks.indexOfLast { it is Block.Answer }
        return when {
            lastAnswer > lastUser -> lastAnswer
            lastUser >= 0 -> lastUser
            else -> (blocks.size - 1).coerceAtLeast(0)
        }
    }

    /** A step's tool: sessions.read's `tool`, or a Console snapshot's first word of "Name detail". */
    fun stepName(m: Message): String = m.tool?.substringBefore(' ')?.takeIf(String::isNotBlank) ?: m.role.name.lowercase()

    /** A step's one line: its input, else the snapshot's detail, else its text; first line only. */
    fun stepDetail(m: Message): String =
        (m.input ?: m.tool?.substringAfter(' ', "")?.takeIf(String::isNotBlank) ?: m.text).orEmpty()
            .lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /** The calls an open steps row lists; a run of results alone lists its results. */
    fun calls(steps: Block.Steps): List<Message> = steps.steps.filter { it.role == Role.TOOL }.ifEmpty { steps.steps }

    /** A step's detail sheet: the tool, its whole input, then what came back (the result after it). */
    fun sheet(steps: Block.Steps, call: Message): List<String> {
        val at = steps.steps.indexOf(call)
        val result = steps.steps.drop(at + 1).takeWhile { it.role == Role.TOOL_RESULT }.firstOrNull()
        return listOfNotNull(
            stepName(call),
            (call.input ?: call.tool?.substringAfter(' ', "") ?: call.text)?.takeIf(String::isNotBlank),
            result?.text?.takeIf(String::isNotBlank),
        )
    }

    /** "4 steps · Read, Bash, Edit": the calls (results are part of their call) and the tools by first use. */
    fun summary(steps: Block.Steps): String {
        val calls = steps.steps.count { it.role == Role.TOOL }.takeIf { it > 0 } ?: steps.steps.size
        val names = steps.steps.mapNotNull { it.tool?.substringBefore(' ')?.takeIf(String::isNotBlank) }.distinct()
        val shown = names.take(NAMES_SHOWN).joinToString(", ") + if (names.size > NAMES_SHOWN) " +${names.size - NAMES_SHOWN}" else ""
        val errors = steps.steps.count { it.isError }
        return (if (calls == 1) "1 step" else "$calls steps") +
            (if (shown.isNotEmpty()) " · $shown" else "") +
            (if (errors > 0) " · $errors failed" else "")
    }
}
