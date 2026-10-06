/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.scene

import net.nevinsky.abyssus.lib.core.editor.scene.AssetRevisionTracker
import net.nevinsky.abyssus.lib.core.editor.scene.ProjectRevisions
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AssetDependencyRevisionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val tracker = AssetRevisionTracker(JsonProcessor())
    private val assets get() = File(tmp.root, "assets")

    private fun write(path: String, text: String, modified: Long? = null): File =
        File(assets, path).apply { parentFile.mkdirs(); writeText(text); modified?.let { setLastModified(it) } }

    private fun terrain(name: String, vararg splat: Pair<String, String>, size: Int = 100) {
        val refs = splat.joinToString("") { ",\"${it.first}\":\"${it.second}\"" }
        write("$name/terrain.data", "xxxx", 1000)
        write("$name/meta.json", """{"format":"abyssus","formatVersion":1,"uuid":"t-$name","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":$size,"uv":1.0$refs}}""")
    }

    private fun texture(name: String, uuid: String, image: String = "a.png", bytes: String = "png", modified: Long = 1000) {
        write("$name/$image", bytes, modified)
        write("$name/meta.json", """{"format":"abyssus","formatVersion":1,"uuid":"$uuid","type":"TEXTURE","additional":{"file":"$image"}}""")
    }

    private fun snap() = tracker.snapshot(assets)

    private fun diff(before: ProjectRevisions, after: ProjectRevisions) = tracker.changed(before, after)

    @Test
    fun `nothing changed reports nothing`() {
        terrain("hills", "splatBase" to "u-grass")
        texture("grass", "u-grass")
        texture("rock", "u-rock")
        assertEquals(emptySet<String>(), diff(snap(), snap()))
    }

    @Test
    fun `a replaced splat image invalidates its terrains but not unrelated assets`() {
        terrain("hills", "splatBase" to "u-grass")
        terrain("flat")
        texture("grass", "u-grass")
        texture("rock", "u-rock")
        val before = snap()
        texture("grass", "u-grass", bytes = "a longer image", modified = 2000)
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `an image replaced with the same size and a newer time is a change`() {
        terrain("hills", "splatR" to "u-grass")
        texture("grass", "u-grass", bytes = "aaa", modified = 1000)
        val before = snap()
        texture("grass", "u-grass", bytes = "bbb", modified = 5000)
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `texture metadata edits invalidate dependent terrains`() {
        terrain("hills", "splatMap" to "u-grass")
        texture("grass", "u-grass", image = "a.png")
        write("grass/b.png", "other")
        val before = snap()
        texture("grass", "u-grass", image = "b.png")
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `a terrain edit invalidates only that terrain`() {
        terrain("hills", "splatBase" to "u-grass")
        terrain("flat")
        texture("grass", "u-grass")
        val before = snap()
        terrain("hills", "splatBase" to "u-grass", size = 200)
        assertEquals(setOf("hills"), diff(before, snap()))
    }

    @Test
    fun `changing the uuid of a referenced texture invalidates the terrains that referenced it`() {
        terrain("hills", "splatBase" to "u-grass")
        terrain("flat", "splatBase" to "u-rock")
        texture("grass", "u-grass")
        texture("rock", "u-rock")
        val before = snap()
        texture("grass", "u-new")
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `a texture that was absent appears and the terrain waiting for it is invalidated`() {
        terrain("hills", "splatBase" to "u-grass")
        terrain("flat")
        assertNull(snap().assets.getValue("hills").references["splatBase"])
        val before = snap()
        texture("grass", "u-grass")
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `a texture that disappears invalidates its terrains and the removed asset itself`() {
        terrain("hills", "splatBase" to "u-grass")
        texture("grass", "u-grass")
        val before = snap()
        File(assets, "grass").deleteRecursively()
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
    }

    @Test
    fun `an unrelated asset added or removed does not invalidate terrains`() {
        terrain("hills", "splatBase" to "u-grass")
        texture("grass", "u-grass")
        val before = snap()
        texture("sand", "u-sand")
        assertEquals(setOf("sand"), diff(before, snap()))
        val withSand = snap()
        File(assets, "sand").deleteRecursively()
        assertEquals(setOf("sand"), diff(withSand, snap()))
    }

    @Test
    fun `a broken meta is a change and does not break the snapshot`() {
        terrain("hills", "splatBase" to "u-grass")
        texture("grass", "u-grass")
        val before = snap()
        write("grass/meta.json", "{ not json")
        assertEquals(setOf("grass", "hills"), diff(before, snap()))
        assertNull(snap().assets.getValue("grass").uuid)
    }

    @Test
    fun `the heights file stamp and the terrain data replacement are changes`() {
        terrain("hills")
        val before = snap()
        write("hills/terrain.data", "yyyy", 9000)
        assertEquals(setOf("hills"), diff(before, snap()))
    }

    @Test
    fun `unsaved metadata text is used instead of the file`() {
        terrain("hills", "splatBase" to "u-grass")
        texture("grass", "u-grass")
        val before = snap()
        val edited = """{"format":"abyssus","formatVersion":1,"uuid":"u-grass","type":"TEXTURE","additional":{"file":"a.png"},"x":1}"""
        val after = tracker.snapshot(assets) { f -> if (f.parentFile.name == "grass") edited else f.readText() }
        assertEquals(setOf("grass", "hills"), diff(before, after))
    }

    @Test
    fun `separate projects have separate snapshots`() {
        val other = TemporaryFolder().also { it.create() }
        try {
            terrain("hills", "splatBase" to "u-grass")
            texture("grass", "u-grass")
            File(other.root, "assets/grass").mkdirs()
            File(other.root, "assets/grass/meta.json").writeText("""{"format":"abyssus","formatVersion":1,"uuid":"u-grass","type":"TEXTURE","additional":{"file":"a.png"}}""")
            val mine = snap()
            val theirs = tracker.snapshot(File(other.root, "assets"))
            texture("grass", "u-grass", bytes = "changed again", modified = 7000)
            assertEquals(setOf("grass", "hills"), diff(mine, snap()))
            assertEquals(emptySet<String>(), diff(theirs, tracker.snapshot(File(other.root, "assets"))))
        } finally {
            other.delete()
        }
    }
}
