package com.dipesh.ai

import com.dipesh.ai.pet.*
import com.dipesh.ai.studio.*
import com.dipesh.ai.studio.LocalSkills.Period
import com.dipesh.ai.studio.LocalSkills.Skill
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class LocalSkillsTest {
    private fun answer(s: String) = (LocalSkills.parse(s) as? Skill.Answer)?.reply

    @Test fun calculator() {
        assertEquals("The answer is 100.", answer("what is 25 times 4"))
        assertEquals("The answer is 360.", answer("what is 15 percent of 2400"))
        assertEquals("The answer is 14.", answer("calculate 2 + 3 * 4"))
        assertEquals("The answer is 5.", answer("10 divided by 2"))
        assertEquals("The answer is 1024.", answer("2 to the power of 10"))
        assertEquals("The answer is 7.", answer("what's 12 minus 5"))
        assertEquals("That can't be calculated.", answer("what is 5 divided by 0"))
    }

    @Test fun calculatorDoesNotHijackNormalSpeech() {
        assertNull(LocalSkills.parse("what is the capital of nepal"))
        assertNull(LocalSkills.parse("what is 5"))
        assertNull(LocalSkills.parse("tell me a story"))
        assertNull(LocalSkills.parse("open whatsapp"))
        assertNull(LocalSkills.parse("hello"))
    }

    @Test fun conversions() {
        assertEquals("5 km is 3.1069 mi.", answer("convert 5 km to mi"))
        assertEquals("5 kilometers is 3.1069 miles.", answer("5 kilometers to miles"))
        assertEquals("100 celsius is 212 fahrenheit.", answer("100 celsius in fahrenheit"))
        assertEquals("2 kg is 4.4092 pounds.", answer("2 kg in pounds"))
        assertNull(LocalSkills.parse("10 apples to oranges"))
        assertNull(LocalSkills.parse("5 km to kg"))
    }

    @Test fun timers() {
        assertEquals(600, (LocalSkills.parse("set a timer for 10 minutes") as Skill.Timer).seconds)
        assertEquals(90, (LocalSkills.parse("start a 90 second timer") as Skill.Timer).seconds)
        assertEquals(1200, (LocalSkills.parse("set timer for twenty minutes") as Skill.Timer).seconds)
        assertEquals(2700, (LocalSkills.parse("timer for forty five minutes") as Skill.Timer).seconds)
        assertEquals(1800, (LocalSkills.parse("countdown half an hour") as Skill.Timer).seconds)
        assertEquals(3600, (LocalSkills.parse("set a timer for an hour") as Skill.Timer).seconds)
        val f = LocalSkills.parse("start focus mode") as Skill.Timer
        assertTrue(f.focus); assertEquals(1500, f.seconds)
        assertEquals(3000, (LocalSkills.parse("start focus for 50 minutes") as Skill.Timer).seconds)
        assertNull(LocalSkills.parse("what is a timer"))
        assertNull(LocalSkills.parse("set a timer"))
        assertTrue(LocalSkills.parse("cancel the timer") is Skill.CancelTimer)
        assertTrue(LocalSkills.parse("how much time is left on the timer") is Skill.TimerLeft)
    }

    @Test fun durationAndClock() {
        assertEquals("1 hour and 30 minutes", LocalSkills.durationText(5400))
        assertEquals("45 seconds", LocalSkills.durationText(45))
        assertEquals("25:00", LocalSkills.clock(1500)); assertEquals("0:05", LocalSkills.clock(5))
    }

    @Test fun spending() {
        val s = LocalSkills.parse("I spent 500 on lunch") as Skill.Spend
        assertEquals(500.0, s.amount, 0.0); assertEquals("lunch", s.note)
        val p = LocalSkills.parse("paid rs 1200 for the groceries") as Skill.Spend
        assertEquals(1200.0, p.amount, 0.0); assertEquals("groceries", p.note)
        assertEquals("misc", (LocalSkills.parse("spent 50") as Skill.Spend).note)
        assertEquals(Period.WEEK, (LocalSkills.parse("how much did I spend this week") as Skill.SpendSummary).period)
        assertEquals(Period.TODAY, (LocalSkills.parse("show my expenses") as Skill.SpendSummary).period)
        assertEquals(Period.MONTH, (LocalSkills.parse("what are my expenses this month") as Skill.SpendSummary).period)
    }

    @Test fun briefingAndBattery() {
        assertTrue(LocalSkills.parse("good morning") is Skill.Briefing)
        assertTrue(LocalSkills.parse("daily briefing") is Skill.Briefing)
        assertTrue(LocalSkills.parse("what's my battery level") is Skill.Battery)
        assertNull(LocalSkills.parse("good morning to everyone in the village"))
        val b = LocalSkills.briefing(8, "Friday, 27 November", listOf("a", "b", "c", "d"), "Sunny, 22 C", 20, 500.0)
        assertTrue(b.startsWith("Good morning.")); assertTrue(b.contains("4 open tasks")); assertTrue(b.contains("plus 1 more"))
        assertTrue(b.contains("Sunny")); assertTrue(b.contains("Rs 500")); assertTrue(b.contains("Battery is at 20"))
        assertTrue(LocalSkills.briefing(20, "x", emptyList(), null, null, null).contains("no open tasks"))
        assertTrue(LocalSkills.batteryText(10, false).contains("low")); assertFalse(LocalSkills.batteryText(80, true).contains("low"))
    }

    @Test fun expenseMathAndStorage() {
        val tz = TimeZone.getTimeZone("UTC")
        val day = 86_400_000L
        val now = 40 * day + 12 * 3_600_000L
        val list = listOf(Expense(now - 1000, 300.0, "Lunch"), Expense(now - 2000, 200.0, "lunch"), Expense(now - day, 100.0, "bus"), Expense(now - 20 * day, 999.0, "old"))
        assertEquals(500.0, Expenses.inPeriod(list, Period.TODAY, now, tz).sumOf { it.amount }, 0.0)
        assertEquals(100.0, Expenses.inPeriod(list, Period.YESTERDAY, now, tz).sumOf { it.amount }, 0.0)
        assertEquals(600.0, Expenses.inPeriod(list, Period.WEEK, now, tz).sumOf { it.amount }, 0.0)
        assertEquals(1599.0, Expenses.inPeriod(list, Period.ALL, now, tz).sumOf { it.amount }, 0.0)
        assertTrue(Expenses.summarize(list, Period.TODAY, now, tz).contains("Rs 500"))
        assertTrue(Expenses.summarize(list, Period.TODAY, now, tz).contains("Biggest: lunch"))
        assertEquals("Nothing recorded today.", Expenses.summarize(emptyList(), Period.TODAY, now, tz))
        assertEquals(list, Expenses.fromJson(Expenses.toJson(list)))
        assertTrue(Expenses.fromJson("garbage").isEmpty())
        assertTrue(Expenses.toCsv(list).startsWith("date,amount,note\n"))
    }

    @Test fun focusKeepsYetiCalm() {
        val b = YetiBrain(java.util.Random(3), sleepAfterMs = 1_000_000L); val p = Pose()
        var now = 1000L
        repeat(4000) {
            b.tick(0.05f, now, PetInput(focus = true, sleepWhenIdle = false), p); now += 50
            assertTrue("${b.act}", b.act == Act.IDLE || b.act == Act.SIT || b.act == Act.HAPPY || b.act == Act.WAKE || b.act == Act.THINK)
        }
    }
}
