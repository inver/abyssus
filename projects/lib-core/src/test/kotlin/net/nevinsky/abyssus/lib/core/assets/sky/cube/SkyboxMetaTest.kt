/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.core.assets.sky.cube

import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.assets.MetaType
import net.nevinsky.abyssus.lib.core.assets.skyShaders
import net.nevinsky.abyssus.lib.core.assets.testMetaLoader
import net.nevinsky.abyssus.lib.core.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SkyboxMetaTest {
    private val projectDir = testProject("Untitled")
    private val metaLoader = testMetaLoader(projectDir)

    @Test
    fun skyboxMetaBindsFromTheAssetFolder() {
        val meta = metaLoader.loadBaseMeta("skybox_default")
        assertNotNull("meta.json of skybox_default must load", meta)
        val additional = meta!!.typedAdditional<SkyboxMeta>()
        assertEquals("skybox_default.png", additional.top)
        assertEquals("skybox_default.png", additional.back)
        assertEquals(MetaType.SKYBOX, meta.type)
    }

    @Test
    fun loaderDecodesAllSixFaces() {
        com.badlogic.gdx.utils.GdxNativesLoader.load()
        val prepared = SkyboxLoader(FileLoader(projectDir), metaLoader, skyShaders()).prepare("skybox_default")?.staged
        assertNotNull("skybox_default must prepare", prepared)
        try {
            assertEquals(6, prepared!!.faces.size)
        } finally {
            prepared!!.dispose()
        }
    }
}
