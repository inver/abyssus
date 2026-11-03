/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files
import com.badlogic.gdx.backends.lwjgl3.TestGl
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import net.nevinsky.abyssus.app.game.controlline.ControlLineGame
import net.nevinsky.abyssus.lib.core.BaseCtx
import net.nevinsky.abyssus.lib.core.assets.ModelKey
import net.nevinsky.abyssus.lib.core.assets.twoQuadData
import net.nevinsky.abyssus.lib.core.assets.writeModelAsset
import net.nevinsky.abyssus.lib.core.scene.RuntimeSceneLoader
import net.nevinsky.abyssus.lib.gdx.testing.warningsTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A model placed with a `meshId` is built and drawn as that mesh only (opt-in, `-Dabyssus.glTests=true`). The model is a
 * two-mesh asset of a temporary project; the committed field is untouched.
 */
class FieldModelMeshGlTest {
    @Before
    fun requireGl() = assumeTrue("GL tests are opt-in", TestGl.enabled)

    private val dirs = mutableListOf<File>()

    @After
    fun cleanUp() = dirs.forEach(File::deleteRecursively)

    private val scene = """{"format":"abyssus","formatVersion":1,"ecs":{
      "1":{"components":{"PositionComponent":{},"RenderComponent":{"type":"MODEL","assetName":"duo"}}},
      "2":{"components":{"PositionComponent":{"localPosition":{"x":3}},"RenderComponent":{"type":"MODEL","assetName":"duo","meshId":"mesh_1"}}},
      "3":{"components":{"PositionComponent":{"localPosition":{"x":6}},"RenderComponent":{"type":"MODEL","assetName":"duo","meshId":"nope"}}}}}"""

    @Test
    fun aMeshPlacedModelIsBuiltAndLoadedAsThatMeshOnly() = TestGl.run {
        Gdx.files = Lwjgl3Files()
        ShaderProgram.pedantic = false
        val project = Files.createTempDirectory("field-mesh").toFile().also(dirs::add)
        writeModelAsset(project, "duo", twoQuadData())
        val messages = mutableListOf<String>()
        val log = warningsTo(messages)
        val ctx = BaseCtx(project.path, "/shader", ControlLineGame::class.java, log)
        val loader = RuntimeSceneLoader(ctx.sceneLoader, ctx.ecsLoader, log)
        val renderer = FieldRenderer(ctx)
        try {
            val field = FieldScene(requireNotNull(loader.loadFromText(scene)) { messages.joinToString("\n") })
            val camera = PerspectiveCamera(60f, 640f, 480f).apply { position.set(3f, 3f, -6f); lookAt(3f, 0f, 0f); near = 1f; far = 100f; update() }
            val names = field.models.associate { (entity, asset) -> entity to field.modelStorageName(entity, asset) }.values.toList()
            assertEquals(listOf("duo", ModelKey("duo", "mesh_1").encode(), ModelKey("duo", "nope").encode()), names)

            var settled = false
            repeat(900) {
                if (settled) return@repeat
                renderer.draw(field, camera, seconds = 0.016f)
                // the unknown mesh never loads, so the scene settles on the other two
                settled = ctx.assetStorage.getAs<Model>(names[0]) != null && ctx.assetStorage.getAs<Model>(names[1]) != null &&
                        !ctx.assetStorage.isLoading(names[2])
                if (!settled) Thread.sleep(10)
            }
            assertTrue("never settled: $messages", settled)
            assertEquals(2, ctx.assetStorage.getAs<Model>(names[0])!!.meshes.size)
            assertEquals(1, ctx.assetStorage.getAs<Model>(names[1])!!.meshes.size)
            assertEquals(listOf("part_1"), ctx.assetStorage.getAs<Model>(names[1])!!.meshParts.map { it.id })
            assertTrue(ctx.assetStorage.getAs<Model>(names[2]) == null)
            assertNotNull(ctx.assetStorage.getAs<Model>(names[0]))
            // the unknown mesh is reported once and does not stop the others from being drawn
            assertEquals(1, messages.count { it.contains("nope") })
        } finally {
            renderer.dispose()
            ctx.assetStorage.dispose()
        }
    }
}
