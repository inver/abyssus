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
    private val geometry = PhysicsOverlayGeometry { name -> metas.terrainSize(name) }

    @Test fun physicsComponentsDrawAtShownPosesAndStayUnchanged() {
        val before = scene.ecs!!.toString()
        for (selected in listOf(null, "0", "1", "2", "missing")) {
            val segments = geometry.segments(content, scene.ecs, selected)
            assertTrue(segments.isNotEmpty())
            if (selected == "0") assertEquals(12, segments.count { it.color == PhysicsColors.brighter(PhysicsColors.DYNAMIC) })
            if (selected == null) {
                val box = segments.filter { it.color == PhysicsColors.DYNAMIC }
                assertEquals(12, box.size)
                assertEquals(2.586434f, box.minOf { minOf(it.from.y, it.to.y) }, 0.00001f)
                assertEquals(3.586434f, box.maxOf { maxOf(it.from.y, it.to.y) }, 0.00001f)
                assertEquals(76, segments.count { it.color == PhysicsColors.STATIC })
                assertTrue(segments.count { it.color == PhysicsColors.CONSTRAINT } >= 7)
            }
            assertEquals(before, scene.ecs!!.toString())
        }
    }

    @Test fun allShapesAndScaledOffsetsFollowPreviewPositions() {
        for ((shape, count) in listOf("BOX" to 12, "SPHERE" to 72, "CAPSULE" to 100, "CONVEX_HULL" to 12)) {
            val ecs = json.readObject("""{"entities":{"0":{"components":{
                "net.nevinsky.abyssus.lib.physics.ColliderComponent":{"shape":"$shape","offset":{"x":1}},
                "net.nevinsky.abyssus.lib.physics.RigidBodyComponent":{"motionType":"KINEMATIC"},
                "PositionComponent":{"localScale":{"x":2,"y":2,"z":2}}}}}}""")
            val shown = net.nevinsky.abyssus.lib.gdx.editor.scene.SceneContent(entityPositions = mapOf("0" to
                net.nevinsky.abyssus.lib.gdx.editor.content.Vec3(10f,20f,30f)))
            val lines = geometry.segments(shown, ecs, "0")
            assertEquals(shape,count,lines.size)
            assertTrue(lines.all { it.selected && it.color == PhysicsColors.brighter(PhysicsColors.KINEMATIC) })
            if (shape == "BOX") {
                assertEquals(11f,lines.minOf { minOf(it.from.x,it.to.x) },0f)
                assertEquals(13f,lines.maxOf { maxOf(it.from.x,it.to.x) },0f)
                assertEquals(19f,lines.minOf { minOf(it.from.y,it.to.y) },0f)
            }
        }
    }

    @Test fun constraintAnchorsUseLocalAndWorldFrames() {
        val ecs = json.readObject("""{"entities":{"0":{"components":{"ConstraintComponent":{"anchor":{"x":1},"otherAnchor":{"y":4}}}}}}""")
        val shown = net.nevinsky.abyssus.lib.gdx.editor.scene.SceneContent(entityPositions = mapOf("0" to
            net.nevinsky.abyssus.lib.gdx.editor.content.Vec3(2f,0f,0f)))
        val lines = geometry.segments(shown,ecs,null)
        assertTrue(lines.any { it.from == net.nevinsky.abyssus.lib.gdx.editor.content.Vec3(3f,0f,0f) })
        assertTrue(lines.any { it.from == net.nevinsky.abyssus.lib.gdx.editor.content.Vec3(-0.15f,4f,0f) &&
            it.to == net.nevinsky.abyssus.lib.gdx.editor.content.Vec3(0.15f,4f,0f) })
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
