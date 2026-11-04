/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.store

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VersionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val root: Path by lazy { tmp.newFolder("updates").toPath() }
    private val seed: Path by lazy { tmp.newFolder("seed").toPath() }

    private fun store(free: Long = Long.MAX_VALUE) = VersionStore(root, seed) { free }

    private fun install(store: VersionStore, version: String) {
        Files.writeString(store.beginStaging(version, 10).resolve("game.jar"), version)
        store.commit(version)
    }

    @Test
    fun `empty store runs the seed`() {
        assertEquals(GameFolder(null, seed), store().resolve(emptySet()))
    }

    @Test
    fun `committed version becomes current`() {
        val store = store()
        install(store, "1.1.0")
        val folder = store.resolve(emptySet())
        assertEquals("1.1.0", folder.version)
        assertEquals("1.1.0", Files.readString(folder.dir.resolve("game.jar")))
    }

    @Test
    fun `interrupted staging is ignored and removed`() {
        val store = store()
        install(store, "1.1.0")
        Files.writeString(store.beginStaging("1.2.0", 10).resolve("half.jar"), "partial")
        Files.createDirectories(root.resolve("versions/1.3.0")) // renamed in but never marked complete
        assertEquals("1.1.0", store.resolve(emptySet()).version)
        store.cleanUp()
        assertFalse(Files.exists(root.resolve("staging")))
        assertFalse(Files.exists(root.resolve("versions/1.3.0")))
        assertEquals("1.1.0", store.resolve(emptySet()).version)
    }

    @Test
    fun `half written current falls back to the seed`() {
        val store = store()
        install(store, "1.1.0")
        for (broken in listOf("", "1.x\u0000", "../evil", "1.1.0-")) {
            Files.writeString(root.resolve("current"), broken)
            assertEquals("'$broken'", GameFolder(null, seed), store.resolve(emptySet()))
        }
        // a well-formed name of a version that is not there: the newest complete version runs
        Files.writeString(root.resolve("current"), "9.9.9")
        assertEquals("1.1.0", store.resolve(emptySet()).version)
        Files.deleteIfExists(root.resolve("versions/1.1.0/COMPLETE"))
        assertEquals(GameFolder(null, seed), store.resolve(emptySet()))
    }

    @Test
    fun `bad version is skipped for the previous good one`() {
        val store = store()
        install(store, "1.1.0")
        install(store, "1.2.0")
        assertEquals("1.1.0", store.resolve(setOf("1.2.0")).version)
        assertEquals(GameFolder(null, seed), store.resolve(setOf("1.2.0", "1.1.0")))
    }

    @Test
    fun `older folders are deleted after a switch`() {
        val store = store()
        install(store, "1.1.0")
        install(store, "1.2.0")
        install(store, "1.3.0")
        val kept = Files.list(root.resolve("versions")).use { it.map { p -> p.fileName.toString() }.sorted().toList() }
        assertEquals(listOf("1.2.0", "1.3.0"), kept)
        assertEquals("1.3.0", store.readCurrent())
    }

    @Test
    fun `not enough free space refuses before writing`() {
        try {
            store(free = 5).beginStaging("1.1.0", 10)
            fail("expected a refusal")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("free space"))
        }
        assertFalse(Files.exists(root.resolve("staging")))
    }

    @Test
    fun `version name cannot escape the folder`() {
        try {
            store().beginStaging("../x", 1)
            fail("expected a refusal")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun `state round trips and a damaged file reads as defaults`() {
        val file = root.resolve("state.json")
        val state = UpdateState(file)
        assertEquals(StateData(), state.load())
        state.update { it.copy(launching = "1.1.0", unclean = mapOf("1.1.0" to 1), bad = setOf("1.0.5")) }
        assertEquals(StateData("1.1.0", mapOf("1.1.0" to 1), setOf("1.0.5")), UpdateState(file).load())
        Files.writeString(file, "{ not json")
        assertEquals(StateData(), state.load())
        assertNull(state.load().launching)
    }
}
