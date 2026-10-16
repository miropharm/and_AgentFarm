package com.muvusoft.agentfarm

import androidx.compose.ui.test.junit4.ComposeTestRule
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

/**
 * What a coroutine of the composition threw while a test waited. The compose rule keeps such an error until a
 * wait ends idle, and the composition it stopped never idles again: the wait fails as "compose being busy" and
 * the error itself is never shown (on a phone the same error ends the app). Test code only: read off the rule's
 * own objects by reflection, so a later ui-test version that moves it yields null, never a wrong answer.
 */
object Hidden {
    fun of(rule: ComposeTestRule): Throwable? = walk(rule, Collections.newSetFromMap(IdentityHashMap()), 0)

    private fun walk(o: Any, seen: MutableSet<Any>, depth: Int): Throwable? {
        if (depth > MAX_DEPTH || !seen.add(o)) return null
        var c: Class<*>? = o.javaClass
        while (c != null && c.name.startsWith(TEST_PACKAGE)) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers) || f.type.isPrimitive) continue
                val v = runCatching { f.isAccessible = true; f.get(o) }.getOrNull() ?: continue
                if (v is Throwable) return v
                if (v is Collection<*>) v.firstOrNull { it is Throwable }?.let { return it as Throwable }
                if (v.javaClass.name.startsWith(TEST_PACKAGE)) walk(v, seen, depth + 1)?.let { return it }
            }
            c = c.superclass
        }
        return null
    }

    private const val TEST_PACKAGE = "androidx.compose.ui.test"
    private const val MAX_DEPTH = 4
}
