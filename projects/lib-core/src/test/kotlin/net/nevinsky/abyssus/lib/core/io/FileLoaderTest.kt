/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileLoaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(): File = tmp.newFolder("project").also {
        File(it, "assets/a/textures").mkdirs()
        File(it, "assets/a/meta.json").writeText("{}")
        File(it, "assets/a/textures/t.png").writeText("x")
        File(it, "secret.txt").writeText("secret")
    }

    @Test
    fun readsFilesInsideTheAssetFolder() {
        val loader = FileLoader(project())
        assertNotNull(loader.findAssetFile("a", "meta.json"))
        assertNotNull(loader.findAssetFile("a", "textures/t.png"))
    }

    @Test
    fun refusesNamesThatLeaveTheAssetFolder() {
        val dir = project()
        val loader = FileLoader(dir)
        assertNull(loader.findAssetFile("a", "../../secret.txt"))
        assertNull(loader.findAssetFile("a", File(dir, "secret.txt").absolutePath))
        assertNull(loader.findAssetFile("a", "/etc/passwd"))
        assertNull(loader.findAssetFile("a", "textures/../../../secret.txt"))
        assertNull(loader.findAssetFile("a", "//host/share/x"))
    }

    @Test
    fun refusesASymbolicLinkOutOfTheFolder() {
        val dir = project()
        val link = File(dir, "assets/a/link.txt").toPath()
        try {
            java.nio.file.Files.createSymbolicLink(link, File(dir, "secret.txt").toPath())
        } catch (e: UnsupportedOperationException) {
            return
        }
        assertNull(FileLoader(dir).findAssetFile("a", "link.txt"))
        assertEquals("secret", File(dir, "secret.txt").readText())
    }
}
