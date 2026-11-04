/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.format.UnsupportedDocumentFormat
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test

class AssetMetaReaderTest {
    private val reader = AssetMetaReader(JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER))

    @Test
    fun rejectedMetadataCannotExposeTypeUuidOrReferences() {
        for (header in listOf("", "\"formatVersion\":2,", "\"formatVersion\":1.0,")) {
            assertThrows(UnsupportedDocumentFormat::class.java) {
                reader.read("{$header\"type\":\"TERRAIN\",\"uuid\":\"u\",\"additional\":{\"splatR\":\"v\"}}")
            }
        }
        val supported =
            reader.read(
                """{"format":"abyssus","formatVersion":1,"version":7,"type":"MODEL","uuid":"u","additional":{}}"""
            )
        assertEquals(MetaType.MODEL, supported.type)
        assertEquals(7, supported.values["version"])
    }
}
