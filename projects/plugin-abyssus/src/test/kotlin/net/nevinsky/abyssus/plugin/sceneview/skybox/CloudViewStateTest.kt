/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.sceneview.skybox

import net.nevinsky.abyssus.lib.gdx.assets.sky.clouds.CloudTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudViewStateTest {
    @Test
    fun assetResolvesToTheAssetsTechnique() {
        val state = CloudViewState()
        assertEquals(CloudChoice.ASSET, state.choice)
        assertNull(state.override)
        assertEquals(CloudTechnique.VOLUMETRIC, state.effective(CloudTechnique.VOLUMETRIC))
        assertTrue(state.choose(CloudChoice.LAYERED))
        assertEquals(CloudTechnique.LAYERED, state.effective(CloudTechnique.VOLUMETRIC))
        assertEquals(CloudTechnique.LAYERED, state.override)
    }

    @Test
    fun theFirstFallbackGoesToShellsAndVolumetricMayBeChosenAgain() {
        val state = CloudViewState()
        state.fallBack()
        assertEquals(CloudChoice.SHELLS, state.choice)
        assertEquals(CloudTechnique.SHELLS, state.effective(CloudTechnique.VOLUMETRIC))
        assertTrue(state.fallbackNote)
        assertFalse(state.sticky)
        assertTrue(state.choose(CloudChoice.VOLUMETRIC))
        assertEquals(CloudChoice.VOLUMETRIC, state.choice)
        assertFalse("a new choice clears the note", state.fallbackNote)
    }

    @Test
    fun theSecondFallbackIsSticky() {
        val state = CloudViewState()
        state.fallBack()
        state.choose(CloudChoice.VOLUMETRIC)
        state.fallBack()
        assertTrue(state.sticky)
        assertEquals(CloudChoice.SHELLS, state.choice)
        assertFalse(state.choose(CloudChoice.VOLUMETRIC))
        assertEquals(CloudChoice.SHELLS, state.choice)
        assertTrue("other choices are still allowed", state.choose(CloudChoice.LAYERED))
    }

    @Test
    fun aNewStateStartsAtAsset() {
        val old = CloudViewState()
        old.fallBack()
        old.fallBack()
        val reopened = CloudViewState()
        assertEquals(CloudChoice.ASSET, reopened.choice)
        assertEquals(0, reopened.fallbacks)
        assertFalse(reopened.fallbackNote)
    }
}
