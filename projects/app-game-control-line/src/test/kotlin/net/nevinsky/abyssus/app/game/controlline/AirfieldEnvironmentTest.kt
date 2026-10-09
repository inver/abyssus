/*
 * Copyright 2023-2026 Alexey Nevinsky
 * SPDX-License-Identifier: Apache-2.0
 */
package net.nevinsky.abyssus.app.game.controlline

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.model.data.ModelNode
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import net.nevinsky.abyssus.lib.gdx.io.JsonProcessor
import net.nevinsky.abyssus.lib.gdx.format.AbyssusDocumentFormat
import net.nevinsky.abyssus.lib.gdx.format.DocumentKind
import net.nevinsky.abyssus.lib.gdx.loader.AssimpModelLoader
import net.nevinsky.abyssus.lib.gdx.model.ModelData
import net.nevinsky.abyssus.app.game.controlline.components.PlaneComponent
import net.nevinsky.abyssus.app.game.controlline.render.FieldScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/** The bundled airfield environment (`control-line-field-environment`): its assets, and the clear flying area. */
class AirfieldEnvironmentTest {
    private val assets = bundledProject().resolve("assets").toFile()
    private val airfield = assets.listFiles()!!.filter { it.name.startsWith("model_airfield_") }.sortedBy { it.name }

    @Test
    fun everyAirfieldModelParsesWithItsTextures() {
        assertTrue("no airfield models", airfield.isNotEmpty())
        for (folder in airfield) {
            val model = File(folder, "model.glb")
            val data = AssimpModelLoader().loadData(FileHandle(model))
            assertTrue("${folder.name} has no meshes", data.meshes.size > 0)
            for (material in data.materials) for (texture in material.textures ?: continue) {
                val image = File(texture.fileName).let { if (it.isAbsolute) it else File(folder, texture.fileName) }
                assertTrue("${folder.name}: missing ${texture.fileName}", image.isFile)
                assertTrue("${folder.name}: ${texture.fileName} is outside the asset", image.canonicalPath.startsWith(folder.canonicalPath))
            }
            assertFalse("${folder.name} embeds textures", File(folder, "embedded").exists())
        }
    }

    @Test
    fun everyAirfieldAssetIsNativeAndRecordsItsSource() {
        val json = JsonProcessor(org.slf4j.helpers.NOPLogger.NOP_LOGGER)
        val format = AbyssusDocumentFormat()
        for (folder in assets.listFiles()!!.filter { it.name.contains("_airfield_") }) {
            assertNull(folder.name, format.validate(json.readObject(File(folder, "meta.json").readText()), DocumentKind.ASSET))
            val source = json.readObject(File(folder, "source.json").readText())
            assertTrue("${folder.name} has no license", source.path("license").asText().isNotEmpty())
            assertTrue("${folder.name} has no source", source.has("sourcePage") || source.has("generator"))
        }
    }

    @Test
    fun sceneryKeepsTheFlyingAreaClear() {
        val field = FieldScene(loadField())
        val pilot = field.position(field.pilot!!).localPosition
        assertEquals(0f, pilot.len(), 1e-6f)
        val boxes = HashMap<String, BoundingBox>()
        var scenery = 0
        for ((entity, name) in field.models) {
            if (entity === field.pilot || entity.getComponent(PlaneComponent::class.java) != null) continue
            if (name == "model_airfield_pad") continue // ground surface detail
            val box = boxes.getOrPut(name) { bounds(File(assets, "$name/model.glb")) }
            val world = field.position(entity).getTransform()
            val footprint = corners(box).map { Vector3(it).mul(world) }.map { Vector2(it.x - pilot.x, it.z - pilot.z) }
            val distance = distanceFromOrigin(footprint)
            assertTrue("$name ${field.position(entity).localPosition} is ${"%.1f".format(distance)} m from the pilot",
                distance >= CLEAR_RADIUS)
            scenery++
        }
        assertTrue("no scenery in the field", scenery > 0)
    }

    @Test
    fun linesAndPlanesAreUnchanged() {
        val field = FieldScene(loadField())
        assertEquals(mapOf("Racer" to 21f, "Stunter" to 18f, "Trainer" to 15f),
            field.planes.associate { it.name to it.lineLength })
    }

    private fun bounds(file: File): BoundingBox {
        val data = AssimpModelLoader().loadData(FileHandle(file))
        val box = BoundingBox().inf()
        for (node in data.nodes) extend(data, node, Matrix4(), box)
        return box
    }

    private fun extend(data: ModelData, node: ModelNode, parent: Matrix4, box: BoundingBox) {
        val local = Matrix4().set(node.translation ?: Vector3(), node.rotation ?: com.badlogic.gdx.math.Quaternion(), node.scale ?: Vector3(1f, 1f, 1f))
        val world = Matrix4(parent).mul(local)
        for (part in node.parts ?: emptyArray()) {
            val mesh = data.meshes.first { m -> m.parts.any { it.id == part.meshPartId } }
            val stride = mesh.attributes.sumOf { it.numComponents }
            var offset = 0
            for (attribute in mesh.attributes) {
                if (attribute.usage == VertexAttributes.Usage.Position) break
                offset += attribute.numComponents
            }
            var i = offset
            while (i < mesh.vertices.size) {
                box.ext(Vector3(mesh.vertices[i], mesh.vertices[i + 1], mesh.vertices[i + 2]).mul(world))
                i += stride
            }
        }
        for (child in node.children ?: emptyArray()) extend(data, child, world, box)
    }

    private fun corners(box: BoundingBox): List<Vector3> = buildList {
        for (x in listOf(box.min.x, box.max.x)) for (y in listOf(box.min.y, box.max.y)) for (z in listOf(box.min.z, box.max.z)) add(Vector3(x, y, z))
    }

    /** Horizontal distance from the pilot to the convex footprint of [points]; 0 if the pilot is inside it. */
    private fun distanceFromOrigin(points: List<Vector2>): Float {
        val hull = convexHull(points)
        var inside = true
        var best = Float.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            if (a.x * b.y - a.y * b.x < 0f) inside = false
            val ab = Vector2(b).sub(a)
            val t = (-a.dot(ab) / ab.len2()).coerceIn(0f, 1f)
            best = minOf(best, hypot(a.x + ab.x * t, a.y + ab.y * t))
        }
        return if (inside) 0f else best
    }

    /** Counter-clockwise hull (monotone chain). */
    private fun convexHull(points: List<Vector2>): List<Vector2> {
        val sorted = points.distinctBy { it.x to it.y }.sortedWith(compareBy({ it.x }, { it.y }))
        fun cross(o: Vector2, a: Vector2, b: Vector2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Vector2>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = mutableListOf<Vector2>()
        for (p in sorted.reversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private companion object {
        /** No scenery this close to the pilot (design: Clearance). */
        const val CLEAR_RADIUS = 28f
    }
}
