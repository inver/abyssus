/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.plugin.physics

import net.nevinsky.abyssus.lib.gdx.editor.scene.sceneContentOf
import net.nevinsky.abyssus.lib.core.io.FileLoader
import net.nevinsky.abyssus.lib.core.io.JsonProcessor
import net.nevinsky.abyssus.lib.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.lib.core.dto.SceneDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicsOverlayGeometryTest {
    private val project = File(checkNotNull(System.getProperty("abyssus.testData")), "project/Physics")
    private val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val scene = json.parse(File(project, "scenes/Main Scene.scene").readText(), SceneDto::class.java)
    private val content = sceneContentOf(scene)
    private val metas = AssetMetaLoader(json, FileLoader(project))
    private val ctx = net.nevinsky.abyssus.lib.core.BaseCtx(project.path, null, null, org.slf4j.helpers.NOPLogger.NOP_LOGGER)
    private val geometry = PhysicsOverlayGeometry(ctx) { name -> metas.terrainSize(name) }

    @org.junit.After
    fun closeContext() {
        ctx.executor.shutdownNow()
        ctx.assetStorage.dispose()
    }

    @Test fun physicsComponentsCurrentlyProduceNoOverlayAndStayUnchanged() {
        val before = scene.ecs!!.toString()
        for (selected in listOf(null, "0", "1", "2", "missing")) {
            assertTrue(geometry.segments(content, scene.ecs, selected).isEmpty())
            assertEquals(before, scene.ecs!!.toString())
        }
    }

    @Test fun absentAndEmptyEntityMapsProduceNoOverlay() {
        assertTrue(geometry.segments(content, null, null).isEmpty())
        assertTrue(geometry.segments(content, json.readObject("{}"), null).isEmpty())
        assertTrue(geometry.segments(content, json.readObject("""{"entities":{}}"""), null).isEmpty())
    }

    @Test fun selectionColorsAreBrighterAndKeepTheirAlpha() {
        for (base in listOf(PhysicsColors.DYNAMIC, PhysicsColors.KINEMATIC, PhysicsColors.STATIC, PhysicsColors.CONSTRAINT)) {
            val selected = PhysicsColors.brighter(base)
            assertEquals(minOf(1f, base.r * 0.5f + 0.5f), selected.r, 0f)
            assertEquals(minOf(1f, base.g * 0.5f + 0.5f), selected.g, 0f)
            assertEquals(minOf(1f, base.b * 0.5f + 0.5f), selected.b, 0f)
            assertEquals(1f, selected.a, 0f)
        }
    }
}
