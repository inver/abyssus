package net.nevinsky.abyssus.lib.core.editor.util

import com.badlogic.gdx.math.Vector3
import org.junit.Assert.*
import org.junit.Test

class UtilTest {
    @Test
    fun aFreeNameIsKept() = assertEquals("tree", Util.uniqueName("tree") { false })

    @Test
    fun aTakenNameGetsTheNextFreeNumberStartingAtTwo() =
        assertEquals("tree-3", Util.uniqueName("tree") { it in setOf("tree", "tree-2") })

    @Test
    fun theExtensionStaysLastAndTheSeparatorCanBeChosen() {
        assertEquals("a-2.png", Util.uniqueName("a", ".png") { it == "a.png" })
        assertEquals("mat_2", Util.uniqueName("mat", separator = "_") { it == "mat" })
    }

    @Test
    fun theCallerDecidesWhatTakenMeans() {
        val used = setOf("textures/A.png")
        assertEquals("textures/a-2.png", Util.uniqueName("textures/a", ".png") { c -> used.any { it.equals(c, ignoreCase = true) } })
    }

    @Test
    fun aVectorIsFiniteOnlyWhenEveryCoordinateIs() {
        assertTrue(Util.isFinite(Vector3(1f, -2f, 3f)))
        assertFalse(Util.isFinite(Vector3(Float.NaN, 0f, 0f)))
        assertFalse(Util.isFinite(Vector3(0f, Float.POSITIVE_INFINITY, 0f)))
        assertFalse(Util.isFinite(Vector3(0f, 0f, Float.NEGATIVE_INFINITY)))
    }
}
