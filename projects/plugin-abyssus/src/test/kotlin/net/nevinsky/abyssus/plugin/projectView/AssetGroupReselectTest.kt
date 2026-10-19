/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.plugin.projectView

import org.junit.Assert.assertEquals
import org.junit.Test

/** Entry paths of assets inside a type group stay reachable by the reselect walk after an edit. */
class AssetGroupReselectTest {
    private val asset = "/p/P.abss/assets/MODEL/tree"

    @Test
    fun theWalkDescendsThroughFolderAndGroupToTheAsset() {
        assertEquals(ReselectStep.DESCEND, reselectStep(asset, "/p/P.abss/assets", null))
        assertEquals(ReselectStep.DESCEND, reselectStep(asset, "/p/P.abss/assets/MODEL", null))
        assertEquals(ReselectStep.FOUND, reselectStep(asset, asset, null))
        assertEquals(ReselectStep.SKIP, reselectStep(asset, "/p/P.abss/assets/TERRAIN", null))
        assertEquals(ReselectStep.SKIP, reselectStep(asset, "/p/P.abss/ui", null))
    }
}
