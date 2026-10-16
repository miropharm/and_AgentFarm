package com.muvusoft.agentfarm.core.markdown

/** Inline markdown: `code`, **bold** / __bold__, *italic* / _italic_, [text](url), backslash escapes. */
object Inline {
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|>~"

    fun parse(text: String): List<Span> = merge(spans(text, Span("")))

    private fun spans(s: String, style: Span): List<Span> {
        val out = mutableListOf<Span>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += style.copy(text = plain.toString()); plain.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) { plain.append(s[i + 1]); i += 2; continue }
            if (c == '`') {
                val ticks = run { var n = 0; while (i + n < s.length && s[i + n] == '`') n++; n }
                val fence = "`".repeat(ticks)
                val end = s.indexOf(fence, i + ticks)
                if (end > i) {
                    flush()
                    out += style.copy(text = s.substring(i + ticks, end).trim(), code = true, bold = false, italic = false)
                    i = end + ticks
                    continue
                }
                plain.append(fence); i += ticks; continue
            }
            if ((c == '*' || c == '_') && i + 1 < s.length && s[i + 1] == c) {
                val mark = "$c$c"
                val end = s.indexOf(mark, i + 2)
                if (end > i + 2 && !s[i + 2].isWhitespace()) {
                    flush(); out += spans(s.substring(i + 2, end), style.copy(bold = true)); i = end + 2; continue
                }
            }
            if ((c == '*' || c == '_') && opens(s, i)) {
                val end = closing(s, i)
                if (end > 0) {
                    flush(); out += spans(s.substring(i + 1, end), style.copy(italic = true)); i = end + 1; continue
                }
            }
            if (c == '[') {
                val close = s.indexOf("](", i + 1)
                val paren = if (close > i) s.indexOf(')', close + 2) else -1
                if (close > i && paren > close && '\n' !in s.substring(i, paren)) {
                    flush()
                    out += spans(s.substring(i + 1, close), style.copy(link = s.substring(close + 2, paren).trim()))
                    i = paren + 1
                    continue
                }
            }
            plain.append(c)
            i++
        }
        flush()
        return out
    }

    /** A single marker opens emphasis only before a word; `_` also only at a word's start (snake_case stays). */
    private fun opens(s: String, i: Int): Boolean {
        val next = s.getOrNull(i + 1) ?: return false
        if (next.isWhitespace() || next == s[i]) return false
        return s[i] == '*' || i == 0 || !s[i - 1].isLetterOrDigit()
    }

    private fun closing(s: String, i: Int): Int {
        val mark = s[i]
        var k = i + 1
        while (k < s.length) {
            val at = s.indexOf(mark, k)
            if (at < 0 || s[at - 1] == '\n') return -1
            val doubled = s.getOrNull(at + 1) == mark
            val wordAfter = s.getOrNull(at + 1)?.isLetterOrDigit() == true
            if (!s[at - 1].isWhitespace() && !doubled && !(mark == '_' && wordAfter)) return at
            k = at + if (doubled) 2 else 1
        }
        return -1
    }

    private fun merge(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (sp in spans) {
            if (sp.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == sp.copy(text = "")) out[out.size - 1] = last.copy(text = last.text + sp.text) else out += sp
        }
        return out
    }
}
