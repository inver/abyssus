package net.nevinsky.abyssus.uitesting.headless

import net.nevinsky.abyssus.uitesting.LifecycleHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class LifecycleHarnessTest {
    @Test
    fun identicalTextHasAnEmptyDiff() {
        assertTrue(LifecycleHarness.lineDiff("a\nb\n", "a\nb\n").isEmpty)
    }

    @Test
    fun anInsertedBlockShowsOnlyItsLines() {
        val diff = LifecycleHarness.lineDiff("{\n  \"a\": 1\n}", "{\n  \"a\": 1,\n  \"b\": 2\n}")
        assertEquals(listOf("  \"a\": 1"), diff.removed)
        assertEquals(listOf("  \"a\": 1,", "  \"b\": 2"), diff.added)
    }

    @Test
    fun repeatedLinesAreCounted() {
        val diff = LifecycleHarness.lineDiff("x\nx\n", "x\nx\nx\n")
        assertEquals(listOf("x"), diff.added)
        assertTrue(diff.removed.isEmpty())
    }

    @Test
    fun sameExceptIgnoresLayoutAndTheNamedValueOnly() {
        val before = """{"format":"abyssus","ecs":{"0":{"n":1.000}}}"""
        val withNew = """{
          "format": "abyssus",
          "ecs": { "0": { "n": 1.000 }, "11": { "n": 2 } }
        }"""
        assertTrue(LifecycleHarness.sameExcept(before, withNew, "/ecs/11"))
        assertFalse("number text differs", LifecycleHarness.sameExcept(before, withNew.replace("1.000", "1.0"), "/ecs/11"))
        assertFalse(LifecycleHarness.sameExcept(before, withNew, "/ecs/12"))
    }

    @Test
    fun awaitStateReturnsWhenReachedAndNamesTheStageWhenNot() {
        var n = 0
        LifecycleHarness.awaitState("count reaches 3", 3, Duration.ofSeconds(5)) { ++n }
        val error = runCatching {
            LifecycleHarness.awaitState("never", "yes", Duration.ofMillis(400)) { "no" }
        }.exceptionOrNull()
        assertTrue(error is AssertionError)
        assertTrue(error!!.message, error.message!!.contains("never") && error.message!!.contains("<yes>") && error.message!!.contains("<no>"))
    }
}
