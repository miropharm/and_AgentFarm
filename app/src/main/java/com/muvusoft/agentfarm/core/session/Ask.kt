package com.muvusoft.agentfarm.core.session

/** Who may settle what waits: a question (console.answer), a permission (the user), an approval (needs.act). */
enum class AskKind { QUESTION, PERMISSION, APPROVAL }

/** One choice of a waiting question: console.sessions `asking.questions[].options[]`. */
data class AskOption(val label: String, val description: String? = null)

/** console.sessions `asking.questions[]`: AskUserQuestion's own shape. */
data class AskQuestion(
    val question: String,
    val header: String? = null,
    val multiSelect: Boolean = false,
    val options: List<AskOption> = emptyList(),
)

/** A turn parked on the user: console.sessions `asking`, or a needs.list row. */
data class Ask(
    val kind: AskKind,
    /** The ask's one line (`asking.detail`): the question, or the tool's command. */
    val detail: String,
    val tool: String? = null,
    val questions: List<AskQuestion> = emptyList(),
    /** `asking.waitingSince` */
    val since: Long,
)

/** An answer short enough to give from the row, without opening the conversation. */
sealed interface Quick {
    data object Allow : Quick
    data object Deny : Quick
    data class Option(val label: String) : Quick
}

object Asks {
    private const val QUICK_LABEL_MAX = 18
    private const val QUICK_OPTIONS_MAX = 3

    /**
     * What the Now row can answer itself: a permission is Deny / Allow (the yes at the end, as on the card); a single one-pick question with a
     * few short options is its options. Anything longer opens the conversation with the card.
     */
    fun quick(ask: Ask): List<Quick> = when (ask.kind) {
        AskKind.PERMISSION -> listOf(Quick.Deny, Quick.Allow)
        AskKind.APPROVAL -> emptyList()
        AskKind.QUESTION -> ask.questions.singleOrNull()
            ?.takeIf { q -> !q.multiSelect && q.options.size in 2..QUICK_OPTIONS_MAX && q.options.all { it.label.length <= QUICK_LABEL_MAX } }
            ?.options?.map { Quick.Option(it.label) }
            ?: emptyList()
    }

    /** The row's one line: the first question, else the ask's own line, first line only. */
    fun firstLine(ask: Ask): String =
        (ask.questions.firstOrNull()?.question ?: ask.detail).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}
