/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.editor.modelimport

import net.nevinsky.abyssus.lib.gdx.assimp.UpAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ModelSourceTest {
    private val fixtures = importFixtures()

    private fun open(name: String) = ModelSourceOpener().open(File(fixtures, name))

    @Test
    fun fixturesArePinned() {
        val pinned = mapOf(
            "box.3ds" to "d7de13c27c335e78989b56ecc2adb5db9804ed883d5822c053fe86e1b6514046",
            "rig.fbx" to "dd9224dae13cba12c55eddf8e1794022dd6d751bc6538bdd7db949c85c660642",
            "crate.glb" to "7baeb327fce6105764ce03f5ced32bba8e3af80147bf1ea7f82b737d9760210e",
        )
        for ((name, sha) in pinned) {
            val actual = MessageDigest.getInstance("SHA-256").digest(File(fixtures, name).readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("$name was regenerated: check it, then pin the new hash", sha, actual)
        }
    }

    @Test
    fun anFbxStatesCentimetresZUpAndItsAnimations() {
        open("rig.fbx").use { source ->
            assertEquals(SourceFormat.FBX, source.format)
            assertEquals(SourceFrame(LengthUnit.CM, UpAxis.Z, FrameOrigin.FILE), source.frame)
            assertEquals(listOf(AnimationInfo("Idle", 2f), AnimationInfo("Run", 1f)), source.animations)
            assertEquals(64, source.sha256.length)
            assertEquals(1, source.textureCount())
            val root = source.data.nodes.first()
            assertNull("the importer's own axis correction is taken off", root.rotation)
        }
    }

    @Test
    fun anObjStatesNothing() {
        open("crate.obj").use { source ->
            assertNull(source.frame.unit)
            assertNull(source.frame.upAxis)
            assertEquals(LengthUnit.M, source.frame.defaultUnit())
            assertEquals(UpAxis.Y, source.frame.defaultUpAxis())
            assertEquals(listOf("Wood"), source.data.materials.map { it.id })
            assertEquals(listOf(ApproximatedItem("Wood", "specular colour dropped")), source.approximated)
        }
    }

    @Test
    fun a3dsIsZUpByItsFormat() {
        open("box.3ds").use { source ->
            assertEquals(SourceFrame(LengthUnit.M, UpAxis.Z, FrameOrigin.FORMAT), source.frame)
            val bounds = ImportTransform().restBounds(source.data)
            assertEquals("the box stands along the file's Z", 200f, bounds.depth, 1e-3f)
        }
    }

    @Test
    fun aDaeStatesItsAsset() {
        open("crate.dae").use { assertEquals(SourceFrame(LengthUnit.CM, UpAxis.Z, FrameOrigin.FILE), it.frame) }
        open("crate_xup.dae").use { source ->
            assertEquals(UpAxis.X, source.frame.upAxis)
            assertEquals("X up is not offered: Y is pre-filled", UpAxis.Y, source.frame.defaultUpAxis())
        }
    }

    @Test
    fun gltfIsMetresAndYUpByItsFormat() {
        open("crate.glb").use { source ->
            assertEquals(SourceFrame(LengthUnit.M, UpAxis.Y, FrameOrigin.FORMAT), source.frame)
            assertTrue(source.approximated.isEmpty())
        }
        open("animated.gltf").use { source ->
            assertEquals(SourceFrame(LengthUnit.M, UpAxis.Y, FrameOrigin.FORMAT), source.frame)
            assertEquals(listOf("wave"), source.animations.map { it.name })
        }
    }

    @Test
    fun morphTargetsAndExtensionsAreLeftOut() {
        open("morph.gltf").use { source ->
            assertEquals(
                listOf(
                    LeftOutItem("triangle", LeftOutReason.MORPH_TARGETS),
                    LeftOutItem("KHR_texture_transform", LeftOutReason.GLTF_EXTENSION),
                ),
                source.leftOut,
            )
        }
    }

    @Test
    fun aMissingTextureIsListedAndTheMaterialKeepsItsColour() {
        open("missing/crate_missing.obj").use { source ->
            assertEquals(listOf(LeftOutItem("wood.png", LeftOutReason.MISSING)), source.leftOut)
            val material = source.data.materials.single()
            assertTrue(material.textures.isEmpty)
            assertEquals(0.6f, material.diffuse.r, 1e-6f)
        }
    }

    @Test
    fun openingAndClosingLeavesTheSourceFolderAlone() {
        val before = fixtures.walkTopDown().map { it.relativeTo(fixtures).path }.sorted().toList()
        var embedded: File
        open("rig.fbx").use { source ->
            embedded = source.embeddedDir
            assertTrue(embedded.list()!!.isNotEmpty())
        }
        open("crate.glb").use { }
        assertFalse(embedded.exists())
        assertEquals(before, fixtures.walkTopDown().map { it.relativeTo(fixtures).path }.sorted().toList())
    }

    @Test
    fun aTruncatedFbxIsRejectedWithAReason() {
        val truncated = File(fixtures, "broken.fbx")
        truncated.writeBytes(File(fixtures, "rig.fbx").readBytes().copyOf(600))
        try {
            ModelSourceOpener().open(truncated).close()
            fail("expected the truncated file to be rejected")
        } catch (e: ModelImportException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun anUnknownFormatIsRejected() {
        assertNull(sourceFormatOf("ship.blend"))
        assertTrue(isBlenderFile("Ship.BLEND"))
        try {
            ModelSourceOpener().open(File(fixtures, "wood.png"))
            fail("expected a refusal")
        } catch (e: ModelImportException) {
            assertTrue(e.message!!.contains("wood.png"))
        }
    }
}
