/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelKeyTest {
    @Test
    fun noMeshEncodesToTheBareName() {
        assertEquals("model_1", ModelKey("model_1").encode())
        assertEquals(ModelKey("model_1"), decodeModelKey("model_1"))
        assertFalse(isModelVariant("model_1"))
    }

    @Test
    fun meshIdsRoundTripWhateverTheyHold() {
        for (mesh in listOf("glass", "a#b", "a/b", "two words", "#", "/", " ", "a\u001Fb", "%23")) {
            val key = ModelKey("model_1", mesh)
            val encoded = key.encode()
            assertTrue(isModelVariant(encoded))
            assertEquals(key, decodeModelKey(encoded))
            assertEquals("model_1", baseAssetName(encoded))
        }
    }

    @Test
    fun anAssetNameWithHashAndSlashStaysTheBareName() {
        val key = decodeModelKey("my#model 2")
        assertEquals("my#model 2", key.name)
        assertNull(key.meshId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyMeshIdIsRejected() {
        ModelKey("model_1", "")
    }
}
