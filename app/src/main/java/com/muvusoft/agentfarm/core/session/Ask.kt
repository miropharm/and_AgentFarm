package com.muvusoft.agentfarm.core.session

import com.muvusoft.agentfarm.core.contract.Codec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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

    /** `asking` as console.sessions and a live frame say it (vsc askingOf); null when nothing waits or it does not read. */
    fun read(asking: JsonElement?): Ask? {
        val o = asking as? JsonObject ?: return null
        val w = try {
            Codec.json.decodeFromJsonElement(WireAsk.serializer(), o)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val questions = w.questions.map { q -> AskQuestion(q.question, q.header, q.multiSelect, q.options.map { AskOption(it.label, it.description) }) }
        // vsc names it by its questions: none is a permission
        val kind = if (w.kind == "permission" || (w.kind != "question" && questions.isEmpty())) AskKind.PERMISSION else AskKind.QUESTION
        return Ask(kind, w.detail.orEmpty(), w.tool, questions, w.waitingSince)
    }
}

@Serializable
private data class WireOption(val label: String, val description: String? = null)

@Serializable
private data class WireQuestion(val question: String = "", val header: String? = null, val multiSelect: Boolean = false, val options: List<WireOption> = emptyList())

@Serializable
private data class WireAsk(val kind: String = "", val tool: String? = null, val questions: List<WireQuestion> = emptyList(), val detail: String? = null, val waitingSince: Long = 0)
