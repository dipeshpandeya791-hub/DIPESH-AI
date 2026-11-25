package com.dipesh.ai

import com.dipesh.ai.ui.MarkdownParser
import com.dipesh.ai.ui.MdBlock
import com.dipesh.ai.ui.MdStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun boldAndItalicSpans() {
        val m = MarkdownParser.inline("Hello **big** and *small* `code`")
        assertEquals("Hello big and small code", m.text)
        assertTrue(m.spans.any { it.style == MdStyle.BOLD && m.text.substring(it.start, it.end) == "big" })
        assertTrue(m.spans.any { it.style == MdStyle.ITALIC && m.text.substring(it.start, it.end) == "small" })
        assertTrue(m.spans.any { it.style == MdStyle.CODE && m.text.substring(it.start, it.end) == "code" })
    }

    @Test
    fun blocksAreRecognised() {
        val blocks = MarkdownParser.parse("# Title\n\n- one\n- two\n1. first\n\n```kotlin\nval x = 1\n```\nplain text")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(2, blocks.count { it is MdBlock.Bullet && it.marker == "•" })
        assertTrue(blocks.any { it is MdBlock.Bullet && it.marker == "1." })
        val code = blocks.first { it is MdBlock.Code } as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
        assertTrue(blocks.last() is MdBlock.Paragraph)
    }

    @Test
    fun unfinishedCodeFenceDoesNotCrash() {
        val blocks = MarkdownParser.parse("Here:\n```\nline one\nline two")
        assertTrue(blocks.last() is MdBlock.Code)
    }

    @Test
    fun linksKeepOnlyLabel() {
        val m = MarkdownParser.inline("see [Google](https://google.com) now")
        assertEquals("see Google now", m.text)
    }
}
