/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin

import net.nevinsky.abyssus.lib.core.editor.manualJsonViolations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NoManualJsonBuildingTest {
    @Test
    fun knownDocumentReadsUseTypedAdmission() {
        val root = File(System.getProperty("abyssus.pluginSources"), "net/nevinsky/abyssus/plugin")
        val sites = listOf("projectView/NewFoliageAction.kt", "projectView/NewTerrainAction.kt",
            "projectView/ImportRefusals.kt", "projectView/NewWeatherPreset.kt", "foliage/FoliageEditing.kt",
            "foliage/FoliageStrokeCommand.kt", "sceneview/SceneParamsSource.kt", "gameui/UiAssetCommands.kt",
            "properties/PanelState.kt", "assetfiles/AssetReferenceGuard.kt")
        // Generic payload scans and lossless edit targets/UiCollectionDecoder input retain their trees (D9).
        val exceptions = setOf("properties/PanelState.kt", "assetfiles/AssetReferenceGuard.kt", "gameui/UiAssetCommands.kt")
        assertEquals(emptyList<String>(), manualJsonViolations(root, sites, exceptions))
    }
}
