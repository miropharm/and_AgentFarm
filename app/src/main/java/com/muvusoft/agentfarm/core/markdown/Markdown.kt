package com.muvusoft.agentfarm.core.markdown

/** A run of text with one style. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/** A list entry: its nesting depth, its number (null = a bullet), its words. */
data class Item(val depth: Int, val number: Int?, val spans: List<Span>)

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<Span>) : MdBlock
    data class Paragraph(val spans: List<Span>) : MdBlock
    data class Items(val items: List<Item>) : MdBlock
    data class Code(val lang: String, val text: String) : MdBlock
    data class Quote(val spans: List<Span>) : MdBlock
    data class Table(val header: List<List<Span>>, val rows: List<List<List<Span>>>) : MdBlock
    data object Rule : MdBlock
}

/**
 * The markdown an agent answers in, as blocks a phone can draw: headings, paragraphs, lists, fenced code,
 * quotes, tables and rules; inside them bold, italic, inline code and links. Anything it does not know is
 * kept as plain text — nothing an agent wrote is ever dropped.
 */
object Markdown {
    private val FENCE = Regex("^\\s*(```+|~~~+)\\s*([^`\\s]*).*$")
    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val ITEM = Regex("^(\\s*)([-*+]|(\\d{1,9})[.)])\\s+(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
    private val TABLE_RULE = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
    private val BULLETS = listOf("•", "◦", "▪")

    /** What an item is drawn behind: its number, else a bullet that changes with depth. */
    fun marker(item: Item): String = item.number?.let { "$it." } ?: BULLETS[item.depth % BULLETS.size]

    /** A table column's width in characters: its longest cell, so every row lines up. */
    fun columnChars(table: MdBlock.Table): List<Int> {
        val rows = listOf(table.header) + table.rows
        val count = rows.maxOf { it.size }
        return (0 until count).map { c -> rows.maxOf { r -> r.getOrNull(c)?.sumOf { it.text.length } ?: 0 } }
    }

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<MdBlock>()
        val para = mutableListOf<String>()
        fun flush() {
            if (para.isNotEmpty()) { out += MdBlock.Paragraph(Inline.parse(para.joinToString("\n"))); para.clear() }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE.matchEntire(line)
            when {
                fence != null -> {
                    flush()
                    val mark = fence.groupValues[1]
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(mark)) { body += lines[i]; i++ }
                    out += MdBlock.Code(fence.groupValues[2], body.joinToString("\n"))
                }
                line.isBlank() -> flush()
                HEADING.matchEntire(line) != null -> {
                    flush()
                    val m = HEADING.matchEntire(line)!!
                    out += MdBlock.Heading(m.groupValues[1].length, Inline.parse(m.groupValues[2]))
                }
                RULE.matches(line) -> { flush(); out += MdBlock.Rule }
                '|' in line && i + 1 < lines.size && TABLE_RULE.matches(lines[i + 1]) && '-' in lines[i + 1] -> {
                    flush()
                    val header = cells(line)
                    val rows = mutableListOf<List<List<Span>>>()
                    i += 2
                    while (i < lines.size && '|' in lines[i] && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                    out += MdBlock.Table(header, rows)
                    continue
                }
                ITEM.matchEntire(line) != null -> {
                    flush()
                    val items = mutableListOf<Item>()
                    while (i < lines.size) {
                        val m = ITEM.matchEntire(lines[i]) ?: break
                        val words = StringBuilder(m.groupValues[4])
                        while (i + 1 < lines.size && continues(lines[i + 1])) { words.append('\n').append(lines[i + 1].trim()); i++ }
                        items += Item(depth(m.groupValues[1]), m.groupValues[3].toIntOrNull(), Inline.parse(words.toString()))
                        i++
                    }
                    out += MdBlock.Items(items)
                    continue
                }
                QUOTE.matchEntire(line) != null -> {
                    flush()
                    val body = mutableListOf<String>()
                    while (i < lines.size) { body += (QUOTE.matchEntire(lines[i]) ?: break).groupValues[1]; i++ }
                    out += MdBlock.Quote(Inline.parse(body.joinToString("\n")))
                    continue
                }
                else -> para += line
            }
            i++
        }
        flush()
        return out
    }

    /** An indented line that is not a new item carries on the item above it. */
    private fun continues(line: String): Boolean =
        line.isNotBlank() && line.startsWith("  ") && ITEM.matchEntire(line) == null && FENCE.matchEntire(line) == null

    private fun depth(indent: String): Int = indent.replace("\t", "    ").length / 2

    private fun cells(line: String): List<List<Span>> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < line.length) {
            val c = line[k]
            if (c == '\\' && k + 1 < line.length && line[k + 1] == '|') { cur.append('|'); k += 2; continue }
            if (c == '|') { parts += cur.toString(); cur.clear() } else cur.append(c)
            k++
        }
        parts += cur.toString()
        val trimmed = parts.map { it.trim() }.toMutableList()
        if (trimmed.isNotEmpty() && trimmed.first().isEmpty() && line.trimStart().startsWith("|")) trimmed.removeAt(0)
        if (trimmed.isNotEmpty() && trimmed.last().isEmpty() && line.trimEnd().endsWith("|")) trimmed.removeAt(trimmed.size - 1)
        return trimmed.map { Inline.parse(it) }
    }
}
