/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.splat

import net.nevinsky.abyssus.lib.core.editor.terrain.splat.NewSplatFiles
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransactionEngine
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplatTransactionsTest {
    class MemoryStore(val files: MutableMap<String, ByteArray> = HashMap(), val dirs: MutableSet<String> = HashSet()) : AssetFileStore {
        override fun read(path: String): FileSnapshot = files[path]?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent
        override fun dirExists(path: String) = path in dirs || files.keys.any { it.startsWith("$path/") }
        override fun children(path: String) = files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/").substringBefore('/') }.distinct()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes }
        override fun delete(path: String) { files.remove(path) }
        override fun makeDir(path: String) { dirs += path }
        override fun removeDir(path: String) { dirs -= path }
    }

    private val store = MemoryStore(
        hashMapOf(
            "assets/texture_splat/meta.json" to "tex".toByteArray(),
            "assets/texture_splat/splat.png" to byteArrayOf(1, 2, 3),
            "assets/terrain_a/meta.json" to "terrain".toByteArray(),
        ),
    )
    private val engine = AssetTransactionEngine(store)
    private val transactions = SplatTransactions(store)

    @Test
    fun `a stroke plans one change and an equal image plans none`() {
        val txn = transactions.stroke("Paint", "texture_splat", "splat.png", byteArrayOf(1, 2, 3), byteArrayOf(9, 9),
            listOf("assets/texture_splat/meta.json", "assets/terrain_a/meta.json"))!!
        assertEquals(listOf("assets/texture_splat/splat.png"), txn.changes.map { it.path })
        assertEquals(setOf("assets/texture_splat/meta.json", "assets/terrain_a/meta.json"), txn.expectedFiles.keys)
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = true))
        assertArrayEquals(byteArrayOf(9, 9), store.files["assets/texture_splat/splat.png"])
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = false))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.files["assets/texture_splat/splat.png"])
        assertNull(transactions.stroke("Paint", "texture_splat", "splat.png", byteArrayOf(1), byteArrayOf(1), emptyList()))
    }

    @Test
    fun `an image changed in between is a conflict for the stroke and for its undo`() {
        val txn = transactions.stroke("Paint", "texture_splat", "splat.png", byteArrayOf(1, 2, 3), byteArrayOf(9, 9), emptyList())!!
        engine.apply(txn, forward = true)
        store.files["assets/texture_splat/splat.png"] = byteArrayOf(7)
        assertTrue(engine.apply(txn, forward = false) is AssetCommandResult.Conflict)
        assertArrayEquals(byteArrayOf(7), store.files["assets/texture_splat/splat.png"])
    }

    @Test
    fun `create plans the folder, two files and the terrain meta and undo removes the folder and restores the link`() {
        val files = NewSplatFiles("texture_terrain_a_splat", "uuid-1", "{meta}", byteArrayOf(5, 6), "terrain-linked")
        val txn = transactions.create("Create splat map", "terrain_a", files, "terrain".toByteArray(), "splat.png")
        assertEquals(listOf("assets/texture_terrain_a_splat"), txn.createdDirs)
        assertEquals(3, txn.changes.size)
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = true))
        assertArrayEquals("terrain-linked".toByteArray(), store.files["assets/terrain_a/meta.json"])
        assertArrayEquals(byteArrayOf(5, 6), store.files["assets/texture_terrain_a_splat/splat.png"])
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = false))
        assertArrayEquals("terrain".toByteArray(), store.files["assets/terrain_a/meta.json"])
        assertFalse(store.files.keys.any { it.startsWith("assets/texture_terrain_a_splat") })
        assertFalse("assets/texture_terrain_a_splat" in store.dirs)
    }

    @Test
    fun `create refuses a terrain meta that changed and a folder that appeared`() {
        val files = NewSplatFiles("texture_terrain_a_splat", "uuid-1", "{meta}", byteArrayOf(5, 6), "terrain-linked")
        val txn = transactions.create("Create splat map", "terrain_a", files, "terrain".toByteArray(), "splat.png")
        store.files["assets/terrain_a/meta.json"] = "edited".toByteArray()
        assertTrue(engine.apply(txn, forward = true) is AssetCommandResult.Conflict)
        store.files["assets/terrain_a/meta.json"] = "terrain".toByteArray()
        store.dirs += "assets/texture_terrain_a_splat"
        assertTrue(engine.apply(txn, forward = true) is AssetCommandResult.Collision)
        assertTrue(store.files.keys.none { it.startsWith("assets/texture_terrain_a_splat/") })
    }
}
