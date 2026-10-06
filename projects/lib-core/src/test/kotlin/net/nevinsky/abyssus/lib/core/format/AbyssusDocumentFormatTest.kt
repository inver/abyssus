/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.format

import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import org.junit.Assert.*
import org.junit.Test

class AbyssusDocumentFormatTest {
    private val json = JsonProcessor()
    private val format = AbyssusDocumentFormat()

    @Test fun nativeHeadersSupportEachDocumentKindWithoutChangingThePayload() {
        for (kind in DocumentKind.entries) {
            val node = json.readObject("""{"format":"abyssus","formatVersion":1,"version":7,"extra":{"class":"custom"}}""")
            val before = json.toString(node)
            assertNull(format.validate(node, kind))
            assertEquals(before, json.toString(node))
        }
    }

    @Test fun absentForeignNullAndNontextMarkersAreRejected() {
        for (text in listOf("{}", """{"format":null,"formatVersion":1}""", """{"format":"foreign","formatVersion":1}""", """{"format":true,"formatVersion":1}""")) {
            val reason = format.validate(json.readObject(text), DocumentKind.SCENE)
            assertEquals(FormatProblem.MARKER, reason?.problem)
            assertEquals("format", reason?.path)
        }
    }

    @Test fun onlyIntegralVersionOneIsAccepted() {
        for (version in listOf("null", "\"1\"", "1.0", "1.5", "2", "-1", "0", "true", "999999999999999999999999")) {
            val node = json.readObject("""{"format":"abyssus","formatVersion":$version}""")
            assertEquals(version, FormatProblem.VERSION, format.validate(node, DocumentKind.SCENE)?.problem)
        }
        assertEquals(FormatProblem.VERSION, format.validate(json.readObject("""{"format":"abyssus"}"""), DocumentKind.PROJECT)?.problem)
    }

    @Test fun markersCannotAdmitLegacyIdentifiersOrRenderableClasses() {
        for (ecs in listOf("""{"componentIdentifiers":null}""", """{"componentIdentifiers":{}}""", """{"entities":{"0":{"components":{"RenderComponent":{"renderable":{"class":null,"kind":"asset"}}}}}}""")) {
            val node = json.readObject("""{"format":"abyssus","formatVersion":1,"ecs":$ecs}""")
            assertEquals(FormatProblem.LEGACY_FIELD, format.validate(node, DocumentKind.SCENE)?.problem)
            assertEquals(FormatProblem.LEGACY_FIELD, format.validateEcs(node["ecs"])?.problem)
        }
    }

    @Test fun reservedFieldsAreCheckedOnlyAtTheirDefinedPaths() {
        val node = json.readObject("""{"format":"abyssus","formatVersion":1,"ecs":{"entities":{"0":{"components":{"WindComponent":{"class":"net.example.Wind","componentIdentifiers":{},"renderable":{"class":"opaque"}},"RenderComponent":{"renderable":{"kind":"debug-marker","payload":{"class":"opaque"}}}}}},"metadata":{"class":"opaque"}}}""")
        assertNull(format.validate(node, DocumentKind.SCENE))
        assertNull(format.validateEcs(node["ecs"]))
    }

    @Test fun requireMethodsReportTheDocumentKindAndReservedPath() {
        val node = json.readObject("""{"format":"abyssus","formatVersion":1,"ecs":{"componentIdentifiers":{}}}""")
        val failure = assertThrows(UnsupportedDocumentFormat::class.java) { format.requireSupported(node, DocumentKind.SCENE) }
        assertEquals(DocumentKind.SCENE, failure.kind)
        assertEquals("ecs.componentIdentifiers", failure.reason.path)
        assertTrue(failure.message!!.contains("componentIdentifiers"))
        assertThrows(UnsupportedDocumentFormat::class.java) { format.requireEcs(node["ecs"]) }
    }
}
