package com.dipesh.ai.studio

import java.util.Calendar
import java.util.TimeZone

/**
 * Instant, offline "skills" understood from plain speech before the AI is asked: calculator, unit conversion, timers and
 * focus sessions, a spoken-expense tracker, battery status and a daily briefing. They work with no internet and no API key,
 * and cost no AI quota. Pure logic (no Android classes) so it is unit-tested.
 */
object LocalSkills {
    enum class Period { TODAY, YESTERDAY, WEEK, MONTH, ALL }

    sealed class Skill {
        class Answer(val reply: String) : Skill()
        class Timer(val seconds: Int, val focus: Boolean) : Skill()
        object CancelTimer : Skill()
        object TimerLeft : Skill()
        class Spend(val amount: Double, val note: String) : Skill()
        class SpendSummary(val period: Period) : Skill()
        object Briefing : Skill()
        object Battery : Skill()
    }

    fun parse(text: String): Skill? {
        val t = text.trim().lowercase().trimEnd('.', '!', '?', ',', ';').replace(Regex("\\s+"), " ")
        if (t.length < 3) return null
        timer(t)?.let { return it }
        spending(t)?.let { return it }
        if (Regex("^(good (morning|afternoon|evening)( dipesh)?|(my )?(daily|morning|evening) (briefing|brief|summary)|brief me|what'?s my day|my day|today'?s (briefing|summary|plan))$").containsMatchIn(t)) return Skill.Briefing
        if (Regex("battery (level|percent|percentage|status|left|remaining)|how much battery|charge (left|remaining)|is (my )?(phone )?charging").containsMatchIn(t)) return Skill.Battery
        convert(t)?.let { return Skill.Answer(it) }
        calc(t)?.let { return Skill.Answer(it) }
        return null
    }

