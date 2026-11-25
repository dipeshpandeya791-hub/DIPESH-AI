package com.dipesh.ai

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ScriptableObject

/** Runs JavaScript on the phone with the open-source Rhino engine: no network, no file access, 10 s limit. */
object JsSandbox {
    class Console {
        val sb = StringBuilder()
        fun log(o: Any?) { sb.append(Context.toString(o)).append('\n') }
    }

    private class Factory : ContextFactory() {
        @Volatile var deadline = 0L
        override fun makeContext(): Context {
            val c = super.makeContext()
            c.optimizationLevel = -1 // interpreted mode: required on Android
            c.languageVersion = Context.VERSION_ES6
            c.instructionObserverThreshold = 20000
            return c
        }
        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            if (System.currentTimeMillis() > deadline) throw Error("Script timed out after 10 seconds")
        }
    }

    private val factory = Factory()

    fun run(code: String): String {
        factory.deadline = System.currentTimeMillis() + 10_000
        return try {
            factory.call { cx ->
                val scope = cx.initSafeStandardObjects()
                val console = Console()
                ScriptableObject.putProperty(scope, "console", Context.javaToJS(console, scope))
                val result = cx.evaluateString(scope, code, "script", 1, null)
                val printed = console.sb.toString().trimEnd()
                val value = if (result == null || result === Context.getUndefinedValue()) "" else Context.toString(result)
                listOf(printed, if (value.isNotEmpty()) "=> $value" else "").filter { it.isNotEmpty() }
                    .joinToString("\n").ifBlank { "(no output)" }.take(6000)
            }
        } catch (e: org.mozilla.javascript.RhinoException) {
            "JavaScript error: ${e.details()} (line ${e.lineNumber()}). Fix the code and run again."
        } catch (e: Throwable) {
            "JavaScript error: ${e.message}. Fix the code and run again."
        }
    }
}
