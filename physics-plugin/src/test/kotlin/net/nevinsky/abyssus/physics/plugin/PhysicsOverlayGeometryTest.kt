/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.physics.plugin

import com.fasterxml.jackson.databind.node.ObjectNode
import net.nevinsky.abyssus.core.FileLoader
import net.nevinsky.abyssus.core.JsonProcessor
import net.nevinsky.abyssus.core.assets.AssetMetaLoader
import net.nevinsky.abyssus.core.scene.Scene
import net.nevinsky.abyssus.sceneview.SceneContent
import net.nevinsky.abyssus.editor.content.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicsOverlayGeometryTest {
    private val project = File(checkNotNull(System.getProperty("abyssus.testData")), "project/Physics")
    private val json = JsonProcessor()
    private val scene = json.parse(File(project, "scenes/Main Scene.scene").readText(), Scene::class.java)
    private val content = SceneContent.of(scene)
    private val metas = AssetMetaLoader(json, FileLoader(project))
    private val geometry = PhysicsOverlayGeometry { name -> metas.terrainSize(name) }

    private fun extent(segments: List<OverlaySegment>, axis: (Vec3) -> Float): Float {
        val values = segments.flatMap { listOf(axis(it.from), axis(it.to)) }
        return values.max() - values.min()
    }

    private fun model0Box(segments: List<OverlaySegment>) = segments.filter { it.color == PhysicsColors.DYNAMIC || it.color == PhysicsColors.brighter(PhysicsColors.DYNAMIC) }

    @Test
    fun aOneMetreGreenCubeForModel0sBox() {
        val box = model0Box(geometry.segments(content, scene.ecs, null))
        assertEquals(12, box.size)
        assertTrue(box.none { it.selected })
        val centre = content.models.single { it.entityId == "0" }.transform.position
        assertEquals(1f, extent(box) { it.x }, 1e-4f)
        assertEquals(1f, extent(box) { it.y }, 1e-4f)
        assertEquals(1f, extent(box) { it.z }, 1e-4f)
        assertEquals(centre.y, box.flatMap { listOf(it.from.y, it.to.y) }.average().toFloat(), 1e-4f)
        assertEquals(3.086434f, centre.y)
    }

    @Test
    fun aRopeLineBetweenTheAnchorsWithAMarkerAtEachEnd() {
        val rope = geometry.segments(content, scene.ecs, null).filter { it.color == PhysicsColors.CONSTRAINT }
        val model0 = content.models.single { it.entityId == "0" }.transform.position
        val model2 = content.models.single { it.entityId == "2" }.transform.position
        // Model 0 starts about 9.9 m from Model 2, beyond the rope's 3 m, so the line between the anchors is dashed
        val dashes = rope.filter { length(it) < 0.29f }
        assertTrue(dashes.size > 10)
        assertTrue(dashes.any { near(it.from, model2, 1e-3f) })
        val span = kotlin.math.sqrt(sq(model0.x - model2.x) + sq(model0.y - model2.y) + sq(model0.z - model2.z))
        assertEquals(span, dashes.maxOf { kotlin.math.sqrt(sq(it.to.x - model2.x) + sq(it.to.y - model2.y) + sq(it.to.z - model2.z)) }, 0.3f)
        // a cross (3 segments of 0.3 m) centred on each anchor
        for (anchor in listOf(model0, model2)) {
            assertEquals(3, rope.count { length(it) in 0.299f..0.301f && near(mid(it), anchor, 1e-3f) })
        }
    }

    @Test
    fun resizingTheBoxGivesTwoMetresAlongX() {
        val ecs = scene.ecs!!.deepCopy<com.fasterxml.jackson.databind.JsonNode>()
        val collider = ecs["entities"]["0"]["components"]["ColliderComponent"] as ObjectNode
        collider.set<ObjectNode>("halfExtents", json.readObject("""{"x": 1, "y": 0.5, "z": 0.5}"""))
        val box = model0Box(geometry.segments(content, ecs, null))
        assertEquals(2f, extent(box) { it.x }, 1e-4f)
        assertEquals(1f, extent(box) { it.y }, 1e-4f)
    }

    @Test
    fun theSelectedEntitysPhysicsIsBrighterAndOnTop() {
        val segments = geometry.segments(content, scene.ecs, "0")
        val box = segments.filter { it.color == PhysicsColors.brighter(PhysicsColors.DYNAMIC) }
        assertEquals(12, box.size)
        assertTrue(box.all { it.selected })
        // the rope touches Model 0, so it is selected too
        assertTrue(segments.filter { it.color == PhysicsColors.brighter(PhysicsColors.CONSTRAINT) }.all { it.selected })
        assertTrue(segments.filter { it.color == PhysicsColors.STATIC }.none { it.selected })
    }

    @Test
    fun theTerrainIsDrawnAsAGreyOutline() {
        val outline = geometry.segments(content, scene.ecs, null).filter { it.color == PhysicsColors.STATIC && it.from.y == 0f && it.to.y == 0f }
        assertEquals(4, outline.size)
        val terrain = content.terrains.single()
        val size = metas.terrainSize(terrain.assetName)!!
        assertEquals(size, extent(outline) { it.x }, 1e-2f)
        assertEquals(terrain.transform.position.x, outline.minOf { minOf(it.from.x, it.to.x) }, 1e-3f)
    }

    private fun length(s: OverlaySegment) = kotlin.math.sqrt(sq(s.from.x - s.to.x) + sq(s.from.y - s.to.y) + sq(s.from.z - s.to.z))
    private fun sq(v: Float) = v * v
    private fun mid(s: OverlaySegment) = Vec3((s.from.x + s.to.x) / 2, (s.from.y + s.to.y) / 2, (s.from.z + s.to.z) / 2)
    private fun near(a: Vec3, b: Vec3, tolerance: Float) = kotlin.math.sqrt(sq(a.x - b.x) + sq(a.y - b.y) + sq(a.z - b.z)) <= tolerance
}
