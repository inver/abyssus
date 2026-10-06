/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.assetfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetTransactionEngineTest {
    /** Files and folders in memory; [failAt] makes the n-th mutation (0 based) throw, before or after it took effect. */
    private class MemoryStore(
        val files: MutableMap<String, ByteArray> = linkedMapOf(),
        val dirs: MutableSet<String> = linkedSetOf(""),
    ) : AssetFileStore {
        var mutations = 0
        var failAt: Int? = null
        var failAfter = false
        var failRestores = false

        private fun mutate(effect: () -> Unit) {
            val n = mutations++
            val fail = failAt == n
            if (fail && !failAfter) throw java.io.IOException("injected before $n")
            effect()
            if (fail && failAfter) throw java.io.IOException("injected after $n")
        }

        override fun read(path: String) = files[path]?.let { FileSnapshot.Bytes(it) } ?: FileSnapshot.Absent
        override fun dirExists(path: String) = path in dirs
        override fun children(path: String) =
            (files.keys + dirs).filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == path }.map { it.substringAfterLast('/') }

        override fun write(path: String, bytes: ByteArray) = mutate {
            check(path.substringBeforeLast('/', "") in dirs) { "no folder for $path" }
            files[path] = bytes.copyOf()
        }

        override fun delete(path: String) = mutate { files.remove(path) }
        override fun makeDir(path: String) = mutate { dirs += path }
        override fun removeDir(path: String) = mutate { dirs -= path }

        fun state() = files.mapValues { it.value.toList() } to dirs.toSet()
    }

    private fun bytes(vararg b: Int) = FileSnapshot.Bytes(ByteArray(b.size) { b[it].toByte() })

    private fun regenerate() = AssetTransaction(
        "Regenerate",
        changes = listOf(
            FileChange("assets/t/terrain.data", bytes(1, 2), bytes(3, 4)),
            FileChange("assets/t/recipe.json", FileSnapshot.Absent, bytes(9)),
        ),
        expectedFiles = mapOf("assets/t/meta.json" to bytes(7)),
    )

    private fun regenerateStore() = MemoryStore(
        files = linkedMapOf("assets/t/terrain.data" to byteArrayOf(1, 2), "assets/t/meta.json" to byteArrayOf(7)),
        dirs = linkedSetOf("", "assets", "assets/t"),
    )

    private fun create() = AssetTransaction(
        "Create",
        changes = listOf(
            FileChange("assets/hills/meta.json", FileSnapshot.Absent, bytes(1)),
            FileChange("assets/hills/terrain.data", FileSnapshot.Absent, bytes(2, 2)),
        ),
        createdDirs = listOf("assets", "assets/hills"),
    )

    @Test
    fun `a transaction applies and reverses exactly`() {
        val store = regenerateStore()
        val start = store.state()
        val engine = AssetTransactionEngine(store)
        assertEquals(AssetCommandResult.Done, engine.apply(regenerate(), true))
        assertEquals(listOf<Byte>(3, 4), store.files.getValue("assets/t/terrain.data").toList())
        assertEquals(listOf<Byte>(9), store.files.getValue("assets/t/recipe.json").toList())
        assertEquals(AssetCommandResult.Done, engine.apply(regenerate(), false))
        assertEquals(start, store.state())
        assertEquals(AssetCommandResult.Done, engine.apply(regenerate(), true))
        assertEquals(listOf<Byte>(3, 4), store.files.getValue("assets/t/terrain.data").toList())
    }

    @Test
    fun `a stale starting file is a conflict and nothing is written`() {
        val store = regenerateStore()
        store.files["assets/t/terrain.data"] = byteArrayOf(5)
        val start = store.state()
        assertEquals(AssetCommandResult.Conflict("assets/t/terrain.data"), AssetTransactionEngine(store).apply(regenerate(), true))
        assertEquals(start, store.state())
        assertEquals(0, store.mutations)
    }

    @Test
    fun `an expected file that changed is a conflict`() {
        val store = regenerateStore()
        store.files["assets/t/meta.json"] = byteArrayOf(8)
        assertEquals(AssetCommandResult.Conflict("assets/t/meta.json"), AssetTransactionEngine(store).apply(regenerate(), true))
        store.files.remove("assets/t/meta.json")
        assertEquals(AssetCommandResult.Conflict("assets/t/meta.json"), AssetTransactionEngine(store).apply(regenerate(), true))
    }

    @Test
    fun `every injected failure before or after each write restores the starting state`() {
        val operations = MemoryStore(regenerateStore().files.toMutableMap(), regenerateStore().dirs.toMutableSet()).also {
            AssetTransactionEngine(it).apply(regenerate(), true)
        }.mutations
        assertEquals(2, operations)
        for (after in listOf(false, true)) for (n in 0 until operations) {
            val store = regenerateStore()
            val start = store.state()
            store.failAt = n
            store.failAfter = after
            val result = AssetTransactionEngine(store).apply(regenerate(), true)
            assertTrue("fail ${if (after) "after" else "before"} $n: $result", result is AssetCommandResult.Failed)
            assertTrue((result as AssetCommandResult.Failed).rolledBack)
            assertEquals("fail ${if (after) "after" else "before"} write $n", start, store.state())
        }
    }

    @Test
    fun `a failing creation removes the files and folders it made`() {
        val operations = MemoryStore().also { AssetTransactionEngine(it).apply(create(), true) }.mutations
        assertEquals(4, operations)
        for (after in listOf(false, true)) for (n in 0 until operations) {
            val store = MemoryStore()
            val start = store.state()
            store.failAt = n
            store.failAfter = after
            val result = AssetTransactionEngine(store).apply(create(), true)
            assertTrue((result as AssetCommandResult.Failed).rolledBack)
            assertEquals("fail ${if (after) "after" else "before"} step $n", start, store.state())
        }
    }

    @Test
    fun `a failure while restoring reports what could not be put back`() {
        val store = regenerateStore()
        store.failAt = 1 // the recipe write fails; restoring the heights is mutation 2, which also fails
        val engine = AssetTransactionEngine(object : AssetFileStore by store {
            override fun write(path: String, bytes: ByteArray) {
                if (store.mutations >= 2) throw java.io.IOException("disk gone")
                store.write(path, bytes)
            }
        })
        val result = engine.apply(regenerate(), true) as AssetCommandResult.Failed
        assertFalse(result.rolledBack)
        assertEquals(listOf("assets/t/terrain.data"), result.leftover)
        assertTrue(result.cause.suppressed.isNotEmpty())
    }

    @Test
    fun `cancellation before the first write writes nothing but after it the commit completes`() {
        val store = regenerateStore()
        val start = store.state()
        assertEquals(AssetCommandResult.Cancelled, AssetTransactionEngine(store).apply(regenerate(), true) { false })
        assertEquals(start, store.state())

        var cancelled = false
        val late = object : AssetFileStore by store {
            override fun write(path: String, bytes: ByteArray) {
                store.write(path, bytes)
                cancelled = true // cancellation requested during the commit
            }
        }
        assertEquals(AssetCommandResult.Done, AssetTransactionEngine(late).apply(regenerate(), true) { !cancelled })
        assertTrue(store.files.containsKey("assets/t/recipe.json"))
    }

    @Test
    fun `creation collides with an existing folder`() {
        val store = MemoryStore(dirs = linkedSetOf("", "assets", "assets/hills"))
        assertEquals(AssetCommandResult.Collision("assets"), AssetTransactionEngine(store).apply(create(), true))
        val withAssets = AssetTransaction("Create", create().changes, createdDirs = listOf("assets/hills"))
        assertEquals(AssetCommandResult.Collision("assets/hills"), AssetTransactionEngine(store).apply(withAssets, true))
        assertEquals(0, store.mutations)
    }

    @Test
    fun `undoing a creation needs the same bytes and an otherwise empty folder`() {
        val store = MemoryStore()
        val engine = AssetTransactionEngine(store)
        engine.apply(create(), true)

        store.files["assets/hills/extra.txt"] = byteArrayOf(1)
        assertEquals(AssetCommandResult.Conflict("assets/hills"), engine.apply(create(), false))
        store.files.remove("assets/hills/extra.txt")

        store.files["assets/hills/terrain.data"] = byteArrayOf(5)
        assertEquals(AssetCommandResult.Conflict("assets/hills/terrain.data"), engine.apply(create(), false))
        store.files["assets/hills/terrain.data"] = byteArrayOf(2, 2)

        assertEquals(AssetCommandResult.Done, engine.apply(create(), false))
        assertEquals(MemoryStore().state(), store.state())
        assertEquals(AssetCommandResult.Done, engine.apply(create(), true))
        assertEquals(listOf<Byte>(1), store.files.getValue("assets/hills/meta.json").toList())
    }

    @Test
    fun `a guard that finds a dependent refuses the undo and nothing is removed`() {
        val store = MemoryStore()
        var reason: String? = null
        val txn = AssetTransaction("Create", create().changes, createdDirs = create().createdDirs, guard = { reason })
        val engine = AssetTransactionEngine(store)
        engine.apply(txn, true)
        val created = store.state()
        reason = "Main Scene uses hills"
        assertEquals(AssetCommandResult.Blocked("Main Scene uses hills"), engine.apply(txn, false))
        assertEquals(created, store.state())
        reason = null
        assertEquals(AssetCommandResult.Done, engine.apply(txn, false))
    }

    @Test
    fun `snapshots are immutable copies`() {
        val source = byteArrayOf(1, 2, 3)
        val snapshot = FileSnapshot.Bytes(source)
        source[0] = 9
        snapshot.toByteArray()[1] = 9
        assertEquals(listOf<Byte>(1, 2, 3), snapshot.toByteArray().toList())
        assertEquals(FileSnapshot.Bytes(byteArrayOf(1, 2, 3)), snapshot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a change that changes nothing is refused`() {
        FileChange("a", bytes(1), bytes(1))
    }
}
