/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */

package net.nevinsky.abyssus.lib.gdx.assets.sky.procedural

import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSkyMeta
import net.nevinsky.abyssus.lib.gdx.io.FileLoader
import net.nevinsky.abyssus.lib.gdx.assets.MetaType
import net.nevinsky.abyssus.lib.gdx.assets.testMetaLoader
import net.nevinsky.abyssus.lib.gdx.assets.testProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProceduralSkyFixtureTest {
    @Test
    fun fixtureFolderIsComplete() {
        val projectDir = testProject("Untitled")
        val meta = testMetaLoader(projectDir).loadBaseMeta("skybox_physical")
        assertNotNull("meta.json of skybox_physical must load", meta)
        assertEquals(MetaType.SKYBOX_PROCEDURAL, meta!!.type)
        val additional = meta.typedAdditional<ProceduralSkyMeta>()
        val files = FileLoader(projectDir)
        assertNotNull(files.loadAssetFile("skybox_physical", additional.vertex))
        assertNotNull(files.loadAssetFile("skybox_physical", additional.fragment))
    }
}
