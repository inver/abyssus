/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ManifestBindingTest {
    private val sample = Manifest(
        schemaVersion = 1, product = "control-line", version = "1.2.0",
        mainClass = "net.nevinsky.abyssus.app.game.controlline.MainKt", installerDigest = "ab".repeat(32),
        parts = listOf(ManifestPart("game-code-1.2.0.zip", 10, "cd".repeat(32))),
        files = listOf(ManifestFile("a.jar", 4, "ef".repeat(32), "game-code-1.2.0.zip")),
    )

    @Test
    fun `round trip`() {
        assertEquals(sample, bindManifest(writeManifest(sample)))
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = String(writeManifest(sample)).replaceFirst("{", "{\"extra\": [1, 2],")
        assertEquals(sample, bindManifest(json.toByteArray()))
    }

    @Test
    fun `unknown schema version means a new installer is needed`() {
        val json = """{"schemaVersion": 2, "somethingNew": true}"""
        try {
            bindManifest(json.toByteArray())
            fail("expected a refusal")
        } catch (e: ManifestException) {
            assertTrue(e.newInstallerNeeded)
        }
    }

    @Test
    fun `bad content is refused without asking for an installer`() {
        for (json in listOf("not json", """{"schemaVersion": 1}""")) {
            try {
                bindManifest(json.toByteArray())
                fail("expected a refusal for $json")
            } catch (e: ManifestException) {
                assertFalse(e.newInstallerNeeded)
            }
        }
    }
}

/** A trimmed manifest written by the packaging task `buildUpdateParts` (build-logic's `renderManifest`). */
class PackagingManifestTest {
    @Test
    fun `the manifest the packaging task writes binds`() {
        val bytes = javaClass.getResourceAsStream("/sample-manifest.json")!!.readAllBytes()
        val manifest = bindManifest(bytes)
        assertEquals("control-line", manifest.product)
        assertEquals(2, manifest.parts.size)
        assertEquals(setOf("installer") + manifest.parts.map { it.name }, manifest.files.map { it.part }.toSet())
        assertTrue(manifest.files.all { it.sha256.length == 64 && it.size > 0 })
        assertTrue(parseVersionOrNull(manifest.version) != null)
    }
}
