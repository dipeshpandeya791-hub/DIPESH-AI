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

class MoreSkillsTest {
    private fun rnd(seed: Long = 1) = java.util.Random(seed)
    private fun ans(s: String) = (LocalSkills.parse(s, rnd()) as? Skill.Answer)

    @Test fun shoppingList() {
        assertEquals(listOf("milk", "rice"), (LocalSkills.parse("add milk and rice to my shopping list") as Skill.ShopAdd).items)
        assertEquals(listOf("eggs", "bread", "tea"), (LocalSkills.parse("please add eggs, bread and tea to the grocery list") as Skill.ShopAdd).items)
        assertEquals(listOf("sugar"), (LocalSkills.parse("shopping list add some sugar") as Skill.ShopAdd).items)
        assertEquals("milk", (LocalSkills.parse("remove milk from my shopping list") as Skill.ShopRemove).item)
        assertTrue(LocalSkills.parse("what's on my shopping list") is Skill.ShopShow)
        assertTrue(LocalSkills.parse("shopping list") is Skill.ShopShow)
        assertTrue(LocalSkills.parse("clear my shopping list") is Skill.ShopClear)
        assertNull(LocalSkills.parse("add a reminder to call mom"))
        assertNull(LocalSkills.parse("add 2 and 3"))
    }

    @Test fun shoppingMaths() {
        val a = Shopping.add(listOf("milk"), listOf("Milk", "rice"))
        assertEquals(listOf("milk", "rice"), a)
        assertEquals(listOf("milk") to true, Shopping.remove(a, "rice"))
        assertEquals(listOf("milk", "rice") to false, Shopping.remove(a, "gold"))
        assertEquals("Your shopping list is empty.", Shopping.describe(emptyList()))
        assertEquals("Your shopping list has 3 items: a, b and c.", Shopping.describe(listOf("a", "b", "c")))
    }

    @Test fun funSkills() {
        assertTrue(ans("flip a coin")!!.reply in listOf("Heads!", "Tails!"))
        assertTrue(Regex("You rolled a [1-6]\\.").matches(ans("roll a dice")!!.reply))
        assertTrue(Regex("You rolled a ([1-9]|1\\d|20)\\.").matches(ans("roll a 20 sided die")!!.reply))
        val n = Regex("(\\d+)").find(ans("random number between 5 and 8")!!.reply)!!.value.toInt()
        assertTrue(n in 5..8)
        val n2 = Regex("(\\d+)").find(ans("random number between 8 and 5")!!.reply)!!.value.toInt()
        assertTrue(n2 in 5..8)
        assertTrue(ans("pick between pizza or burger")!!.reply in listOf("I pick pizza.", "I pick burger."))
        val j = ans("tell me a joke")!!; assertEquals("laugh", j.event); assertTrue(j.reply.length > 20)
        assertNull(LocalSkills.parse("what is a coin"))
    }

    @Test fun nepaliAndHindiPhrases() {
        assertEquals(300, (LocalSkills.parse("५ मिनेट टाइमर") as Skill.Timer).seconds)
        assertEquals(600, (LocalSkills.parse("टाइमर 10 मिनट") as Skill.Timer).seconds)
        val f = LocalSkills.parse("२५ मिनेट फोकस") as Skill.Timer
        assertTrue(f.focus); assertEquals(1500, f.seconds)
        assertEquals(7200, (LocalSkills.parse("2 घण्टा टाइमर") as Skill.Timer).seconds)
        assertTrue(LocalSkills.parse("सुप्रभात") is Skill.Briefing)
        assertTrue(LocalSkills.parse("शुभ प्रभात") is Skill.Briefing)
        assertTrue(LocalSkills.parse("बैटरी कति छ") is Skill.Battery)
        assertTrue(LocalSkills.parse("बैटरी कितनी है") is Skill.Battery)
    }

    @Test fun levelsAndXp() {
        assertEquals(1, Progress.level(0)); assertEquals(1, Progress.level(39)); assertEquals(2, Progress.level(40)); assertEquals(3, Progress.level(160))
        assertEquals(0f, Progress.fraction(0), 1e-6f); assertEquals(0.5f, Progress.fraction(20), 1e-6f)
        assertEquals("Snow Pup", Progress.title(1)); assertEquals("Yeti Legend", Progress.title(99))
        var (s, up) = Progress.record(ProgressState(), Ev.TASK, 100L)
        assertEquals(10, s.xp); assertEquals(1, s.tasks); assertFalse(up)
        s = ProgressState(xp = 35); val r = Progress.record(s, Ev.FOCUS, 100L)
        assertTrue(r.second); assertEquals(1, r.first.focus)
    }

    @Test fun streaks() {
        var s = ProgressState()
        s = Progress.record(s, Ev.COMMAND, 10L).first; assertEquals(1, s.streak)
        s = Progress.record(s, Ev.COMMAND, 10L).first; assertEquals(1, s.streak)   // same day
        s = Progress.record(s, Ev.COMMAND, 11L).first; assertEquals(2, s.streak)
        s = Progress.record(s, Ev.COMMAND, 12L).first; assertEquals(3, s.streak)
        assertTrue(Progress.earned(s).any { it.id == "streak_3" })
        s = Progress.record(s, Ev.COMMAND, 20L).first; assertEquals(1, s.streak)   // gap resets
    }

    @Test fun badgesAndStorageFormat() {
        val s = ProgressState(xp = 500, tasks = 10, focus = 1, commands = 60, expenses = 5, streak = 7)
        val ids = Progress.earned(s).map { it.id }
        assertTrue(ids.containsAll(listOf("first_task", "tasks_10", "focus_1", "talker_50", "saver_5", "streak_3", "streak_7")))
        assertFalse(ids.contains("focus_10"))
        assertEquals(s, Progress.fromJson(Progress.toJson(s)))
        assertEquals(ProgressState(), Progress.fromJson("garbage"))
    }

    @Test fun yetiLaughsOnEvent() {
        val b = YetiBrain(java.util.Random(1)); b.tick(0.05f, 1000, PetInput(), Pose())
        b.react("laugh", 9L); assertEquals(Act.LAUGH, b.act)
    }
}
