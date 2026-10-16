package com.muvusoft.agentfarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.muvusoft.agentfarm.core.markdown.MdBlock
import com.muvusoft.agentfarm.core.markdown.Markdown
import com.muvusoft.agentfarm.core.markdown.Span
import com.muvusoft.agentfarm.core.theme.Tokens
import com.muvusoft.agentfarm.ui.theme.CodeFont

/** An agent's markdown, drawn full width: headings, lists, code that scrolls sideways, tables that line up. */
@Composable
fun MarkdownText(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { MdBlockView(it) }
    }
}

@Composable
private fun MdBlockView(b: MdBlock) {
    val type = MaterialTheme.typography
    when (b) {
        is MdBlock.Heading -> Text(
            annotated(b.spans),
            Modifier.padding(top = 4.dp).semantics { heading() },
            style = when (b.level) { 1 -> type.titleLarge; 2 -> type.titleMedium; else -> type.titleSmall },
        )
        is MdBlock.Paragraph -> Text(annotated(b.spans), style = type.bodyLarge)
        is MdBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEach { item ->
                Row(Modifier.padding(start = (item.depth * 18).dp)) {
                    Text(Markdown.marker(item), Modifier.width(if (item.number != null) 28.dp else 18.dp), style = type.bodyLarge)
                    Text(annotated(item.spans), style = type.bodyLarge)
                }
            }
        }
        is MdBlock.Code -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(12.dp)) {
                if (b.lang.isNotEmpty()) Text(b.lang, Modifier.padding(bottom = 6.dp), style = type.labelSmall, color = MaterialTheme.colorScheme.outline)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(b.text, fontFamily = CodeFont, fontSize = Tokens.CODE_SP.sp, lineHeight = 19.sp, softWrap = false)
                }
            }
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            Text(annotated(b.spans), Modifier.padding(start = 12.dp), style = type.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is MdBlock.Table -> TableView(b)
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TableView(t: MdBlock.Table) {
    val rows = listOf(t.header) + t.rows
    val count = rows.maxOf { it.size }
    val cells = rows.map { row -> (0 until count).map { c -> annotated(row.getOrNull(c).orEmpty()) } }
    val body = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A column is as wide as its widest cell in the font really drawn (the font scale included), so a short
    // value never breaks inside itself; only a cell past the cap wraps.
    val widths = remember(cells, body, density) {
        (0 until count).map { c ->
            val px = cells.withIndex().maxOf { (r, row) ->
                measurer.measure(row[c], if (r == 0) body.copy(fontWeight = FontWeight.SemiBold) else body, softWrap = false).size.width
            }
            with(density) { px.toDp() + 20.dp + 1.dp }.coerceIn(56.dp, 240.dp)
        }
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            cells.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(Modifier.width(widths.fold(0.dp) { a, w -> a + w }), color = line)
                Row {
                    widths.forEachIndexed { c, w ->
                        Text(
                            row[c],
                            Modifier.widthIn(min = w, max = w).padding(horizontal = 10.dp, vertical = 8.dp),
                            style = body,
                            fontWeight = if (r == 0) FontWeight.SemiBold else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun annotated(spans: List<Span>): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        spans.forEach { s ->
            val style = SpanStyle(
                fontWeight = if (s.bold) FontWeight.SemiBold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                fontFamily = if (s.code) CodeFont else null,
                background = if (s.code) codeBg else Color.Unspecified,
                fontSize = if (s.code) 0.92.em else androidx.compose.ui.unit.TextUnit.Unspecified,
            )
            val url = s.link
            if (url != null) {
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    withStyle(style) { append(s.text) }
                }
            } else {
                withStyle(style) { append(s.text) }
            }
        }
    }
}
