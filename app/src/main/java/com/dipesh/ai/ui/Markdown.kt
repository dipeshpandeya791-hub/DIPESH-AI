package com.dipesh.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MdStyle { BOLD, ITALIC, CODE, LINK }

data class MdSpan(val start: Int, val end: Int, val style: MdStyle)

data class MdText(val text: String, val spans: List<MdSpan>)

sealed class MdBlock {
    data class Paragraph(val content: MdText) : MdBlock()
    data class Heading(val level: Int, val content: MdText) : MdBlock()
    data class Bullet(val marker: String, val content: MdText) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
}

/** A small, dependency-free Markdown parser: headings, lists, code blocks, bold, italic, inline code, links. */
object MarkdownParser {
    private val heading = Regex("^(#{1,3})\\s+(.*)$")
    private val bullet = Regex("^\\s*[-*•]\\s+(.*)$")
    private val ordered = Regex("^\\s*(\\d+)[.)]\\s+(.*)$")
    private val link = Regex("^\\[([^\\]]+)]\\(([^)]+)\\)")

    fun parse(src: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val para = ArrayList<String>()
        fun flush() {
            if (para.isNotEmpty()) {
                blocks.add(MdBlock.Paragraph(inline(para.joinToString("\n"))))
                para.clear()
            }
        }
        val lines = src.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                flush()
                val lang = line.trim().removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(lang, code.toString()))
                i++ // skip the closing fence (or run past the end for an unfinished stream)
                continue
            }
            val h = heading.find(line)
            val b = bullet.find(line)
            val o = ordered.find(line)
            when {
                line.isBlank() -> flush()
                h != null -> {
                    flush()
                    blocks.add(MdBlock.Heading(h.groupValues[1].length, inline(h.groupValues[2])))
                }
                b != null -> {
                    flush()
                    blocks.add(MdBlock.Bullet("•", inline(b.groupValues[1])))
                }
                o != null -> {
                    flush()
                    blocks.add(MdBlock.Bullet(o.groupValues[1] + ".", inline(o.groupValues[2])))
                }
                else -> para.add(line)
            }
            i++
        }
        flush()
        return blocks
    }

    fun inline(s: String): MdText {
        val sb = StringBuilder()
        val spans = ArrayList<MdSpan>()
        var i = 0
        fun wrap(inner: String, style: MdStyle) {
            val m = inline(inner)
            val st = sb.length
            sb.append(m.text)
            m.spans.forEach { spans.add(MdSpan(it.start + st, it.end + st, it.style)) }
            spans.add(MdSpan(st, sb.length, style))
        }
        while (i < s.length) {
            val ch = s[i]
            when {
                ch == '`' -> {
                    val j = s.indexOf('`', i + 1)
                    if (j > i + 1) {
                        val st = sb.length
                        sb.append(s, i + 1, j)
                        spans.add(MdSpan(st, sb.length, MdStyle.CODE))
                        i = j + 1
                    } else {
                        sb.append(ch); i++
                    }
                }
                s.startsWith("**", i) -> {
                    val j = s.indexOf("**", i + 2)
                    if (j > i + 2) {
                        wrap(s.substring(i + 2, j), MdStyle.BOLD)
                        i = j + 2
                    } else {
                        sb.append(ch); i++
                    }
                }
                ch == '*' && i + 1 < s.length && s[i + 1] != ' ' && s[i + 1] != '*' -> {
                    val j = s.indexOf('*', i + 1)
                    if (j > i + 1) {
                        wrap(s.substring(i + 1, j), MdStyle.ITALIC)
                        i = j + 1
                    } else {
                        sb.append(ch); i++
                    }
                }
                ch == '[' -> {
                    val m = link.find(s.substring(i))
                    if (m != null) {
                        val st = sb.length
                        sb.append(m.groupValues[1])
                        spans.add(MdSpan(st, sb.length, MdStyle.LINK))
                        i += m.value.length
                    } else {
                        sb.append(ch); i++
                    }
                }
                else -> {
                    sb.append(ch); i++
                }
            }
        }
        return MdText(sb.toString(), spans)
    }
}

private fun MdText.toAnnotated(codeBg: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { sp ->
        val style = when (sp.style) {
            MdStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MdStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MdStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 13.sp)
            MdStyle.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        }
        addStyle(style, sp.start, sp.end)
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = LocalDipeshColors.current.text) {
    val c = LocalDipeshColors.current
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val codeBg = if (c.isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    block.content.toAnnotated(codeBg, c.cyan), color = color, style = MaterialTheme.typography.bodyLarge
                )
                is MdBlock.Heading -> Text(
                    block.content.toAnnotated(codeBg, c.cyan), color = color,
                    style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
                is MdBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(block.marker, color = c.violet, style = MaterialTheme.typography.bodyLarge)
                    Text(block.content.toAnnotated(codeBg, c.cyan), color = color, style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Code -> Text(
                    block.code,
                    modifier = Modifier
                        .background(Color(if (c.isDark) 0x66000000 else 0x12000000), RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }
}
