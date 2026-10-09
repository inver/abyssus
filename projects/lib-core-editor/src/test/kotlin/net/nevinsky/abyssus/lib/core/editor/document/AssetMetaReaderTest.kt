/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.gdx.editor.document
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import org.junit.Assert.*
import org.junit.Test

class AssetMetaReaderTest {
    private val reader = AssetMetaReader(JsonProcessor())
    @Test fun rejectedMetadataCannotExposeTypeUuidOrReferences() {
        for (header in listOf("", "\"format\":\"foreign\",\"formatVersion\":1,", "\"format\":\"abyssus\",\"formatVersion\":2,")) {
            assertThrows(UnsupportedDocumentFormat::class.java) { reader.read("{$header\"type\":\"TERRAIN\",\"uuid\":\"u\",\"additional\":{\"splatR\":\"v\"}}") }
        }
        val supported = reader.read("""{"format":"abyssus","formatVersion":1,"version":7,"type":"MODEL","uuid":"u","additional":{}}""")
        assertEquals(MetaType.MODEL, supported.type)
        assertEquals(7, supported.json["version"].intValue())
    }
}
