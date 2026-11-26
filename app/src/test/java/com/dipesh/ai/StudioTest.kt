package com.dipesh.ai

import com.dipesh.ai.studio.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class StudioTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun unsafePathsAreRefused() {
        listOf("../x", "a/../../x", "/etc/passwd", "C:/win", "~/x", "a\\..\\b", "", "a/b\u0000c", "..", "a/./..").forEach {
            assertNull("should refuse '$it'", SafePath.clean(it))
        }
        assertEquals("src/app.py", SafePath.clean("./src//app.py"))
        assertEquals("a/b.txt", SafePath.clean("a\\b.txt"))
    }

    @Test fun duplicatesAreNotOverwritten() {
        val b = FileBundle()
        assertTrue(b.add("README.md", "one"))
        assertFalse(b.add("readme.md", "two"))
        assertFalse(b.add("../evil", "x"))
        assertEquals("one", b.files["README.md"])
        assertEquals(2, b.rejected.size)
    }

    @Test fun parserReadsFileBlocksAndSkipsUnsafe() {
        val text = "Here:\n```file:index.html\n<h1>Hi</h1>\n```\n```python file:src/a.py\nprint(1)\n```\n```file:../bad\nx\n```\n```js\nnoname\n```"
        val b = BundleParser.parse(text)
        assertEquals(setOf("index.html", "src/a.py"), b.files.keys)
        assertEquals(1, b.rejected.size)
    }

    @Test fun allTemplatesProduceSafeMultiFileProjects() {
        val all = mapOf(
            "agent" to Templates.agent("Helper Bot", "Does chores"),
            "site" to Templates.website("Shop", "Sells tea", "jarvis"),
            "site2" to Templates.website("Shop", "Sells tea", "clean"),
            "remotion" to Templates.video("remotion", "Promo", "A promo"),
            "ffmpeg" to Templates.video("ffmpeg", "Promo", "A promo"),
            "moviepy" to Templates.video("moviepy", "Promo", "A promo")
        )
        for ((k, b) in all) {
            assertTrue("$k has several files", b.size >= 5 || k == "remotion" || k == "moviepy")
            assertTrue("$k has README", b.files.containsKey("README.md"))
            assertTrue("$k has no rejects: ${b.rejected}", b.rejected.isEmpty())
            b.files.forEach { (p, c) -> assertFalse("$k/$p leaked placeholder", c.contains('§')); assertNotNull(SafePath.clean(p)) }
        }
        assertTrue(Templates.agent("a", "b").files.keys.containsAll(listOf("requirements.txt", ".env.example", "tests/test_agent.py", "src/agent/tools.py")))
        assertTrue(Templates.agent("a", "b").files[".env.example"]!!.contains("your-key-here"))
        assertTrue(Templates.video("ffmpeg", "x", "y").files.keys.any { it.startsWith("scripts/") })
        assertTrue(Templates.video("remotion", "x", "y").files.keys.contains("package.json"))
        assertTrue(Templates.video("moviepy", "x", "y").files.keys.contains("main.py"))
        assertEquals(listOf("remotion", "ffmpeg", "moviepy"), Templates.VIDEO_KINDS)
    }

    @Test fun websiteEscapesHtmlInUserText() {
        val html = Templates.website("<script>alert(1)</script>", "x\" onload=\"y", "jarvis").files["index.html"]!!
        assertFalse(html.contains("<script>alert"))
    }

    @Test fun zipHasRootFolderAndSafeEntries() {
        val out = ByteArrayOutputStream()
        Zipper.write(mapOf("a.txt" to "A", "dir/b.txt" to "B", "../evil.txt" to "X", "A.TXT" to "dup"), "My Proj", out)
        val names = mutableListOf<String>()
        ZipInputStream(out.toByteArray().inputStream()).use { z -> generateSequence { z.nextEntry }.forEach { names.add(it.name) } }
        assertEquals(listOf("my-proj/a.txt", "my-proj/dir/b.txt"), names)
    }

    @Test fun libraryRoundTripAndNumberedCopies() {
        val lib = ProjectLibrary(tmp.newFolder("lib"))
        val b = Templates.website("Shop", "Tea", "clean")
        val n1 = lib.save("websites", "Shop", b); val n2 = lib.save("websites", "Shop", b)
        assertEquals("shop", n1); assertEquals("shop-2", n2)
        assertEquals(2, lib.list().size)
        assertEquals(b.files.keys, lib.files("websites", "shop").keys)
        val zip = lib.exportZip("websites", "shop", tmp.newFile("shop.zip"))
        assertTrue(zip.length() > 100)
        assertTrue(lib.delete("websites", "shop-2")); assertEquals(1, lib.list().size)
    }

    @Test fun csvEscapesAndBlocksFormulaInjection() {
        val csv = TaskCsv.toCsv(listOf(TaskItem(1, "Buy milk, eggs", false, "2026-12-01", 5), TaskItem(2, "=HYPERLINK(\"x\")", true, "", 6)))
        val lines = csv.trim().lines()
        assertEquals("id,title,done,due,created", lines[0])
        assertEquals("1,\"Buy milk, eggs\",false,2026-12-01,5", lines[1])
        assertTrue(lines[2].contains("'=HYPERLINK"))
    }

    @Test fun taskBackupRoundTrips() {
        val repo = TaskRepo(tmp.newFile("tasks.json"))
        val tasks = listOf(TaskItem(1, "A", true, "2026-01-01", 10), TaskItem(2, "B"))
        repo.save(tasks)
        assertEquals(tasks.map { it.title to it.done }, repo.load().map { it.title to it.done })
        assertTrue(runCatching { TaskJson.fromJson("not json") }.isFailure)
    }

    @Test fun hubQueryParsers() {
        assertEquals(ApiHub.CurrencyQuery("100", "USD", "EUR"), ApiHub.parseCurrencyQuery("100 usd to eur"))
        assertEquals(ApiHub.CurrencyQuery("1", "GBP", "INR"), ApiHub.parseCurrencyQuery("GBP in INR"))
        assertNull(ApiHub.parseCurrencyQuery("hello"))
        assertEquals("US" to 2026, ApiHub.parseHolidayQuery("us 2026"))
        assertEquals("en|ne" to "Good morning", ApiHub.parseTranslateQuery("en>ne: Good morning"))
        assertNull(ApiHub.parseTranslateQuery("just text"))
    }

    @Test fun hubResponseParsers() {
        val p = ApiHub.Place("Kathmandu, Nepal", 27.7, 85.3)
        val w = ApiHub.weather(p, """{"current":{"temperature_2m":18.5,"relative_humidity_2m":60,"wind_speed_10m":5.0,"weather_code":2},"daily":{"time":["2026-11-26"],"temperature_2m_max":[24.0],"temperature_2m_min":[10.0],"precipitation_sum":[0.0]}}""")
        assertTrue(w.contains("18.5°C") && w.contains("Mostly clear") && w.contains("10.0° to 24.0°C"))
        assertTrue(ApiHub.air(p, """{"current":{"us_aqi":160,"pm10":80.0,"pm2_5":65.0,"ozone":30.0,"nitrogen_dioxide":20.0}}""").contains("Unhealthy"))
        assertEquals("100 USD = 92.1 EUR\nRates date: 2026-11-25", ApiHub.currency(ApiHub.CurrencyQuery("100", "USD", "EUR"), """{"date":"2026-11-25","rates":{"EUR":92.1}}"""))
        assertTrue(ApiHub.country("""[{"name":{"common":"Nepal","official":"Federal Democratic Republic of Nepal"},"capital":["Kathmandu"],"region":"Asia","subregion":"Southern Asia","population":29000000,"area":147181.0,"languages":{"nep":"Nepali"},"currencies":{"NPR":{"name":"Nepalese rupee"}}}]""").contains("Capital: Kathmandu"))
        assertTrue(ApiHub.holidays("US", 2026, """[{"date":"2026-07-04","name":"Independence Day"}]""").contains("Independence Day"))
        assertTrue(ApiHub.wiki("""{"title":"Everest","extract":"Highest mountain."}""").contains("Highest mountain."))
        assertTrue(ApiHub.books("""{"docs":[{"title":"Siddhartha","author_name":["Hermann Hesse"],"first_publish_year":1922}]}""").contains("Hermann Hesse"))
        assertTrue(ApiHub.dictionary("""[{"word":"hello","phonetic":"/həˈləʊ/","meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"A greeting."}]}]}]""").contains("A greeting."))
        assertEquals("शुभ प्रभात", ApiHub.translate("""{"responseStatus":200,"responseData":{"translatedText":"शुभ प्रभात"}}"""))
        assertTrue(runCatching { ApiHub.translate("""{"responseStatus":429,"responseDetails":"limit"}""") }.exceptionOrNull() is HubError)
        assertTrue(ApiHub.quakes("""{"features":[{"properties":{"mag":5.1,"place":"Somewhere"}}]}""").contains("M5.1"))
        assertEquals(10, ApiHub.services.size)
    }
}
