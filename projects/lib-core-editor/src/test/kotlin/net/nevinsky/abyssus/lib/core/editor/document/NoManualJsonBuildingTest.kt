/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.lib.core.editor.document

import net.nevinsky.abyssus.lib.core.editor.manualJsonViolations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NoManualJsonBuildingTest {
    @Test
    fun nativeAssetWritersUseTypedDocuments() {
        val root = File(System.getProperty("abyssus.editorCoreSources"), "net/nevinsky/abyssus/lib/core/editor")
        val sites = listOf("terrain/NewTerrainFiles.kt", "foliage/NewFoliageFiles.kt", "ui/UiAssetTemplate.kt",
            "modelimport/ModelImport.kt", "flightgear/FlightGearStaging.kt", "weather/WeatherPresetDraft.kt")
        // This writer merges a source preset's number text and unknown keys.
        assertEquals(emptyList<String>(), manualJsonViolations(root, sites, setOf("weather/WeatherPresetDraft.kt")))
    }
}
