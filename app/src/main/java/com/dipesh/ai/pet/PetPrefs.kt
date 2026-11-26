package com.dipesh.ai.pet

import android.content.Context

/** Yeti pet settings, stored in the same small preferences file the floating service reads. */
object PetPrefs {
    const val FILE = "widget"
    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Window width in dp for each size name. */
    val sizes = linkedMapOf("Small" to 84, "Medium" to 108, "Large" to 136)

    fun size(ctx: Context): String = p(ctx).getString("size", "Medium") ?: "Medium"
    fun setSize(ctx: Context, v: String) = p(ctx).edit().putString("size", v).apply()
    fun sizeDp(ctx: Context): Int = sizes[size(ctx)] ?: 108

    fun headphones(ctx: Context) = p(ctx).getBoolean("headphones", true)
    fun setHeadphones(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("headphones", v).apply()
    fun wander(ctx: Context) = p(ctx).getBoolean("wander", true)
    fun setWander(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("wander", v).apply()
    fun sleepy(ctx: Context) = p(ctx).getBoolean("sleepy", true)
    fun setSleepy(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("sleepy", v).apply()

    /** Keeps a window position fully on screen and below the status bar. Pure maths, unit-tested. */
    fun clamp(x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int, topInset: Int, bottomInset: Int): Pair<Int, Int> =
        x.coerceIn(0, maxOf(0, screenW - w)) to y.coerceIn(topInset, maxOf(topInset, screenH - h - bottomInset))
}
