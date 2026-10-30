package net.nevinsky.abyssus.uitesting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.lib.core.editor.document.SceneJson
import java.time.Duration

/**
 * Pure checks the lifecycle scenarios share, so each stage can say what changed and prove what did not: byte-level
 * line differences for text that must stay exactly as written, and a semantic comparison for what may be re-spelled.
 */
object LifecycleHarness {
    /** What a text edit removed and added, as lines; an unchanged line appears in neither. Order-insensitive but count-aware. */
    data class LineDiff(val removed: List<String>, val added: List<String>) {
        val isEmpty get() = removed.isEmpty() && added.isEmpty()
    }

    fun lineDiff(before: String, after: String): LineDiff {
        val remaining = before.lines().groupingBy { it }.eachCount().toMutableMap()
        val added = ArrayList<String>()
        for (line in after.lines()) {
            val count = remaining[line] ?: 0
            if (count > 0) remaining[line] = count - 1 else added += line
        }
        val removed = before.lines().filter { line ->
            val count = remaining[line] ?: 0
            (count > 0).also { if (it) remaining[line] = count - 1 }
        }
        return LineDiff(removed, added)
    }

    /**
     * True when [before] and [after] are the same JSON once the value at [pointer] (`/ecs/11`, a JSON Pointer) is left
     * out of both; numbers compare by their text, so `1.000` and `1.0` differ. Whitespace and key layout are ignored.
     */
    fun sameExcept(before: String, after: String, pointer: String): Boolean =
        withoutPointer(before, pointer) == withoutPointer(after, pointer)

    private fun withoutPointer(text: String, pointer: String): String {
        val root = SceneJson().parse(text)
        val parts = pointer.trim('/').split('/').filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) {
            var node: JsonNode? = root
            for (part in parts.dropLast(1)) node = node?.get(part)
            (node as? ObjectNode)?.remove(parts.last())
        }
        return SceneJson().compact(root)
    }

    /** Polls [observe] until it equals [expected], then returns; otherwise fails naming [stage] with both values. */
    fun <T> awaitState(stage: String, expected: T, timeout: Duration = Duration.ofSeconds(15), observe: () -> T) {
        val deadline = System.nanoTime() + timeout.toNanos()
        var seen: Result<T>
        do {
            seen = runCatching(observe)
            if (seen.getOrNull() == expected) return
            Thread.sleep(200)
        } while (System.nanoTime() < deadline)
        throw AssertionError("$stage: expected <$expected> within $timeout but observed <${seen.getOrElse { "error: $it" }}>")
    }
}
