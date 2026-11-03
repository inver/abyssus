/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.editor.terrain.splat

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SplatLayersTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val layers = SplatLayers(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER))
    private val assets get() = File(tmp.root, "assets")

    private fun texture(name: String, uuid: String) {
        val dir = File(assets, name).apply { mkdirs() }
        File(dir, "meta.json").writeText("""{"format":"abyssus","formatVersion":1,"version":1,"uuid":"$uuid","type":"TEXTURE","additional":{"file":"a.png"}}""")
        File(dir, "a.png").writeBytes(byteArrayOf(1))
    }

    private fun terrain(vararg fields: String) =
        """{"format":"abyssus","formatVersion":1,"version":1,"uuid":"t","type":"TERRAIN","additional":{"size":100${fields.joinToString("") { ",$it" }}}}"""

    @Test
    fun `the five slots come in order with names, empty slots and an unresolved reference kept visible`() {
        texture("texture_dirt", "uuid-dirt")
        val slots = layers.read(assets, terrain(""""splatBase":"uuid-dirt"""", """"splatG":"uuid-gone"""", """"splatB":null"""))
        assertEquals(listOf("splatBase", "splatR", "splatG", "splatB", "splatA"), slots.map { it.field })
        assertEquals("texture_dirt", slots[0].name)
        assertTrue(slots[0].resolved)
        assertNotNull(slots[0].image)
        assertNull(slots[1].uuid)
        assertNull(slots[1].name)
        assertEquals("uuid-gone", slots[2].uuid)
        assertFalse(slots[2].resolved)
        assertEquals("uuid-gone", slots[2].name)
        assertNull(slots[3].uuid)
    }

    @Test
    fun `an unsupported terrain meta has no slots`() {
        assertTrue(layers.read(assets, """{"additional":{}}""").isEmpty())
        assertTrue(layers.read(assets, "{ nope").isEmpty())
    }
}
