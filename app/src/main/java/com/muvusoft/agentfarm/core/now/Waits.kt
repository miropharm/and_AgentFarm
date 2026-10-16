package com.muvusoft.agentfarm.core.now

import com.muvusoft.agentfarm.core.session.AnswerCall
import com.muvusoft.agentfarm.core.session.AskKind
import com.muvusoft.agentfarm.core.session.Answers
import com.muvusoft.agentfarm.core.session.Asks
import com.muvusoft.agentfarm.core.session.Quick

/** What a Now "waiting" row can settle itself, and the call that settles it. */
object Waits {
    /**
     * The row's own buttons: a permission only when the farm gave its Needs key (the key is never built
     * here), a question only on a Console card (console.answer takes the card). Otherwise the row opens.
     */
    fun quick(w: Waiting): List<Quick> = when (w.ask.kind) {
        AskKind.PERMISSION -> if (w.need != null) Asks.quick(w.ask) else emptyList()
        AskKind.QUESTION -> if (w.ask.questions.isNotEmpty()) Asks.quick(w.ask) else emptyList()
        AskKind.APPROVAL -> emptyList()
    }

    /** The call a quick button makes; null when the row cannot settle it (it opens instead). */
    fun answer(w: Waiting, q: Quick): AnswerCall? {
        if (q !in quick(w)) return null
        return when (q) {
            Quick.Allow -> w.need?.let { Answers.need(it, Answers.ALLOW) }
            Quick.Deny -> w.need?.let { Answers.need(it, Answers.DENY) }
            is Quick.Option -> w.ask.questions.singleOrNull()?.let { Answers.question(w.session, it.question, q.label) }
        }
    }
}
