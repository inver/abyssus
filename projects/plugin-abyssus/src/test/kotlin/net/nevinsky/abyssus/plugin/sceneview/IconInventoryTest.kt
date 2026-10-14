/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconInventoryTest {
    private fun resource(path: String) = SceneViewIcon::class.java.getResource(path)

    @Test
    fun everyListedIconHasALightAndADarkFile() {
        for (icon in SceneViewIcon.entries) {
            assertNotNull("missing icon resource ${icon.path}", resource(icon.path))
            assertNotNull("missing icon resource ${icon.darkPath}", resource(icon.darkPath))
        }
    }

    @Test
    fun noSceneViewIconFileIsUnlisted() {
        val dir = File("src/main/resources/icons")
        assertTrue("icons folder not found from ${File(".").absolutePath}", dir.isDirectory)
        val listed = SceneViewIcon.entries.flatMap { listOf(it.path, it.darkPath) }.map { it.substringAfterLast('/') }.toSet()
        val onDisk = dir.listFiles { f -> f.name.startsWith("scene_view_") }.orEmpty().map { it.name }.toSet()
        assertEquals("orphaned icon files: ${onDisk - listed}", emptySet<String>(), onDisk - listed)
    }

    @Test
    fun theDarkVariantDiffersFromTheLightOne() {
        for (icon in SceneViewIcon.entries) {
            val light = checkNotNull(resource(icon.path)).readText()
            val dark = checkNotNull(resource(icon.darkPath)).readText()
            assertTrue("${icon.name} dark variant equals the light one", light != dark)
        }
    }
}
