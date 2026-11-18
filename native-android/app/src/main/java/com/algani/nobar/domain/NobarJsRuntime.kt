package com.algani.nobar.domain

import android.content.Context
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable

/** Executes portable NOBAR business rules in an embedded JavaScript runtime (no browser DOM). */
class NobarJsRuntime(context: Context) {
    private val scope: Scriptable
    init {
        val source = context.assets.open("js/nobar-core.js").bufferedReader().use { it.readText() }
        val cx = RhinoContext.enter()
        try {
            cx.optimizationLevel = -1
            scope = cx.initStandardObjects()
            cx.evaluateString(scope, source, "nobar-core.js", 1, null)
        } finally { RhinoContext.exit() }
    }
    fun call(function: String, vararg args: Any?): Any? {
        val cx = RhinoContext.enter()
        return try {
            cx.optimizationLevel = -1
            val core = scope.get("NobarCore", scope) as Scriptable
            val fn = core.get(function, core) as org.mozilla.javascript.Function
            fn.call(cx, scope, core, args.map { it ?: org.mozilla.javascript.Undefined.instance }.toTypedArray())
                .let { org.mozilla.javascript.Context.jsToJava(it, Any::class.java) }
        } finally { RhinoContext.exit() }
    }
}
