/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import net.nevinsky.abyssus.lib.core.assets.sky.cube.SkyboxCube
import net.nevinsky.abyssus.lib.core.assets.sky.hdr.HdrSky
import net.nevinsky.abyssus.lib.core.assets.sky.procedural.ProceduralSky
import net.nevinsky.abyssus.lib.core.assets.terrain.TerrainMesh
import net.nevinsky.abyssus.lib.core.assets.testProject
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import com.badlogic.gdx.graphics.g3d.Model
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor

/**
 * [fieldAssets] loads every kind of asset the `Untitled` fixture holds, in a real GL context (opt-in,
 * `-Dabyssus.glTests=true`): the game reads the same kinds as the scene view (spec `asset-loading`).
 */
class FieldAssetsGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val model = "model_29e9be61-6594-4f82-a6cf-44ccf09f71fb"
    private val terrain = "terrain_2cf70bf7-f7ee-4c41-934c-e40df1d35c8b"

    @Test
    fun everyAssetKindOfTheFixtureLoadsToItsType() {
        val logged = mutableListOf<String>()
        TestGl.run {
            Gdx.files = Lwjgl3Files()
            ShaderProgram.pedantic = false
            val log = warningsTo(logged)
            val assets = fieldAssets(testProject("Untitled"), JsonProcessor(log), log, Executor(Runnable::run))
            try {
                val names = setOf(model, terrain, "skybox_default", "skybox_physical", "skybox_hdr")
                repeat(600) {
                    names.forEach(assets::request)
                    assets.update()
                    if (!assets.isLoading()) return@repeat
                }
                assertNotNull("model", assets.getAs<Model>(model))
                val mesh = assets.getAs<TerrainMesh>(terrain)
                assertNotNull("terrain", mesh)
                assertEquals(1600, mesh!!.data.size)
                assertEquals(60f, mesh.data.uv, 0f)
                assertNotNull("cube sky", assets.getAs<SkyboxCube>("skybox_default"))
                assertNotNull("procedural sky", assets.getAs<ProceduralSky>("skybox_physical"))
                assertNotNull("hdr sky", assets.getAs<HdrSky>("skybox_hdr"))
            } finally {
                assets.dispose()
            }
        }
        assertEquals(emptyList<String>(), logged)
    }
}
