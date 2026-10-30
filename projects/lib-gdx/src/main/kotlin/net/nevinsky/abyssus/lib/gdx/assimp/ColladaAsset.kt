/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import java.io.File
import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

/**
 * Reads the unit and up axis a COLLADA (DAE) file states in `COLLADA/asset` (`unit@meter`, `up_axis`). Assimp's
 * Collada importer applies them itself and does not report them, so an import that lets the user choose reads them
 * here. `X_UP` is returned as [UpAxis.X]: stated, though an import may not offer it. Nothing else is read; DTDs and
 * external entities are refused.
 */
class ColladaAsset {
    fun read(file: File): StatedFrame = file.inputStream().buffered().use { read(it) }

    /** @throws XMLStreamException if the document is not well-formed up to the end of its `asset` element */
    fun read(input: InputStream): StatedFrame {
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        val reader = factory.createXMLStreamReader(input)
        try {
            var depth = 0
            var inAsset = false
            var unit: Float? = null
            var up: UpAxis? = null
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        depth++
                        val name = reader.localName
                        when {
                            depth == 2 && name == "asset" -> inAsset = true
                            depth == 2 -> return StatedFrame(unit, up) // asset comes first, if at all
                            inAsset && depth == 3 && name == "unit" ->
                                unit = reader.getAttributeValue(null, "meter")?.trim()?.toFloatOrNull()?.takeIf { it > 0f && it.isFinite() }
                            inAsset && depth == 3 && name == "up_axis" -> up = when (reader.elementText.trim()) {
                                "X_UP" -> UpAxis.X
                                "Y_UP" -> UpAxis.Y
                                "Z_UP" -> UpAxis.Z
                                else -> null
                            }.also { depth-- } // getElementText consumed the end tag
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (inAsset && depth == 2) return StatedFrame(unit, up)
                        depth--
                    }
                }
            }
            return StatedFrame(unit, up)
        } finally {
            reader.close()
        }
    }
}
