/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.assets.sky.cube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import net.nevinsky.abyssus.assets.skyShaders
import net.nevinsky.abyssus.assets.testProject
import net.nevinsky.abyssus.assets.json.JsonProcessor
import net.nevinsky.abyssus.assets.files.AssetFiles
import net.nevinsky.abyssus.assets.files.MetaType
import net.nevinsky.abyssus.assets.sky.cube.SkyboxMeta
import net.nevinsky.abyssus.assets.sky.cube.SkyboxLoader
import java.io.File

class SkyboxMetaTest {
    private val projectDir = testProject("Untitled")

    @Test
    fun skyboxMetaBindsFromTheAssetFolder() {
        val asset = AssetFiles(projectDir, JsonProcessor()).loadAsset(SkyboxMeta::class.java, "skybox_default")
        assertNotNull("meta.json of skybox_default must bind to SkyboxMeta", asset)
        assertEquals("skybox_default.png", asset!!.meta.additional.top)
        assertEquals("skybox_default.png", asset.meta.additional.back)
        assertEquals(MetaType.SKYBOX, asset.meta.type)
    }

    @Test
    fun loaderDecodesAllSixFaces() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val prepared = SkyboxLoader(skyShaders()).prepare(AssetFiles(projectDir, JsonProcessor()), "skybox_default")
        assertNotNull("skybox_default must prepare", prepared)
        try {
            assertEquals(6, prepared!!.faces.size)
        } finally {
            prepared!!.dispose()
        }
    }
}