    // ------------------------------------------------------------ timers & focus
    private val words = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "fifteen" to 15.0, "twenty" to 20.0, "thirty" to 30.0, "forty five" to 45.0, "forty" to 40.0,
        "fifty" to 50.0, "sixty" to 60.0, "half" to 0.5, "half an" to 0.5, "quarter" to 0.25)

    private fun number(s: String): Double? = s.trim().toDoubleOrNull() ?: words[s.trim()]

    private fun timer(t: String): Skill? {
        if (Regex("(cancel|stop|clear|end|turn off|abort).{0,14}(timer|countdown|focus|pomodoro)").containsMatchIn(t)) return Skill.CancelTimer
        if (Regex("(how much time|time left|how long|how many minutes).{0,18}(timer|left|remaining|focus)|timer status|focus status").containsMatchIn(t)) return Skill.TimerLeft
        val focus = Regex("\\b(focus|pomodoro|study session|deep work)\\b").containsMatchIn(t)
        val timer = Regex("\\b(timer|countdown)\\b").containsMatchIn(t)
        if (!focus && !timer) return null
        if (Regex("^(what|who|why|how does|explain|tell me about)\\b").containsMatchIn(t) && !Regex("\\d").containsMatchIn(t)) return null
        val m = Regex("(\\d+(?:\\.\\d+)?|half an|forty five|[a-z]+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\\b").find(t)
        val seconds: Int? = m?.let {
            val n = number(it.groupValues[1]) ?: return@let null
            val unit = it.groupValues[2]
            (n * when { unit.startsWith("h") -> 3600.0; unit.startsWith("m") -> 60.0; else -> 1.0 }).toInt()
        }
        val s = seconds ?: if (focus) 25 * 60 else return null
        if (s !in 1..86_400) return null
        return Skill.Timer(s, focus)
    }

    fun durationText(seconds: Int): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h hour" + if (h == 1) "" else "s")
        if (m > 0) parts.add("$m minute" + if (m == 1) "" else "s")
        if (s > 0 && h == 0) parts.add("$s second" + if (s == 1) "" else "s")
        return parts.joinToString(" and ").ifEmpty { "0 seconds" }
    }

    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    // ------------------------------------------------------------ spending
    private fun spending(t: String): Skill? {
        if (Regex("(how much|what).{0,25}(spent|spend|expenses?)|(show|read|tell|give).{0,14}(my )?(expenses?|spending)|(expense|spending) (report|summary|total)").containsMatchIn(t)) {
            val p = when {
                t.contains("yesterday") -> Period.YESTERDAY
                t.contains("week") -> Period.WEEK
                t.contains("month") -> Period.MONTH
                Regex("\\b(all|ever|total|overall)\\b").containsMatchIn(t) && !t.contains("today") -> Period.ALL
                else -> Period.TODAY
            }
            return Skill.SpendSummary(p)
        }
        val m = Regex("^(?:i |i've |ive )?(?:just )?(?:spent|paid|spend|bought for|expense of)\\s+(?:rs\\.?\\s*|npr\\s*|₹\\s*|\\$\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(?:rs\\.?|rupees?|npr|dollars?|usd)?\\s*(?:on|for|in|at|to)?\\s*(.*)$").find(t) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (amount <= 0.0 || amount > 1e9) return null
        val note = m.groupValues[2].trim().ifEmpty { "misc" }.removePrefix("the ").removePrefix("a ").take(40)
        return Skill.Spend(amount, note)
    }

    // ------------------------------------------------------------ unit conversion
    private val unitAlias: Map<String, String> = buildMap {
        fun add(code: String, vararg names: String) = names.forEach { put(it, code) }
        add("km", "km", "kilometer", "kilometre", "kilometers", "kilometres", "kms"); add("m", "m", "meter", "metre", "meters", "metres")
        add("cm", "cm", "centimeter", "centimetre", "centimeters", "centimetres"); add("mm", "mm", "millimeter", "millimetre", "millimeters", "millimetres")
        add("mi", "mi", "mile", "miles"); add("yd", "yd", "yard", "yards"); add("ft", "ft", "foot", "feet"); add("in", "inch", "inches")
        add("kg", "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms"); add("g", "g", "gram", "grams"); add("mg", "mg", "milligram", "milligrams")
        add("lb", "lb", "lbs", "pound", "pounds"); add("oz", "oz", "ounce", "ounces")
        add("l", "l", "liter", "litre", "liters", "litres"); add("ml", "ml", "milliliter", "millilitre", "milliliters", "millilitres")
        add("gal", "gal", "gallon", "gallons"); add("cup", "cup", "cups")
        add("c", "c", "celsius", "centigrade"); add("f", "f", "fahrenheit"); add("k", "kelvin")
        add("mph", "mph", "miles per hour"); add("km/h", "km/h", "kmh", "kph", "kilometers per hour", "kilometres per hour")
        add("m/s", "m/s", "meters per second", "metres per second")
        add("gb", "gb", "gigabyte", "gigabytes"); add("mb", "mb", "megabyte", "megabytes"); add("kb", "kb", "kilobyte", "kilobytes")
    }

    private fun fmt(v: Double): String {
        val r = Math.round(v * 10_000.0) / 10_000.0
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
    }

    private fun convert(t: String): String? {
        val m = Regex("^(?:convert |how (?:many|much) is |what is |what's )?(-?\\d+(?:\\.\\d+)?)\\s*([a-z/ ]+?)\\s+(?:to|in|into|equals? in|is how many)\\s+([a-z/ ]+)$").find(t) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val fromW = m.groupValues[2].trim(); val toW = m.groupValues[3].trim()
        val from = unitAlias[fromW] ?: return null; val to = unitAlias[toW] ?: return null
        return try { "${fmt(v)} $fromW is ${fmt(Units.convert(v, from, to))} $toW." } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------ calculator
    private fun calc(t: String): String? {
        var e = t.replace(Regex("^(what is|what's|whats|calculate|compute|solve|how much is|find|tell me)\\s+"), "").replace(",", "")
        e = e.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s*of\\s*"), "($1/100)*")
        e = e.replace(" multiplied by ", "*").replace(" divided by ", "/").replace(" to the power of ", "^").replace(" times ", "*")
            .replace(" plus ", "+").replace(" minus ", "-").replace(" into ", "*").replace(" over ", "/").replace(" upon ", "/")
        e = e.replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        if (!Regex("^[0-9+\\-*/^().\\s]+$").matches(e)) return null
        if (!Regex("\\d").containsMatchIn(e) || !Regex("[+\\-*/^]").containsMatchIn(e.trim().trimStart('-'))) return null
        return try {
            val v = Calc.eval(e)
            if (v.isNaN() || v.isInfinite()) "That can't be calculated." else "The answer is ${fmt(v)}."
        } catch (e: Exception) { if (e.message?.contains("zero", true) == true || e.message?.contains("finite", true) == true) "That can't be calculated." else null }
    }

    // ------------------------------------------------------------ battery & briefing text
    fun batteryText(percent: Int, charging: Boolean): String =
        "Battery is at $percent percent" + (if (charging) " and charging." else ".") +
            when { !charging && percent <= 15 -> " That's low, please plug in soon."; !charging && percent <= 30 -> " You may want to charge it soon."; else -> "" }

    fun briefing(hour: Int, dateText: String, openTasks: List<String>, weather: String?, battery: Int?, spentToday: Double?): String = buildString {
        append(when { hour < 12 -> "Good morning."; hour < 17 -> "Good afternoon."; else -> "Good evening." })
        append(" Today is ").append(dateText).append(". ")
        if (!weather.isNullOrBlank()) append(weather.trim().trimEnd('.')).append(". ")
        when {
            openTasks.isEmpty() -> append("You have no open tasks. ")
            else -> {
                val shown = openTasks.take(3)
                append("You have ${openTasks.size} open task" + (if (openTasks.size == 1) "" else "s") + ": ")
                append(if (shown.size == 1) shown[0] else shown.dropLast(1).joinToString(", ") + " and " + shown.last())
                append(if (openTasks.size > 3) ", plus ${openTasks.size - 3} more. " else ". ")
            }
        }
        if (spentToday != null && spentToday > 0) append("You have spent Rs ${fmt(spentToday)} so far today. ")
        if (battery != null && battery <= 30) append("Battery is at $battery percent, so charge it soon. ")
    }.trim()
}

