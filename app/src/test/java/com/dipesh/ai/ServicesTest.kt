package com.dipesh.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesTest {
    private val rss = """
        <rss><channel>
          <title>Feed title (not an item)</title>
          <item><title>Rain &amp; wind hit Kathmandu - The Himalayan Times</title></item>
          <item><title><![CDATA[Markets rally - Reuters]]></title></item>
          <item><title>Third story</title></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun newsParserReadsItemTitlesOnly() {
        val t = NewsParser.titles(rss, 5)
        assertEquals(3, t.size)
        assertEquals("Rain & wind hit Kathmandu - The Himalayan Times", t[0])
        assertEquals("Markets rally - Reuters", t[1])
    }

    @Test
    fun newsParserRespectsMax() {
        assertEquals(2, NewsParser.titles(rss, 2).size)
    }

    @Test
    fun weatherCodesAreDescribed() {
        assertEquals("clear sky", WeatherCodes.describe(0))
        assertEquals("thunderstorm", WeatherCodes.describe(95))
        assertTrue(WeatherCodes.describe(12345).isNotBlank())
    }
}
