/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.updater.sync

import net.nevinsky.abyssus.lib.updater.manifest.Manifest
import net.nevinsky.abyssus.lib.updater.manifest.ManifestFile
import net.nevinsky.abyssus.lib.updater.manifest.ManifestPart
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePlannerTest {
    private val code = ManifestPart("code.zip", 1, "c")
    private val project = ManifestPart("project.zip", 1, "p")
    private val files = listOf(
        ManifestFile("a.jar", 1, "h1", "code.zip"), ManifestFile("b.jar", 1, "h2", "code.zip"),
        ManifestFile("project/x.scene", 1, "h3", "project.zip"),
    )
    private val manifest = Manifest(1, "control-line", "1.1.0", "Main", "d", listOf(code, project), files)

    @Test
    fun `project only change downloads only the project part`() {
        val base = BaseFiles { mapOf("a.jar" to "h1", "b.jar" to "h2", "project/x.scene" to "old")[it] }
        val plan = UpdatePlanner().plan(manifest, base)
        assertEquals(listOf(project), plan.partsToDownload)
        assertEquals(listOf("a.jar", "b.jar"), plan.filesToCopy.map { it.path })
    }

    @Test
    fun `one changed jar downloads the code part`() {
        val base = BaseFiles { mapOf("a.jar" to "h1", "b.jar" to "changed", "project/x.scene" to "h3")[it] }
        val plan = UpdatePlanner().plan(manifest, base)
        assertEquals(listOf(code), plan.partsToDownload)
        assertEquals(listOf("project/x.scene"), plan.filesToCopy.map { it.path })
    }

    @Test
    fun `new file counts as a change and nothing changed downloads nothing`() {
        assertEquals(listOf(code, project), UpdatePlanner().plan(manifest, BaseFiles { null }).partsToDownload)
        val same = BaseFiles { path -> files.first { it.path == path }.sha256 }
        val plan = UpdatePlanner().plan(manifest, same)
        assertEquals(emptyList<ManifestPart>(), plan.partsToDownload)
        assertEquals(3, plan.filesToCopy.size)
    }
}
