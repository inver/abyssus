/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.io

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicJsonStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val store = AtomicJsonStore()

    @Test fun truncatedFileIsKeptAsBadAndReplacementIsWhole() {
        val file = tmp.root.toPath().resolve("scores.json")
        Files.writeString(file, "{\"version\":")
        var damaged = false
        val read = store.read(file, {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(it.toFile())
        }, { damaged = true })
        assertNull(read)
        assertTrue(damaged)
        assertFalse(Files.exists(file))
        assertEquals("{\"version\":", Files.readString(file.resolveSibling("scores.json.bad")))
        store.save(file, "scores") { Files.writeString(it, "{\"version\":1}") }
        store.save(file, "scores") { Files.writeString(it, "{\"version\":2}") }
        assertEquals("{\"version\":2}", Files.readString(file))
        assertEquals(setOf("scores.json", "scores.json.bad"), tmp.root.list()!!.toSet())
    }

    @Test fun failedWriteLeavesPreviousMainFileAndRemovesTemp() {
        val file = tmp.root.toPath().resolve("scores.json")
        Files.writeString(file, "previous")
        assertThrows(java.io.IOException::class.java) {
            store.save(file, "scores") {
                Files.writeString(it, "partial")
                throw java.io.IOException("write failed")
            }
        }
        assertEquals("previous", Files.readString(file))
        assertEquals(listOf("scores.json"), tmp.root.list()!!.toList())
    }
}
