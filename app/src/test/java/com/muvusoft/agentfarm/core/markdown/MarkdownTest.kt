package com.muvusoft.agentfarm.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    private fun text(spans: List<Span>) = spans.joinToString("") { it.text }

    @Test
    fun headingsParagraphsAndRules() {
        val b = Markdown.parse("# Durum\nİlk satır\nikinci satır\n\n---\n### Kalan ###")
        assertEquals(4, b.size)
        assertEquals(1, (b[0] as MdBlock.Heading).level)
        assertEquals("Durum", text((b[0] as MdBlock.Heading).spans))
        assertEquals("İlk satır\nikinci satır", text((b[1] as MdBlock.Paragraph).spans))
        assertEquals(MdBlock.Rule, b[2])
        assertEquals("Kalan", text((b[3] as MdBlock.Heading).spans))
    }

    @Test
    fun listsKeepDepthNumbersAndContinuations() {
        val b = Markdown.parse("Önce:\n- bir\n  devamı\n  - iç madde\n1. ilk\n2) ikinci")
        assertEquals("Önce:", text((b[0] as MdBlock.Paragraph).spans))
        val items = (b[1] as MdBlock.Items).items
        assertEquals(listOf(0, 1, 0, 0), items.map { it.depth })
        assertEquals(listOf(null, null, 1, 2), items.map { it.number })
        assertEquals("bir\ndevamı", text(items[0].spans))
    }

    @Test
    fun fencedCodeKeepsItsTextAndAnUnclosedFenceRunsToTheEnd() {
        val b = Markdown.parse("```kotlin\nval x = 1\n\n  **not bold**\n```\nsonra")
        assertEquals(MdBlock.Code("kotlin", "val x = 1\n\n  **not bold**"), b[0])
        assertEquals("sonra", text((b[1] as MdBlock.Paragraph).spans))
        assertEquals(listOf(MdBlock.Code("", "a\nb")), Markdown.parse("~~~\na\nb"))
    }

    @Test
    fun tablesSplitCellsAndKeepEscapedPipes() {
        val b = Markdown.parse("| Kalem | Durum |\n|---|:--:|\n| U-17 | **açık** |\n| a \\| b | c |\nsonra")
        val t = b[0] as MdBlock.Table
        assertEquals(listOf("Kalem", "Durum"), t.header.map(::text))
        assertEquals(listOf("U-17", "açık"), t.rows[0].map(::text))
        assertTrue(t.rows[0][1].single().bold)
        assertEquals(listOf("a | b", "c"), t.rows[1].map(::text))
        assertEquals("sonra", text((b[1] as MdBlock.Paragraph).spans))
    }

    @Test
    fun markers() {
        assertEquals(listOf("3.", "•", "◦", "▪", "•"), listOf(Item(0, 3, emptyList())).plus((0..3).map { Item(it, null, emptyList()) }).map(Markdown::marker))
    }

    @Test
    fun quotesJoinTheirLines() {
        val b = Markdown.parse("> bir\n> iki\nüç")
        assertEquals("bir\niki", text((b[0] as MdBlock.Quote).spans))
        assertEquals("üç", text((b[1] as MdBlock.Paragraph).spans))
    }

    @Test
    fun inlineStyles() {
        val s = Inline.parse("Run **npm test** with `--watch`, see *notes* and [the PR](https://x.y/1).")
        assertEquals("Run npm test with --watch, see notes and the PR.", text(s))
        assertTrue(s.single { it.text == "npm test" }.bold)
        assertTrue(s.single { it.text == "--watch" }.code)
        assertTrue(s.single { it.text == "notes" }.italic)
        assertEquals("https://x.y/1", s.single { it.text == "the PR" }.link)
    }

    @Test
    fun boldAndItalicNestAndCodeIsLiteral() {
        val s = Inline.parse("*a **b** c* and `**x**`")
        assertTrue(s.single { it.text == "b" }.let { it.bold && it.italic })
        assertTrue(s.single { it.text == "**x**" }.code)
    }

    @Test
    fun plainTextThatOnlyLooksLikeMarkdownStaysPlain() {
        assertEquals(listOf(Span("snake_case_name and 2 * 3 * 4")), Inline.parse("snake_case_name and 2 * 3 * 4"))
        assertEquals(listOf(Span("an *unclosed star and `tick")), Inline.parse("an *unclosed star and `tick"))
        assertEquals(listOf(Span("not *a link [x] (y)")), Inline.parse("not \\*a link [x] (y)"))
        // a bullet whose words start with a dash is still a bullet, never a rule
        assertEquals(MdBlock.Items(listOf(Item(0, null, listOf(Span("- x"))))), Markdown.parse("- - x").single())
    }

    @Test
    fun nothingAnAgentWroteIsDropped() {
        val src = "# T\n\npara **b** `c`\n\n- i\n\n```\ncode\n```\n\n| h |\n|---|\n| r |\n\n> q"
        val words = Markdown.parse(src).joinToString(" ") { b ->
            when (b) {
                is MdBlock.Heading -> text(b.spans)
                is MdBlock.Paragraph -> text(b.spans)
                is MdBlock.Items -> b.items.joinToString(" ") { text(it.spans) }
                is MdBlock.Code -> b.text
                is MdBlock.Quote -> text(b.spans)
                is MdBlock.Table -> (b.header + b.rows.flatten()).joinToString(" ", transform = ::text)
                MdBlock.Rule -> ""
            }
        }
        assertEquals("T para b c i code h r q", words)
    }
}
