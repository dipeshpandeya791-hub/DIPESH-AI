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
        class Answer(val reply: String, val event: String? = null) : Skill()
        class ShopAdd(val items: List<String>) : Skill()
        class ShopRemove(val item: String) : Skill()
        object ShopShow : Skill()
        object ShopClear : Skill()
        class Timer(val seconds: Int, val focus: Boolean) : Skill()
        object CancelTimer : Skill()
        object TimerLeft : Skill()
        class Spend(val amount: Double, val note: String) : Skill()
        class SpendSummary(val period: Period) : Skill()
        object Briefing : Skill()
        object Battery : Skill()
    }

    private fun normalise(text: String): String {
        // Devanagari digits -> 0-9 so Nepali and Hindi numbers work in every pattern
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(if (ch in '\u0966'..'\u096F') ('0' + (ch - '\u0966')) else ch)
        return sb.toString().trim().lowercase().trimEnd('.', '!', '?', ',', ';', '।').replace(Regex("\\s+"), " ")
    }

    fun parse(text: String, rnd: java.util.Random = java.util.Random()): Skill? {
        val t = normalise(text)
        if (t.length < 3) return null
        shopping(t)?.let { return it }
        fun2(t, rnd)?.let { return it }
        timer(t)?.let { return it }
        spending(t)?.let { return it }
        if (Regex("^(सुप्रभात|शुभ प्रभात|शुभ दिन|शुभ संध्या|शुभ साँझ|good (morning|afternoon|evening)( dipesh)?|(my )?(daily|morning|evening) (briefing|brief|summary)|brief me|what'?s my day|my day|today'?s (briefing|summary|plan))$").containsMatchIn(t)) return Skill.Briefing
        if (Regex("battery (level|percent|percentage|status|left|remaining|kati)|how much battery|charge (left|remaining)|is (my )?(phone )?charging|(बैटरी|ब्याट्री|बैट्री).{0,12}(कति|कितनी|कितना|लेवल|प्रतिशत|कितने)").containsMatchIn(t)) return Skill.Battery
        convert(t)?.let { return Skill.Answer(it) }
        calc(t)?.let { return Skill.Answer(it) }
        return null
    }

    // ------------------------------------------------------------ shopping list
    private fun splitItems(raw: String): List<String> =
        raw.split(Regex("\\s*(?:,|&|\\band\\b|\\bplus\\b)\\s*")).map { it.trim().removePrefix("some ").removePrefix("a ").removePrefix("an ").removePrefix("the ").trim() }.filter { it.isNotEmpty() }.map { it.take(40) }.take(20)

    private fun shopping(t: String): Skill? {
        val list = "(?:my |the |our )?(?:shopping|grocery|groceries|buy|to-?buy)\\s*list"
        if (Regex("^(?:please )?(?:clear|empty|delete|reset)\\s+$list$").containsMatchIn(t)) return Skill.ShopClear
        Regex("^(?:please )?(?:remove|delete|cross off|check off|tick off|got|bought)\\s+(.+?)\\s+(?:from|off|on)\\s+$list$").find(t)?.let { return Skill.ShopRemove(it.groupValues[1].trim()) }
        Regex("^(?:please )?(?:add|put|buy|get me|i need)\\s+(.+?)\\s+(?:to|on|in|onto)\\s+$list$").find(t)?.let { m -> splitItems(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { return Skill.ShopAdd(it) } }
        Regex("^(?:please )?$list\\s+(?:add|put)\\s+(.+)$").find(t)?.let { m -> splitItems(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { return Skill.ShopAdd(it) } }
        if (Regex("^(?:what'?s|what is|show|read|tell me|open|check)?\\s*(?:what'?s on |what is on |me )?$list$|(?:what'?s|what is|what do i have).{0,14}$list").containsMatchIn(t)) return Skill.ShopShow
        return null
    }

    // ------------------------------------------------------------ small fun skills
    private val jokes = listOf(
        "Why don't scientists trust atoms? Because they make up everything.",
        "I told my phone I needed space. Now it won't stop showing me ads for rockets.",
        "Why did the computer go to the doctor? It had a virus.",
        "What do you call a bear with no teeth? A gummy bear.",
        "Why was the math book sad? It had too many problems.",
        "I would tell you a construction joke, but I'm still working on it.",
        "Why did the Yeti cross the road? To prove he was not a myth.",
        "What did the router say to the modem? You complete me, but you're always slow."
    )

    private fun fun2(t: String, rnd: java.util.Random): Skill? {
        if (Regex("^(?:please )?(?:flip|toss) (?:a )?coin$|^heads or tails$").containsMatchIn(t)) return Skill.Answer(if (rnd.nextBoolean()) "Heads!" else "Tails!")
        Regex("^(?:please )?roll (?:a |an )?(?:(\\d{1,3})[- ]?sided )?(?:dice|die)$").find(t)?.let {
            val sides = it.groupValues[1].toIntOrNull()?.coerceIn(2, 1000) ?: 6
            return Skill.Answer("You rolled a ${rnd.nextInt(sides) + 1}.")
        }
        Regex("^(?:pick |give me |choose )?(?:a )?random number(?: between (\\d+) and (\\d+))?$").find(t)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: 1; val b = it.groupValues[2].toIntOrNull() ?: 100
            val lo = minOf(a, b); val hi = maxOf(a, b)
            return Skill.Answer("Your number is ${lo + rnd.nextInt(hi - lo + 1)}.")
        }
        Regex("^(?:please )?(?:pick|choose|decide) (?:between )?(.+?) (?:or|and) (.+)$").find(t)?.let {
            val a = it.groupValues[1].trim(); val b = it.groupValues[2].trim()
            if (a.length in 1..40 && b.length in 1..40 && !a.contains(" or ") ) return Skill.Answer("I pick ${if (rnd.nextBoolean()) a else b}.")
        }
        if (Regex("^(?:please )?(?:tell me |say |crack )?(?:a |another )?joke$|^make me laugh$").containsMatchIn(t)) return Skill.Answer(jokes[rnd.nextInt(jokes.size)], "laugh")
        return null
    }

    // ------------------------------------------------------------ timers & focus
    private val words = mapOf("a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0,
        "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "fifteen" to 15.0, "twenty" to 20.0, "thirty" to 30.0, "forty five" to 45.0, "forty" to 40.0,
        "fifty" to 50.0, "sixty" to 60.0, "half" to 0.5, "half an" to 0.5, "quarter" to 0.25)

    private fun number(s: String): Double? = s.trim().toDoubleOrNull() ?: words[s.trim()]

    private fun timer(t: String): Skill? {
        if (Regex("(cancel|stop|clear|end|turn off|abort).{0,14}(timer|countdown|focus|pomodoro|टाइमर)").containsMatchIn(t)) return Skill.CancelTimer
        if (Regex("(how much time|time left|how long|how many minutes).{0,18}(timer|left|remaining|focus)|timer status|focus status").containsMatchIn(t)) return Skill.TimerLeft
        val focus = Regex("(focus|pomodoro|study session|deep work|फोकस)").containsMatchIn(t)
        val timer = Regex("(timer|countdown|टाइमर|टाईमर|taimar)").containsMatchIn(t)
        if (!focus && !timer) return null
        if (Regex("^(what|who|why|how does|explain|tell me about)\\b").containsMatchIn(t) && !Regex("\\d").containsMatchIn(t)) return null
        val m = Regex("(\\d+(?:\\.\\d+)?|half an|forty five|[a-z]+)\\s*(hours?|hrs?|घण्टा|घंटा|घण्टे|ghanta|minutes?|mins?|मिनेट|मिनट|मिनिट|minet|seconds?|secs?|सेकेन्ड|सेकंड)").find(t)
        val seconds: Int? = m?.let {
            val n = number(it.groupValues[1]) ?: return@let null
            val unit = it.groupValues[2]
            (n * when { unit.startsWith("h") || unit.startsWith("घ") || unit.startsWith("g") -> 3600.0; unit.startsWith("m") || unit.startsWith("म") -> 60.0; else -> 1.0 }).toInt()
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

/** Shopping list maths (the phone-side storage is [ShoppingStore]). */
object Shopping {
    fun add(list: List<String>, items: List<String>): List<String> {
        val out = list.toMutableList()
        items.forEach { i -> if (out.none { it.equals(i, true) }) out.add(i) }
        return out
    }
    fun remove(list: List<String>, item: String): Pair<List<String>, Boolean> {
        val key = item.trim().lowercase()
        val hit = list.firstOrNull { it.equals(key, true) } ?: list.firstOrNull { it.lowercase().contains(key) || key.contains(it.lowercase()) }
        return if (hit == null) list to false else list.filter { it != hit } to true
    }
    fun describe(list: List<String>): String = when {
        list.isEmpty() -> "Your shopping list is empty."
        list.size == 1 -> "Your shopping list has one item: ${list[0]}."
        else -> "Your shopping list has ${list.size} items: " + list.dropLast(1).joinToString(", ") + " and " + list.last() + "."
    }
}

class ShoppingStore(private val ctx: android.content.Context) {
    private val p get() = ctx.getSharedPreferences("shopping", android.content.Context.MODE_PRIVATE)
    fun load(): List<String> = try { val a = org.json.JSONArray(p.getString("items", "[]")); (0 until a.length()).map { a.getString(it) } } catch (_: Exception) { emptyList() }
    fun save(list: List<String>) { p.edit().putString("items", org.json.JSONArray(list.take(200)).toString()).apply() }
}