/** Spoken-expense tracker data and maths. */
data class Expense(val ts: Long, val amount: Double, val note: String)

object Expenses {
    fun periodStart(p: LocalSkills.Period, now: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Long> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val today = c.timeInMillis
        val day = 86_400_000L
        return when (p) {
            LocalSkills.Period.TODAY -> today to Long.MAX_VALUE
            LocalSkills.Period.YESTERDAY -> (today - day) to today
            LocalSkills.Period.WEEK -> (today - 6 * day) to Long.MAX_VALUE
            LocalSkills.Period.MONTH -> { c.set(Calendar.DAY_OF_MONTH, 1); c.timeInMillis to Long.MAX_VALUE }
            LocalSkills.Period.ALL -> 0L to Long.MAX_VALUE
        }
    }

    fun inPeriod(list: List<Expense>, p: LocalSkills.Period, now: Long, tz: TimeZone = TimeZone.getDefault()): List<Expense> {
        val (from, to) = periodStart(p, now, tz)
        return list.filter { it.ts in from until to }
    }

    private fun money(v: Double): String { val r = Math.round(v * 100.0) / 100.0; return "Rs " + if (r == Math.floor(r)) r.toLong().toString() else r.toString() }
    private val label = mapOf(LocalSkills.Period.TODAY to "today", LocalSkills.Period.YESTERDAY to "yesterday", LocalSkills.Period.WEEK to "in the last 7 days",
        LocalSkills.Period.MONTH to "this month", LocalSkills.Period.ALL to "in total")

    fun summarize(list: List<Expense>, p: LocalSkills.Period, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val items = inPeriod(list, p, now, tz)
        if (items.isEmpty()) return "Nothing recorded ${label[p]}."
        val total = items.sumOf { it.amount }
        val top = items.groupBy { it.note.lowercase() }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
        return "You spent ${money(total)} ${label[p]} across ${items.size} entr${if (items.size == 1) "y" else "ies"}." +
            (if (top != null && items.size > 1) " Biggest: ${top.key}, ${money(top.value)}." else "")
    }

    fun toCsv(list: List<Expense>): String = buildString {
        append("date,amount,note\n")
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        list.sortedBy { it.ts }.forEach { append(f.format(java.util.Date(it.ts))).append(',').append(it.amount).append(',').append('"').append(it.note.replace("\"", "\"\"")).append("\"\n") }
    }

    fun toJson(list: List<Expense>): String {
        val a = org.json.JSONArray()
        list.forEach { a.put(org.json.JSONObject().put("t", it.ts).put("a", it.amount).put("n", it.note)) }
        return a.toString()
    }

    fun fromJson(s: String): List<Expense> = try {
        val a = org.json.JSONArray(s)
        (0 until a.length()).map { val o = a.getJSONObject(it); Expense(o.getLong("t"), o.getDouble("a"), o.optString("n", "misc")) }
    } catch (_: Exception) { emptyList() }
}

/** Keeps expenses in a small private preferences file. */
class ExpenseStore(private val ctx: android.content.Context) {
    private val p get() = ctx.getSharedPreferences("expenses", android.content.Context.MODE_PRIVATE)
    fun load(): List<Expense> = Expenses.fromJson(p.getString("list", "[]") ?: "[]")
    fun add(amount: Double, note: String, now: Long = System.currentTimeMillis()) {
        val list = (load() + Expense(now, amount, note)).takeLast(2000)
        p.edit().putString("list", Expenses.toJson(list)).apply()
    }
}
