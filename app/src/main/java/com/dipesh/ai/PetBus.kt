package com.dipesh.ai

/** Lets the floating Yeti read the assistant's state and react to it, without holding the view model. */
object WidgetBus {
    @Volatile var provider: (() -> String)? = null
    @Volatile var levelProvider: (() -> Float)? = null
    /** Set by the view model; returns true when the tap was handled inside the running app (no screen switch needed). */
    @Volatile var onTap: (() -> Boolean)? = null
    /** Loudness 0..1 of what the assistant is saying right now (natural voice); 0 when unknown. */
    @Volatile var mouth: Float = 0f
    /** True while a focus session runs: the Yeti sits quietly instead of wandering. */
    @Volatile var focus: Boolean = false
    @Volatile var event: String = ""
    @Volatile var eventAt: Long = 0L

    fun status(): String = try { provider?.invoke() ?: "IDLE" } catch (_: Exception) { "IDLE" }
    fun level(): Float = try { levelProvider?.invoke() ?: 0f } catch (_: Exception) { 0f }

    /** "success", "error" or "celebrate"; the pet reacts once. */
    fun post(name: String) { event = name; eventAt = System.currentTimeMillis() }
}
