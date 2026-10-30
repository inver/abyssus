/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.assetfiles

import net.nevinsky.abyssus.plugin.AbyssusBundle
import org.junit.Assert.*
import org.junit.Test

class AssetResultMessagesTest {
    @Test
    fun mapsAllSixResultsWithoutLosingTheirDetails() {
        assertNull(assetResultMessage(AssetCommandResult.Done))
        assertEquals(AbyssusBundle.message("newTerrainNameError.EXISTS"), assetResultMessage(AssetCommandResult.Collision("asset")))
        assertEquals("asset/meta.json", assetResultMessage(AssetCommandResult.Conflict("asset/meta.json")))
        assertEquals("still referenced", assetResultMessage(AssetCommandResult.Blocked("still referenced")))
        assertEquals(AbyssusBundle.message("uiAssetCancelled"), assetResultMessage(AssetCommandResult.Cancelled))
        assertEquals("cannot write", assetResultMessage(AssetCommandResult.Failed(IllegalStateException("cannot write"), emptyList())))
    }

    @Test
    fun callersRetainTheirCancellationConflictAndFailureStyles() {
        assertEquals(AbyssusBundle.message("newFoliageCancelled"),
            assetResultMessage(AssetCommandResult.Cancelled, "newFoliageCancelled"))
        assertEquals(AbyssusBundle.message("uiAssetConflict", "path"),
            assetResultMessage(AssetCommandResult.Conflict("path"), conflict = { AbyssusBundle.message("uiAssetConflict", it) }))
        val cause = IllegalStateException()
        assertEquals("IllegalStateException", assetResultMessage(AssetCommandResult.Failed(cause, emptyList())))
        assertEquals("import failure", assetResultMessage(AssetCommandResult.Failed(cause, emptyList()), failure = { "import failure" }))
    }
}
