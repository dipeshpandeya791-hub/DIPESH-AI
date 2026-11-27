package com.dipesh.ai.studio

/** The Yeti's growth: experience points, levels, a daily streak and badges earned by using the assistant. Pure logic. */
data class ProgressState(val xp: Int = 0, val tasks: Int = 0, val focus: Int = 0, val commands: Int = 0, val expenses: Int = 0, val streak: Int = 0, val lastDay: Long = -1L)

enum class Ev(val xp: Int) { TASK(10), FOCUS(25), TIMER(5), COMMAND(1), EXPENSE(2) }

class Badge(val id: String, val icon: String, val name: String, val desc: String, val test: (ProgressState) -> Boolean)

object Progress {
    /** Level 1 at 0 XP, level 2 at 40, level 3 at 160, level 4 at 360 ... */
    fun level(xp: Int): Int = Math.sqrt(xp / 40.0).toInt() + 1
    fun xpFor(level: Int): Int = 40 * (level - 1) * (level - 1)
    fun fraction(xp: Int): Float { val l = level(xp); val a = xpFor(l); val b = xpFor(l + 1); return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f) }

    val titles = listOf("Snow Pup", "Frost Cub", "Ice Explorer", "Glacier Guard", "Peak Walker", "Blizzard Buddy", "Summit Sage", "Yeti Legend")
    fun title(level: Int) = titles[(level - 1).coerceIn(0, titles.size - 1)]

    val badges = listOf(
        Badge("first_task", "✅", "First task", "Finish a task") { it.tasks >= 1 },
        Badge("tasks_10", "🏅", "Task master", "Finish 10 tasks") { it.tasks >= 10 },
        Badge("focus_1", "🎯", "In the zone", "Finish a focus session") { it.focus >= 1 },
        Badge("focus_10", "🧘", "Deep focus", "Finish 10 focus sessions") { it.focus >= 10 },
        Badge("talker_50", "🗣", "Chatterbox", "Give 50 commands") { it.commands >= 50 },
        Badge("saver_5", "💰", "Money minded", "Log 5 expenses") { it.expenses >= 5 },
        Badge("streak_3", "🔥", "3-day streak", "Use the assistant 3 days in a row") { it.streak >= 3 },
        Badge("streak_7", "🌟", "Week warrior", "Use the assistant 7 days in a row") { it.streak >= 7 }
    )

    fun earned(s: ProgressState): List<Badge> = badges.filter { it.test(s) }

    /** Records an event on [epochDay] (days since 1970 in the user's time zone). Returns the new state and whether the level went up. */
    fun record(s: ProgressState, ev: Ev, epochDay: Long): Pair<ProgressState, Boolean> {
        val streak = when {
            s.lastDay == epochDay -> s.streak.coerceAtLeast(1)
            s.lastDay == epochDay - 1 -> s.streak + 1
            else -> 1
        }
        val n = s.copy(
            xp = s.xp + ev.xp, streak = streak, lastDay = epochDay,
            tasks = s.tasks + if (ev == Ev.TASK) 1 else 0, focus = s.focus + if (ev == Ev.FOCUS) 1 else 0,
            commands = s.commands + if (ev == Ev.COMMAND) 1 else 0, expenses = s.expenses + if (ev == Ev.EXPENSE) 1 else 0
        )
        return n to (level(n.xp) > level(s.xp))
    }

    fun toJson(s: ProgressState): String = org.json.JSONObject().put("xp", s.xp).put("tasks", s.tasks).put("focus", s.focus)
        .put("commands", s.commands).put("expenses", s.expenses).put("streak", s.streak).put("lastDay", s.lastDay).toString()

    fun fromJson(j: String): ProgressState = try {
        val o = org.json.JSONObject(j)
        ProgressState(o.optInt("xp"), o.optInt("tasks"), o.optInt("focus"), o.optInt("commands"), o.optInt("expenses"), o.optInt("streak"), o.optLong("lastDay", -1L))
    } catch (_: Exception) { ProgressState() }
}

class ProgressStore(private val ctx: android.content.Context) {
    private val p get() = ctx.getSharedPreferences("progress", android.content.Context.MODE_PRIVATE)
    fun load(): ProgressState = Progress.fromJson(p.getString("state", "{}") ?: "{}")
    fun save(s: ProgressState) { p.edit().putString("state", Progress.toJson(s)).apply() }
}
