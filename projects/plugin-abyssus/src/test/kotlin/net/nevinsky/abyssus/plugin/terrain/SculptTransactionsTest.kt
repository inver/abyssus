/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.terrain

import net.nevinsky.abyssus.lib.core.editor.terrain.sculpt.SculptFileSet
import net.nevinsky.abyssus.plugin.assetfiles.AssetCommandResult
import net.nevinsky.abyssus.plugin.assetfiles.AssetFileStore
import net.nevinsky.abyssus.plugin.assetfiles.AssetTransactionEngine
import net.nevinsky.abyssus.plugin.assetfiles.FileSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SculptTransactionsTest {
    private class MemoryStore(val files: MutableMap<String, ByteArray> = HashMap()) : AssetFileStore {
        override fun read(path: String): FileSnapshot = files[path]?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent
        override fun dirExists(path: String) = files.keys.any { it.startsWith("$path/") }
        override fun children(path: String) = files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/").substringBefore('/') }.distinct()
        override fun write(path: String, bytes: ByteArray) { files[path] = bytes }
        override fun delete(path: String) { files.remove(path) }
        override fun makeDir(path: String) {}
        override fun removeDir(path: String) {}
    }

    private val dir = "assets/t"
    private val store = MemoryStore(
        hashMapOf(
            "$dir/terrain.data" to byteArrayOf(1, 2, 3, 4),
            "$dir/abyssus-terrain.recipe.json" to "{}\n".toByteArray(),
            "$dir/abyssus-terrain.sculpt.json" to "manifest".toByteArray(),
            "$dir/abyssus-terrain.sculpt" to byteArrayOf(5, 5),
            "$dir/abyssus-terrain.base" to byteArrayOf(6, 6),
        ),
    )
    private val engine = AssetTransactionEngine(store)
    private val before = store.files.mapValues { it.value.copyOf() }

    private fun plan(set: SculptFileSet) = SculptTransactions(store).plan("Reset", "t", "terrain.data", set)

    @Test
    fun `a set equal to the files plans nothing`() {
        val same = SculptFileSet(before.getValue("$dir/terrain.data"), before.getValue("$dir/abyssus-terrain.recipe.json"),
            before.getValue("$dir/abyssus-terrain.sculpt.json"), before.getValue("$dir/abyssus-terrain.sculpt"), before.getValue("$dir/abyssus-terrain.base"))
        assertNull(plan(same))
    }

    @Test
    fun `reset removes the sculpt files and rewrites the heights and undo restores the exact bytes`() {
        val txn = plan(SculptFileSet(byteArrayOf(9, 9, 9, 9), before.getValue("$dir/abyssus-terrain.recipe.json"), null, null, null))!!
        assertEquals(4, txn.changes.size)
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = true))
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), store.files["$dir/terrain.data"])
        assertTrue(store.files.keys.none { it.endsWith(".sculpt") || it.endsWith(".sculpt.json") || it.endsWith(".base") })
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = false))
        assertEquals(before.keys, store.files.keys)
        for ((path, bytes) in before) assertArrayEquals(path, bytes, store.files[path])
    }

    @Test
    fun `a stroke introduces new files and undo removes them`() {
        store.files.keys.removeAll { it.endsWith(".sculpt") || it.endsWith(".sculpt.json") || it.endsWith(".base") }
        val txn = plan(SculptFileSet(byteArrayOf(1, 2, 3, 5), before.getValue("$dir/abyssus-terrain.recipe.json"), "m".toByteArray(), byteArrayOf(0, 1), null))!!
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = true))
        assertArrayEquals(byteArrayOf(0, 1), store.files["$dir/abyssus-terrain.sculpt"])
        assertEquals(AssetCommandResult.Done, engine.apply(txn, forward = false))
        assertNull(store.files["$dir/abyssus-terrain.sculpt"])
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), store.files["$dir/terrain.data"])
    }

    @Test
    fun `an external change since planning is a conflict and nothing is written`() {
        val txn = plan(SculptFileSet(byteArrayOf(9, 9, 9, 9), null, null, null, null))!!
        store.files["$dir/terrain.data"] = byteArrayOf(0, 0, 0, 0)
        val result = engine.apply(txn, forward = true)
        assertTrue(result is AssetCommandResult.Conflict)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), store.files["$dir/terrain.data"])
        assertArrayEquals(before.getValue("$dir/abyssus-terrain.sculpt"), store.files["$dir/abyssus-terrain.sculpt"])
    }
}
