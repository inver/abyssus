/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import net.nevinsky.abyssus.lib.gdx.editor.scene.AssetRevisionBatch
import net.nevinsky.abyssus.lib.gdx.editor.scene.PendingAssetRevision
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AssetRefreshTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val backgroundQueue = ArrayDeque<Runnable>()
    private val uiQueue = ArrayDeque<Runnable>()
    private val delivered = mutableListOf<AssetRevisionBatch>()
    private var unsaved: Map<File, String> = emptyMap()
    private var unsavedReads = 0

    private val assets get() = File(tmp.root, "assets")

    private fun refresh() = AssetRefresh(
        tmp.root, JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER),
        unsavedMeta = { unsavedReads++; unsaved },
        background = { backgroundQueue += it },
        ui = { uiQueue += it },
        deliver = { delivered += it },
    )

    /** Runs the pending background reads and their UI continuations until nothing is left. */
    private fun settle() {
        while (backgroundQueue.isNotEmpty() || uiQueue.isNotEmpty()) {
            backgroundQueue.removeFirstOrNull()?.run()
            uiQueue.removeFirstOrNull()?.run()
        }
    }

    private fun write(path: String, text: String, modified: Long? = null): File =
        File(assets, path).apply { parentFile.mkdirs(); writeText(text); modified?.let { setLastModified(it) } }

    private fun terrainMeta(size: Int) =
        """{"format":"abyssus","formatVersion":1,"uuid":"t","type":"TERRAIN","additional":{"terrainFile":"terrain.data","size":$size,"uv":1.0,"splatBase":"u-grass"}}"""

    /** The size the `hills` terrain meta says as a load after [batch] reads it (its unsaved text over the disk). */
    private fun terrainSize(batch: AssetRevisionBatch): Int {
        val meta = File(assets, "hills/meta.json").absoluteFile
        val text = batch.unsaved[meta] ?: meta.readText()
        return JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER).readObject(text).get("additional").get("size").asInt()
    }

    private fun project() {
        write("hills/terrain.data", "1234", 1000)
        write("hills/meta.json", terrainMeta(100))
        write("grass/a.png", "png", 1000)
        write("grass/meta.json", """{"format":"abyssus","formatVersion":1,"uuid":"u-grass","type":"TEXTURE","additional":{"file":"a.png"}}""")
        write("tree/meta.json", """{"format":"abyssus","formatVersion":1,"uuid":"m","type":"MODEL","additional":{"file":"tree.gltf"}}""")
        write("tree/tree.gltf", "gltf", 1000)
    }

    private fun started(): AssetRefresh {
        project()
        return refresh().also { it.start(); settle() }
    }

    @Test
    fun `nothing is delivered for the starting revision or an event that changed nothing`() {
        val r = started()
        r.changed()
        settle()
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `a saved metadata change reloads the asset`() {
        val r = started()
        write("hills/meta.json", terrainMeta(200))
        r.changed()
        settle()
        assertEquals(setOf("hills"), delivered.single().names)
        assertEquals(200, terrainSize(delivered.single()))
    }

    @Test
    fun `unsaved metadata text is used and saving the same text is not a second change`() {
        val r = started()
        val meta = File(assets, "hills/meta.json").absoluteFile
        unsaved = mapOf(meta to terrainMeta(300))
        r.changed()
        settle()
        assertEquals(setOf("hills"), delivered.single().names)
        assertEquals("the snapshot reads the unsaved text", 300, terrainSize(delivered.single()))
        assertEquals("the disk still holds the old size", 100, JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER).readObject(meta.readText()).get("additional").get("size").asInt())

        // saved: the document is no longer unsaved and the disk holds the same text
        write("hills/meta.json", terrainMeta(300))
        unsaved = emptyMap()
        r.changed()
        settle()
        assertEquals("saving what is already shown does nothing", 1, delivered.size)
    }

    @Test
    fun `undo back to the loaded text reloads again and redo reapplies`() {
        val r = started()
        val meta = File(assets, "hills/meta.json").absoluteFile
        unsaved = mapOf(meta to terrainMeta(300))
        r.changed(); settle()
        unsaved = emptyMap() // undo: the unsaved text is gone, the disk text is the original
        r.changed(); settle()
        unsaved = mapOf(meta to terrainMeta(300)) // redo
        r.changed(); settle()
        assertEquals(listOf(setOf("hills"), setOf("hills"), setOf("hills")), delivered.map { it.names })
        assertEquals(listOf(300, 100, 300), delivered.map { terrainSize(it) })
    }

    @Test
    fun `a replaced texture image reloads the texture and the terrain using it but not other assets`() {
        val r = started()
        write("grass/a.png", "a longer png", 5000)
        r.changed()
        settle()
        assertEquals(setOf("grass", "hills"), delivered.single().names)
    }

    @Test
    fun `external deletion and recreation of the heights are both changes`() {
        val r = started()
        File(assets, "hills/terrain.data").delete()
        r.changed(); settle()
        assertEquals(setOf("hills"), delivered.last().names)
        write("hills/terrain.data", "1234", 9000)
        r.changed(); settle()
        assertEquals(2, delivered.size)
        assertEquals(setOf("hills"), delivered.last().names)
    }

    @Test
    fun `a broken then repaired asset is reported each time and others stay out`() {
        val r = started()
        write("hills/meta.json", "{ broken")
        r.changed(); settle()
        write("hills/meta.json", terrainMeta(100))
        r.changed(); settle()
        assertEquals(listOf(setOf("hills"), setOf("hills")), delivered.map { it.names })
    }

    @Test
    fun `events during a read cost one more read, and unsaved text is captured fresh each time`() {
        val r = started()
        val before = unsavedReads
        write("hills/meta.json", terrainMeta(150))
        r.changed()
        r.changed()
        r.changed()
        assertEquals("one read in flight", 1, backgroundQueue.size)
        settle()
        assertEquals("the first read and one more for the events during it", before + 2, unsavedReads)
        assertEquals(setOf("hills"), delivered.single().names)
    }

    @Test
    fun `a disposed refresh delivers nothing even for a read in flight`() {
        val r = started()
        write("hills/meta.json", terrainMeta(150))
        r.changed()
        r.dispose()
        settle()
        r.changed()
        settle()
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun `batches merge their names and the newer snapshot wins`() {
        val r = started()
        write("hills/meta.json", terrainMeta(150)); r.changed(); settle()
        write("tree/tree.gltf", "gltf!", 4000); r.changed(); settle()
        val merged = delivered[0] + delivered[1]
        assertEquals(setOf("hills", "tree"), merged.names)
        assertEquals(150, terrainSize(merged))
        assertEquals(delivered[1].unsaved, merged.unsaved)
    }

    @Test
    fun `a project without assets has nothing to deliver`() {
        val r = refresh().also { it.start(); settle() }
        write("late/meta.json", """{"format":"abyssus","formatVersion":1,"type":"MODEL","additional":{}}""")
        r.changed(); settle()
        assertEquals(setOf("late"), delivered.single().names)
    }

    @Test
    fun `a hidden view keeps revisions until a frame takes them, merged`() {
        val r = started()
        val pending = PendingAssetRevision()
        assertEquals(null, pending.take())
        write("hills/meta.json", terrainMeta(150)); r.changed(); settle()
        pending.queue(delivered[0]) // the view is hidden: nothing renders, nothing is taken
        write("tree/tree.gltf", "gltf!", 4000); r.changed(); settle()
        pending.queue(delivered[1])
        val taken = pending.take()!!
        assertEquals(setOf("hills", "tree"), taken.names)
        assertEquals(delivered[1].unsaved, taken.unsaved)
        assertEquals("taken once", null, pending.take())
    }
}
