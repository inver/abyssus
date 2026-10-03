/*
 * Copyright 2023-2026 Alexey Nevinsky
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
