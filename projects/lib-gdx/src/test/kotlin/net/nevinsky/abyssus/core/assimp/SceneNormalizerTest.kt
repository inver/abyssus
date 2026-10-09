/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assimp

import com.badlogic.gdx.files.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SceneNormalizerTest {
    @Test
    fun fbxMetadataStatesUnitAndUpAxis() {
        val stated = SceneNormalizer.stated(mapOf("UpAxis" to 2, "UpAxisSign" to 1, "UnitScaleFactor" to 1.0))
        assertEquals(UpAxis.Z, stated.upAxis)
        assertEquals(0.01f, stated.unitMetres!!, 1e-7f)
    }

    @Test
    fun noMetadataStatesNothing() {
        assertEquals(StatedFrame(null, null), SceneNormalizer.stated(emptyMap()))
    }

    @Test
    fun aZUpFileIsCorrectedOnlyWhenNormalizing() {
        val meta = mapOf<String, Number>("UpAxis" to 2, "FrontAxis" to 1, "FrontAxisSign" to -1, "CoordAxis" to 0)
        assertNotNull(SceneNormalizer.correction(meta, false))
    }

    /** A Z-up file in centimetres: Assimp's Collada importer would rotate and scale its root unless told not to. */
    @Test
    fun anUnnormalizedLoadLeavesTheRootAsTheFileHasIt() {
        val file = FileHandle(resourceCopy("/model/crate.dae"))

        val raw = AssimpModelDataLoader(normalize = false).loadScene("crate", file)
        val root = raw.data.nodes.first()
        assertNull(root.translation)
        assertNull(root.rotation)
        assertNull(root.scale)
        val box = raw.data.restBounds()
        assertEquals(100f, box.width, 1e-3f)
        assertEquals(100f, box.height, 1e-3f)
        assertEquals(100f, box.depth, 1e-3f)
        assertEquals(0f, box.min.z, 1e-3f)
        assertEquals(100f, box.max.z, 1e-3f)

        // the default load still applies the file's frame: Z up becomes Y up, centimetres become metres
        val normalized = AssimpModelDataLoader().load("crate", file).restBounds()
        assertEquals(1f, normalized.height, 1e-3f)
        assertEquals(1f, normalized.max.y, 1e-3f)
    }
}
